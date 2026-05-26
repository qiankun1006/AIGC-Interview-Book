import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ============================================================
 * Scenarios —— InnoDB 原子性 & 两阶段提交 场景演示集合
 * ============================================================
 * <p>
 * 包含 7 个演示场景，每个场景独立调用 resetDisk() 初始化磁盘状态：
 * <pre>
 *  场景1: scenario1_normalTransfer()        — 正常转账，完整 2PC 流程
 *  场景2: scenario2_rollback()              — 业务异常 ROLLBACK（5次UPDATE多步回滚）
 *  场景3: scenario3_crashAfterCommit()      — COMMIT 后宕机，Redo Log 恢复数据页
 *  场景4: scenario4_crashBeforeCommit()     — COMMIT 前宕机（含4a/4b两个子场景）
 *  场景5: scenario5_flushPolicy2Risk()      — innodb_flush_log_at_trx_commit=2 的已知风险
 *  场景6: scenario6_crashAfterPrepareBeforeBinlog() — 2PC 崩溃点1（PREPARE后Binlog前）
 *  场景7: scenario7_crashAfterBinlogBeforeCommit()  — 2PC 崩溃点2（Binlog后COMMIT前）
 * </pre>
 * <p>
 * 依赖：TransactionEngine（所有事务操作）/ DiskStore（磁盘初始化）/ LockManager（锁清理）
 */
public class Scenarios extends MysqlDemoBase {

    // ==================== 磁盘 & 内存状态重置 ====================

    /**
     * 清空磁盘文件并写入初始数据（每个场景开始前调用）
     * <p>
     * 包含：data.ibd / undo_001.ibu / ib_logfile0 / ib_logfile_header / binlog
     * <p>
     * 注意：txIdCounter 不重置，保证事务 ID 全局唯一递增（和真实 trx_sys 一致）
     */
    static void resetDisk() {
        DiskStore.initDiskDir();
        try {
            Files.deleteIfExists(Paths.get(DiskStore.DATA_FILE));
            Files.deleteIfExists(Paths.get(DiskStore.UNDO_FILE));
            Files.deleteIfExists(Paths.get(DiskStore.REDO_FILE));
            Files.deleteIfExists(Paths.get(DiskStore.REDO_HEADER_FILE));
            Files.deleteIfExists(Paths.get(DiskStore.BINLOG_FILE));
        } catch (Exception e) {
            throw new RuntimeException("清理 disk 目录失败", e);
        }

        // 内存全量归零
        TransactionEngine.bufferPool.clear();
        TransactionEngine.undoLog.clear();
        TransactionEngine.undoPageDirty = false;
        Arrays.fill(TransactionEngine.redoLogBuffer, null);
        TransactionEngine.logBufPos = 0;
        TransactionEngine.txUndoNoCounter.clear();
        TransactionEngine.binlogCache.clear();
        LockManager.clearAll();
        TransactionEngine.currentLsn      = 0;
        TransactionEngine.checkpointLsn   = 0;
        TransactionEngine.flushedToDisklsn = 0;
        TransactionEngine.redoWriteHead   = 0;
        // txIdCounter 保持不变

        // 写初始数据到 data.ibd（Alice + Bob + Charlie）
        Map<String, Integer> init = new LinkedHashMap<>();
        init.put("Alice",   ALICE_INIT);    // 10000
        init.put("Bob",     BOB_INIT);      // 5000
        init.put("Charlie", CHARLIE_INIT);  // 8000
        DiskStore.flushDataToDisk(init);
        DiskStore.saveRedoHeader(0, 0);
        System.out.println("初始化: Alice=" + ALICE_INIT + ", Bob=" + BOB_INIT
                + ", Charlie=" + CHARLIE_INIT + " -> " + DiskStore.DATA_FILE);
        System.out.println("disk 目录: " + new File(DiskStore.DISK_DIR).getAbsolutePath());
    }

    // ==================== 场景 1：正常转账 ====================

    /**
     * 场景1：正常转账 —— 完整 2PC 流程（Alice 转 2000 给 Bob）
     * <p>
     * 演示要点：
     *   · 加锁（IX + Record X + Gap）→ Undo Log → Redo Log Buffer → Binlog Cache → commit (2PC)
     *   · Phase1 Redo PREPARE fsync → Phase2 Binlog XID_COMMIT fsync → Phase3 Redo COMMIT
     *   · checkpoint 脏页异步落盘（不阻塞 COMMIT 返回）
     */
    static void scenario1_normalTransfer() {
        long txId = TransactionEngine.txIdCounter++;
        System.out.println("[BEGIN tx=" + txId + "] Alice 转 " + TRANSFER_AMT + " 元给 Bob");

        System.out.println("\n-- UPDATE Alice（" + ALICE_INIT + " - " + TRANSFER_AMT + " = " + ALICE_AFTER + "）--");
        TransactionEngine.update(txId, "Alice", TransactionEngine.read("Alice") - TRANSFER_AMT);

        System.out.println("\n-- UPDATE Bob（" + BOB_INIT + " + " + TRANSFER_AMT + " = " + BOB_AFTER + "）--");
        TransactionEngine.update(txId, "Bob", TransactionEngine.read("Bob") + TRANSFER_AMT);

        System.out.println("\n-- COMMIT --");
        TransactionEngine.commit(txId);

        System.out.println("\n最终 data.ibd: " + DiskStore.loadDataFromDisk());
        System.out.println("可以 cat "  + new File(DiskStore.DATA_FILE).getAbsolutePath() + " 查看文件内容");
        System.out.println("可以 xxd "  + new File(DiskStore.REDO_FILE).getAbsolutePath() + " | head 查看 ib_logfile0 二进制内容");
        System.out.println("✓ 原子性：两步要么全成功，要么全失败");
        System.out.println("✓ 持久性：Redo Log fsync 后即持久，脏页异步落盘");
    }

    // ==================== 场景 2：多步回滚 ====================

    /**
     * 场景2：业务异常 ROLLBACK —— 5次UPDATE多步回滚演示
     * <p>
     * 演示要点：
     *   · 多个账户同时修改（Alice 扣款 + Bob 收款 → Bob 再转 Charlie → Alice 手续费）
     *   · Undo Log 的 undoNo 从 100 递增，roll_pointer 形成版本链
     *   · ROLLBACK 时按 undoNo 降序逆序遍历，确保回到初始值
     *   · 锁的持有与释放（多次 UPDATE 同一行复用锁）
     */
    static void scenario2_rollback() {
        long txId = TransactionEngine.txIdCounter++;
        System.out.println("[BEGIN tx=" + txId + "] 批量转账：Alice → Bob 转 " + TRANSFER_AMT
                + " 元，Bob → Charlie 转 1000 元，手续费从 Alice 账户扣 500 元");
        System.out.println("→ 模拟 5 次 UPDATE 操作，演示 Undo Log 的 undoNo + roll_pointer 链如何支撑多步回滚");

        // UPDATE 1：Alice 扣款 2000（转账给 Bob）
        System.out.println("\n-- UPDATE 1: Alice 扣款 " + TRANSFER_AMT
                + "（Alice " + ALICE_INIT + " → " + (ALICE_INIT - TRANSFER_AMT) + "）--");
        TransactionEngine.update(txId, "Alice", TransactionEngine.read("Alice") - TRANSFER_AMT);

        // UPDATE 2：Bob 收款 2000
        System.out.println("\n-- UPDATE 2: Bob 收款 " + TRANSFER_AMT
                + "（Bob " + BOB_INIT + " → " + (BOB_INIT + TRANSFER_AMT) + "）--");
        TransactionEngine.update(txId, "Bob", TransactionEngine.read("Bob") + TRANSFER_AMT);

        // UPDATE 3~5：Bob → Charlie 转 1000，Alice 手续费 -500
        System.out.println("\n-- UPDATE 3~5: Bob → Charlie 转 1000，Alice 手续费 -500 --");
        TransactionEngine.update(txId, "Bob",     TransactionEngine.read("Bob")     - 1000);
        TransactionEngine.update(txId, "Charlie", TransactionEngine.read("Charlie") + 1000);
        TransactionEngine.update(txId, "Alice",   TransactionEngine.read("Alice")   - 500);

        System.out.println("  事务中间态（仅在 Buffer Pool，未提交）: Alice=" + TransactionEngine.bufferPool.get("Alice")
                + ", Bob=" + TransactionEngine.bufferPool.get("Bob")
                + ", Charlie=" + TransactionEngine.bufferPool.get("Charlie"));
        System.out.println("  Undo Log 此时已有 5 条记录，undoNo 从 100 递增，roll_pointer 形成版本链");

        // 业务校验失败：Charlie 账户被风控冻结，整体回滚
        System.out.println("\n-- Charlie 账户被风控系统冻结，本次批量转账整体回滚 -> ROLLBACK --");
        System.out.println("  回滚策略：按 undoNo 降序逆序读取 Undo Log，沿 roll_pointer 链逐条撤销");
        TransactionEngine.rollback(txId);

        int aliceFinal   = TransactionEngine.bufferPool.getOrDefault("Alice",
                DiskStore.loadDataFromDisk().getOrDefault("Alice",   0));
        int bobFinal     = TransactionEngine.bufferPool.getOrDefault("Bob",
                DiskStore.loadDataFromDisk().getOrDefault("Bob",     0));
        int charlieFinal = TransactionEngine.bufferPool.getOrDefault("Charlie",
                DiskStore.loadDataFromDisk().getOrDefault("Charlie", 0));
        System.out.println("\n最终数据（含 Buffer Pool）: Alice=" + aliceFinal
                + ", Bob=" + bobFinal + ", Charlie=" + charlieFinal);
        System.out.println("✓ 5 条 UPDATE 全部按 undoNo 逆序 + roll_pointer 链撤销，三个账户均回到初始值");
        System.out.println("  Alice=" + ALICE_INIT + "（期望）, Bob=" + BOB_INIT
                + "（期望）, Charlie=" + CHARLIE_INIT + "（期望）");
    }

    // ==================== 场景 3：COMMIT 后崩溃 ====================

    /**
     * 场景3：COMMIT 后宕机 —— Redo Log 恢复数据页（WAL 持久性保证）
     * <p>
     * 演示要点：
     *   · 已提交事务的 Redo Log 已 fsync → 即使脏页未落盘，崩溃恢复也能重放
     *   · checkpointLsn 标记脏页落盘进度，data.ibd 可能比 ib_logfile0 旧
     */
    static void scenario3_crashAfterCommit() {
        long txId = TransactionEngine.txIdCounter++;
        System.out.println("[BEGIN tx=" + txId + "] Alice 转 " + TRANSFER_AMT + " 元给 Bob");
        TransactionEngine.update(txId, "Alice", ALICE_AFTER);
        TransactionEngine.update(txId, "Bob",   BOB_AFTER);
        TransactionEngine.commit(txId);

        System.out.println("\n[CRASH!] 宕机，Buffer Pool 丢失；模拟脏页未落盘（手动覆盖 data.ibd 为旧值）");
        System.out.println("  真实场景：commit() 中 Phase3 Redo COMMIT 已 fsync，但 Page Cleaner 的");
        System.out.println("  checkpoint 落盘尚未完成，机器此时断电 → data.ibd 仍是旧值");
        System.out.println("  模拟方法：① 回退 ib_logfile_header 中的 checkpoint_lsn=0（脏页未落盘）");
        System.out.println("            ② 将 data.ibd 强制改回旧值（模拟脏页丢失）");

        // ① 回退 checkpoint_lsn = 0，告诉崩溃恢复"脏页落盘前的 LSN=0，需要从头重放"
        //    真实：断电瞬间 checkpoint 还没写入文件头，ib_logfile_header 还是上次的旧值
        TransactionEngine.checkpointLsn = 0;
        DiskStore.saveRedoHeader(0, TransactionEngine.redoWriteHead);
        System.out.println("  ib_logfile_header 回退: checkpoint_lsn=0 (模拟 checkpoint 未来得及持久化)");

        // ② 强制覆盖 data.ibd 为旧值（模拟 Page Cleaner 还未来得及刷脏页）
        TransactionEngine.bufferPool.clear();
        Map<String, Integer> stale = new LinkedHashMap<>();
        stale.put("Alice", ALICE_INIT);
        stale.put("Bob",   BOB_INIT);
        DiskStore.flushDataToDisk(stale);
        System.out.println("  data.ibd 强制改回: Alice=" + ALICE_INIT + ", Bob=" + BOB_INIT
                + "（期望 Redo 恢复为 Alice=" + ALICE_AFTER + ", Bob=" + BOB_AFTER + "）");
        System.out.println("  ib_logfile0 已 fsync，Redo COMMIT 标记已持久化 ✓");
        System.out.println("重启，开始 Crash Recovery，读取 ib_logfile_header + ib_logfile0 ...");

        // 模拟重启：清空全部内存状态
        TransactionEngine.bufferPool.clear();
        TransactionEngine.undoLog.clear();
        TransactionEngine.currentLsn      = 0;
        TransactionEngine.checkpointLsn   = 0;
        TransactionEngine.flushedToDisklsn = 0;

        TransactionEngine.crashRecovery();
        System.out.println("恢复后 data.ibd: " + DiskStore.loadDataFromDisk());
        System.out.println("✓ Redo Log WAL：已 fsync 的日志必能重放，已提交事务不丢失");
        System.out.println("  checkpoint_lsn=0 → 恢复时重放所有已落盘 Redo → 找到 COMMIT 标记 → 重建 data.ibd");
    }

    // ==================== 场景 4：COMMIT 前崩溃 ====================

    /**
     * 场景4：COMMIT 前宕机 —— 分两个子场景讨论
     * <p>
     * 场景4 核心问题：Redo Log 也不是每次都立即 fsync，崩溃时丢了怎么办？
     * <p>
     * 关键约束（WAL）：
     *   数据脏页落盘前，该页对应的 Redo Log 必须已 fsync。
     *   Page Cleaner 刷脏页时会检查：page.newest_modification <= flushed_to_disk_lsn
     *   → 如果 Redo 未 fsync，脏页就不会被刷盘
     *   → Redo 和脏页的"丢失"是同步的：要丢一起丢
     * <p>
     * 子场景4a：Log Buffer 完全未 fsync（最常见）
     *   → Redo 没了，WAL 保证脏页也没落盘，data.ibd 还是干净值，无需任何操作
     * <p>
     * 子场景4b：Redo for Undo 已 fsync（后台 log flusher 已刷），但 COMMIT 的 Redo 未 fsync
     *   → 有 Undo 但没 COMMIT 标记，Undo Phase 重建 Undo Page 并回滚 Alice 脏页
     */
    static void scenario4_crashBeforeCommit() {
        // ── 子场景4a ──────────────────────────────────────────────────────────
        System.out.println("\n---------- 子场景4a：Log Buffer 完全未 fsync ----------");
        System.out.println("最常见情况：COMMIT 前崩溃，Log Buffer 里的所有 Redo（含 Redo for Undo）都未落盘");
        {
            long txId = TransactionEngine.txIdCounter++;
            System.out.println("[BEGIN tx=" + txId + "] Alice 转 " + TRANSFER_AMT
                    + " 元给 Bob（不提交，不 fsync）");
            TransactionEngine.update(txId, "Alice", ALICE_AFTER);
            TransactionEngine.update(txId, "Bob",   BOB_AFTER);

            System.out.println("\n[CRASH!] 宕机。Log Buffer 全部丢失（未 fsync）");
            System.out.println("WAL 约束保证：Redo 未 fsync → 对应脏页也不会被 Page Cleaner 落盘");
            System.out.println("  → data.ibd 仍是初始值 Alice=" + ALICE_INIT + ", Bob=" + BOB_INIT + "（干净，无需恢复）");
            System.out.println("  → ib_logfile0 里没有任何该事务的记录");
            System.out.println("  → undo_001.ibu 里也没有 Undo（Redo for Undo 未落盘，Page Cleaner 未刷 Undo Page）");

            TransactionEngine.bufferPool.clear();
            TransactionEngine.undoLog.clear();
            TransactionEngine.undoPageDirty = false;
            TransactionEngine.currentLsn = 0;

            TransactionEngine.crashRecovery();
            System.out.println("恢复后 data.ibd: " + DiskStore.loadDataFromDisk());
            System.out.println("✓ 子场景4a：Redo 未落盘 = 脏页也未落盘，data.ibd 本来就干净，无需任何回滚");
        }

        // ── 子场景4b ──────────────────────────────────────────────────────────
        resetDisk();
        System.out.println("\n---------- 子场景4b：Redo for Undo 已 fsync，但 COMMIT 未 fsync ----------");
        System.out.println("触发条件：Log Buffer 被后台 log flusher 定时刷盘（Redo for Undo 落盘了），");
        System.out.println("          但 COMMIT 的 Redo 还在 Log Buffer 里，此时崩溃");
        {
            long txId = TransactionEngine.txIdCounter++;
            System.out.println("[BEGIN tx=" + txId + "] Alice 转 " + TRANSFER_AMT + " 元给 Bob（不提交）");
            TransactionEngine.update(txId, "Alice", ALICE_AFTER);
            TransactionEngine.update(txId, "Bob",   BOB_AFTER);

            System.out.println("\n[后台 log flusher] 定时将 Log Buffer 中的 Redo for Undo 写入 ib_logfile0（fsync）");
            System.out.println("  注意：此时 COMMIT Redo 还没写，ib_logfile0 里只有 MLOG_UNDO_INSERT，没有 COMMIT 标记");
            TransactionEngine.flushLogBufferToDisk();

            System.out.println("\n[CRASH!] 宕机。COMMIT 未执行，ib_logfile0 无 COMMIT block");
            System.out.println("关键差异：此时 Redo for Undo 已落盘（lsn<=flushedToDisklsn），");
            System.out.println("  → WAL 约束不阻止 Alice 脏页落盘 → 模拟最坏情况：Alice 脏页恰好被 Page Cleaner 刷入");

            Map<String, Integer> partial = new LinkedHashMap<>();
            partial.put("Alice", ALICE_AFTER);  // 8000（脏页已落盘）
            partial.put("Bob",   BOB_INIT);     // 5000（未落盘）
            DiskStore.flushDataToDisk(partial);
            System.out.println("崩溃后 data.ibd: Alice=" + ALICE_AFTER + "（已落），Bob=" + BOB_INIT
                    + "（未落）← 不一致！期望 Undo Phase 回滚至 Alice=" + ALICE_INIT + ", Bob=" + BOB_INIT);
            System.out.println("ib_logfile0 有 MLOG_UNDO_INSERT（Redo for Undo），无 COMMIT 标记");

            TransactionEngine.bufferPool.clear();
            TransactionEngine.undoLog.clear();
            TransactionEngine.undoPageDirty = false;
            TransactionEngine.currentLsn = 0;

            TransactionEngine.crashRecovery();
            System.out.println("恢复后 data.ibd: " + DiskStore.loadDataFromDisk());
            System.out.println("✓ 子场景4b：Redo for Undo 落盘 → Redo Phase 重建 Undo Page");
            System.out.println("            没有 COMMIT 标记 → Undo Phase 回滚所有修改，恢复一致性");
        }
    }

    // ==================== 场景 5：flushPolicy=2 的风险 ====================

    /**
     * 场景5：innodb_flush_log_at_trx_commit=2 的风险 —— OS 崩溃丢已提交事务
     * <p>
     * 演示要点：
     *   · flushPolicy=2 时，COMMIT 只 write 到 OS page cache，不立即 fsync
     *   · OS 崩溃（断电）后 page cache 丢失，ib_logfile0 中该事务 block isFlushed=false
     *   · 崩溃恢复读不到该事务的 Redo → 无法重放 → 已提交数据丢失！
     * <p>
     * 适用场景：允许少量丢失，追求极致写性能（如日志流水表、临时统计表）
     * 金融/支付系统：必须使用 innodb_flush_log_at_trx_commit=1（双1配置）
     */
    static void scenario5_flushPolicy2Risk() {
        TransactionEngine.flushPolicy = 2;
        System.out.println("切换 innodb_flush_log_at_trx_commit=2（COMMIT 只 write 到 OS cache，不立即 fsync）");

        long txId = TransactionEngine.txIdCounter++;
        System.out.println("[BEGIN tx=" + txId + "] Alice 转 " + TRANSFER_AMT + " 元给 Bob");
        TransactionEngine.update(txId, "Alice", ALICE_AFTER);
        TransactionEngine.update(txId, "Bob",   BOB_AFTER);
        TransactionEngine.commit(txId); // ib_logfile0 中该事务 block isFlushed=false（模拟 OS cache 未 fsync）

        System.out.println("\n[CRASH!] 机器断电（OS 崩溃），OS page cache 丢失！");
        System.out.println("ib_logfile0 中该事务 block isFlushed=false -> Redo Phase 无法重放");
        Map<String, Integer> stale = new LinkedHashMap<>();
        stale.put("Alice", ALICE_INIT);
        stale.put("Bob",   BOB_INIT);
        DiskStore.flushDataToDisk(stale);
        TransactionEngine.flushedToDisklsn = 0;
        TransactionEngine.bufferPool.clear();
        TransactionEngine.currentLsn = 0;

        TransactionEngine.crashRecovery();
        System.out.println("恢复后 data.ibd: " + DiskStore.loadDataFromDisk());
        System.out.println("✗ 已提交事务丢失！这是 innodb_flush_log_at_trx_commit=2 的已知风险");
        System.out.println("  适用场景：允许少量丢失，追求极致写性能（如日志流水表、临时统计表）");
        System.out.println("  金融/支付系统：必须使用 innodb_flush_log_at_trx_commit=1");

        TransactionEngine.flushPolicy = 1; // 恢复默认值
    }

    // ==================== 场景 6：2PC 崩溃点1 ====================

    /**
     * 场景6：两阶段提交 —— 崩溃点1（Redo PREPARE 已落盘，Binlog 尚未写入）
     * <p>
     * 时序：Redo PREPARE fsync ✓ → [CRASH!] → Binlog 未写
     * <p>
     * 崩溃恢复逻辑：
     *   ① Redo Phase：从 ib_logfile0 读到 PREPARE 标记（isFlushed=true，isPrepare=true）
     *   ② 2PC Check：查 Binlog → 无 XID_COMMIT（Binlog 文件甚至不存在）
     *   ③ 判定：PREPARE 无 XID → 回滚（Undo Phase 撤销 Alice/Bob 修改）
     * <p>
     * 结论：Alice=10000, Bob=5000（事务被完整回滚）
     * 主从一致性：主库回滚，Binlog 无记录，从库从未执行，主从一致 ✓
     */
    static void scenario6_crashAfterPrepareBeforeBinlog() {
        System.out.println("━━━━━━━━━━ 两阶段提交时序回顾 ━━━━━━━━━━");
        System.out.println("  Phase1: Redo PREPARE fsync  ← 崩溃在这里之后");
        System.out.println("  [CRASH!] ← 崩溃点1");
        System.out.println("  Phase2: Binlog XID_COMMIT fsync  ← 未到达");
        System.out.println("  Phase3: Redo COMMIT fsync  ← 未到达");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        long txId = TransactionEngine.txIdCounter++;
        System.out.println("\n[BEGIN tx=" + txId + "] Alice 转 " + TRANSFER_AMT + " 元给 Bob");
        TransactionEngine.update(txId, "Alice", ALICE_AFTER);
        TransactionEngine.update(txId, "Bob",   BOB_AFTER);

        // 手工执行 Phase1（Redo PREPARE fsync），然后模拟崩溃，跳过 Phase2/Phase3
        System.out.println("\n-- [Phase1] 写 Redo PREPARE 并 fsync ib_logfile0 --");
        TransactionEngine.currentLsn++;
        RedoLogRecord prepareRecord = RedoLogRecord.commit(TransactionEngine.currentLsn, txId);
        prepareRecord.isCommit  = false;
        prepareRecord.isPrepare = true;
        TransactionEngine.writeToRedoLogBuffer(prepareRecord);
        TransactionEngine.flushLogBufferToDisk();
        System.out.println("  ib_logfile0 fsync 完成：Redo PREPARE 已持久化 (LSN="
                + TransactionEngine.currentLsn + ", isPrepare=true)");
        System.out.println("  此时事务处于 PREPARE 态：数据变更有 WAL 保证，但 Binlog 还未写");

        System.out.println("\n[CRASH!] 崩溃！Phase2（Binlog fsync）尚未执行");
        System.out.println("  ib_logfile0 : 有 PREPARE 标记（isFlushed=true）✓");
        System.out.println("  binlog 文件 : 不存在 / 无任何事件 ✗");
        System.out.println("  data.ibd    : 脏页已被 Page Cleaner 刷入（模拟最坏情况）");

        // 模拟：脏页已落盘（WAL 满足，Page Cleaner 允许刷入），但事务未提交
        Map<String, Integer> partial = new LinkedHashMap<>();
        partial.put("Alice", ALICE_AFTER);
        partial.put("Bob",   BOB_AFTER);
        DiskStore.flushDataToDisk(partial);

        // Binlog Cache 还在内存里，模拟崩溃直接丢弃
        TransactionEngine.binlogCache.clear();
        System.out.println("  binlogCache 丢失（模拟崩溃，Binlog 未写入文件）");

        // 重置内存，模拟重启
        TransactionEngine.bufferPool.clear();
        TransactionEngine.undoLog.clear();
        TransactionEngine.undoPageDirty = false;
        TransactionEngine.currentLsn = 0;
        System.out.println("  MySQL 重启，内存全部清空，开始 Crash Recovery ...");

        TransactionEngine.crashRecovery();

        System.out.println("\n恢复后 data.ibd: " + DiskStore.loadDataFromDisk());
        System.out.println("━━━━━━━━━━ 场景6 崩溃点1 结论 ━━━━━━━━━━");
        System.out.println("  ✓ Redo 有 PREPARE，Binlog 无 XID → 2PC Check 判定：回滚");
        System.out.println("  ✓ Alice=" + ALICE_INIT + ", Bob=" + BOB_INIT
                + "（事务被完整回滚，恢复初始值）");
        System.out.println("  ✓ 主从一致：主库回滚，Binlog 无记录，从库从未执行，双方均无此变更");
        System.out.println("  ✓ 原子性保证：整个事务要么全成功，要么全失败（此处全失败）");
    }

    // ==================== 场景 7：2PC 崩溃点2 ====================

    /**
     * 场景7：两阶段提交 —— 崩溃点2（Binlog XID_COMMIT 已落盘，Redo COMMIT 尚未写入）
     * <p>
     * 时序：Redo PREPARE fsync ✓ → Binlog XID_COMMIT fsync ✓ → [CRASH!] → Redo COMMIT 未写
     * <p>
     * 崩溃恢复逻辑：
     *   ① Redo Phase：从 ib_logfile0 读到 PREPARE 标记（isFlushed=true，isPrepare=true）
     *                  没有 COMMIT 标记（Phase3 未完成）
     *   ② 2PC Check：查 Binlog → 有 XID_COMMIT（isFlushed=true）
     *   ③ 判定：PREPARE 有 XID → 补提交（重放 Redo 数据日志，补写 COMMIT 标记）
     * <p>
     * 结论：Alice=8000, Bob=7000（事务被补提交，数据恢复到转账后状态）
     * 主从一致性：Binlog 已落盘（从库已/将要执行），主库补提交，主从一致 ✓
     * <p>
     * 这也是 Phase3（Redo COMMIT fsync）可选的原因：
     *   有 Binlog XID 作为锚点，即使 Phase3 崩溃，重启也能补提交，不影响正确性
     */
    static void scenario7_crashAfterBinlogBeforeCommit() {
        System.out.println("━━━━━━━━━━ 两阶段提交时序回顾 ━━━━━━━━━━");
        System.out.println("  Phase1: Redo PREPARE fsync  ← 已完成 ✓");
        System.out.println("  Phase2: Binlog XID_COMMIT fsync  ← 已完成 ✓");
        System.out.println("  [CRASH!] ← 崩溃点2");
        System.out.println("  Phase3: Redo COMMIT fsync  ← 未到达");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        long txId = TransactionEngine.txIdCounter++;
        System.out.println("\n[BEGIN tx=" + txId + "] Alice 转 " + TRANSFER_AMT + " 元给 Bob");
        TransactionEngine.update(txId, "Alice", ALICE_AFTER);
        TransactionEngine.update(txId, "Bob",   BOB_AFTER);

        // Phase1：Redo PREPARE fsync
        System.out.println("\n-- [Phase1] 写 Redo PREPARE 并 fsync ib_logfile0 --");
        TransactionEngine.currentLsn++;
        RedoLogRecord prepareRecord = RedoLogRecord.commit(TransactionEngine.currentLsn, txId);
        prepareRecord.isCommit  = false;
        prepareRecord.isPrepare = true;
        TransactionEngine.writeToRedoLogBuffer(prepareRecord);
        TransactionEngine.flushLogBufferToDisk();
        System.out.println("  ib_logfile0 fsync 完成：PREPARE 持久化 (LSN=" + TransactionEngine.currentLsn + ")");

        // Phase2：Binlog XID_COMMIT fsync —— 事务提交的真正分界线！
        System.out.println("\n-- [Phase2] 写 Binlog Cache 并 fsync binlog 文件 --");
        TransactionEngine.binlogCache.add(BinlogEntry.xidCommit(txId));
        DiskStore.writeBinlogEntries(TransactionEngine.binlogCache, true); // sync_binlog=1
        TransactionEngine.binlogCache.clear();
        System.out.println("  binlog fsync 完成：XID_COMMIT 已持久化 (tx=" + txId + ") ← 提交的真正分界线！");
        System.out.println("  一旦 XID_COMMIT 落盘，无论后续是否崩溃，这个事务 必须 提交（主从均有记录）");

        System.out.println("\n[CRASH!] 崩溃！Phase3（Redo COMMIT fsync）尚未执行");
        System.out.println("  ib_logfile0 : 有 PREPARE 标记，无 COMMIT 标记");
        System.out.println("  binlog 文件 : 有 XID_COMMIT 事件（isFlushed=true）✓");
        System.out.println("  data.ibd    : 脏页可能未落盘（最坏情况：仍是旧值）");

        // 模拟崩溃：脏页未落盘，data.ibd 仍是旧值
        Map<String, Integer> stale = new LinkedHashMap<>();
        stale.put("Alice", ALICE_INIT);
        stale.put("Bob",   BOB_INIT);
        DiskStore.flushDataToDisk(stale);
        System.out.println("  data.ibd 强制恢复为旧值: Alice=" + ALICE_INIT + ", Bob=" + BOB_INIT
                + "（脏页未落盘）");

        // 重置内存，模拟重启
        TransactionEngine.bufferPool.clear();
        TransactionEngine.undoLog.clear();
        TransactionEngine.undoPageDirty = false;
        TransactionEngine.currentLsn = 0;
        System.out.println("  MySQL 重启，内存全部清空，开始 Crash Recovery ...");

        TransactionEngine.crashRecovery();

        System.out.println("\n恢复后 data.ibd: " + DiskStore.loadDataFromDisk());
        System.out.println("━━━━━━━━━━ 场景7 崩溃点2 结论 ━━━━━━━━━━");
        System.out.println("  ✓ Redo 有 PREPARE，Binlog 有 XID → 2PC Check 判定：补提交");
        System.out.println("  ✓ Alice=" + ALICE_AFTER + ", Bob=" + BOB_AFTER
                + "（事务被补提交，数据恢复到转账后状态）");
        System.out.println("  ✓ 主从一致：Binlog XID 已落盘，从库会/已执行，主库补提交，双方均有此变更");
        System.out.println("  ✓ 这就是 Phase3（Redo COMMIT）可选的原因：有 Binlog XID 即可保证补提交");
        System.out.println("  ✓ sync_binlog=1 + innodb_flush_log_at_trx_commit=1（双1）保证了无论哪个崩溃点都一致");
    }
}

