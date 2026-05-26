/**
 * ============================================================
 * InnoDBAtomicityDemo —— 入口类（程序启动点）
 * ============================================================
 * <p>
 * 本文件仅包含 main 方法，负责按序调用各演示场景。
 * 具体实现分散在 innodb/ 子目录下的四个模块中：
 * <pre>
 *   innodb/
 *   ├── DiskStore.java         — 磁盘 IO 层（文件路径常量 + 所有磁盘读写操作）
 *   ├── LockManager.java       — 锁管理器（全局锁表 + 加锁/解锁接口）
 *   ├── TransactionEngine.java — 核心事务引擎（内存状态 + read/update/commit/rollback/crashRecovery）
 *   └── Scenarios.java         — 演示场景集（7 个独立场景 + resetDisk 初始化）
 * </pre>
 * <p>
 * 基础实体类（共享常量 + UndoLogRecord / RedoLogRecord / LockEntry / BinlogEntry）
 * 定义在 MysqlDemoBase.java 中，所有模块均继承它。
 * <p>
 * 【运行方式（从 demo/ 目录编译并运行）】
 * <pre>
 *   # 1. 编译所有类（包含 innodb/ 子目录）
 *   cd 面试学习/mysql/demo
 *   javac -encoding UTF-8 MysqlDemoBase.java innodb/DiskStore.java innodb/LockManager.java \
 *         innodb/TransactionEngine.java innodb/Scenarios.java InnoDBAtomicityDemo.java
 *
 *   # 2. 运行入口类
 *   java InnoDBAtomicityDemo
 *
 *   # 3. 查看磁盘文件（可选）
 *   cat disk/data.ibd                    # 数据文件（文本格式）
 *   cat disk/binlog                      # Binlog 文件（文本格式）
 *   xxd disk/ib_logfile0 | head -32      # Redo Log 文件（二进制，每 64B 一个 block）
 *   cat disk/ib_logfile_header           # checkpoint_lsn + write_head
 * </pre>
 * <p>
 * 【磁盘文件说明（本 Demo 真实写入 disk/ 目录）】
 * <pre>
 *   disk/data.ibd          — 模拟数据文件（.ibd），文本 key=val 格式，每行一条
 *                            真实：16KB 数据页，紧凑二进制，有页头/行格式/页目录
 *   disk/undo_001.ibu      — 模拟 Undo 表空间文件，文本格式，每行一条 Undo Record
 *                            真实：Rollback Segment → Undo Page（16KB 二进制页）
 *   disk/ib_logfile0       — 模拟环形 Redo Log 文件，二进制格式（每块 64B 定长 block）
 *                            真实：ib_logfile0/1 轮换循环写，每 block 512B（对齐扇区）
 *   disk/ib_logfile_header — 模拟日志文件头，记录 checkpoint_lsn 和 write_head
 *                            真实：文件头 4096B，含 LOG_CHECKPOINT_1/2 两个 checkpoint 页
 *   disk/binlog            — 模拟 mysql-bin.000001（Server 层），文本每行一条事件
 *                            真实：rotate 新文件，文件头含 magic number + FDE 事件
 * </pre>
 * <p>
 * 【与真实 InnoDB 的整体差异（简化说明）】
 * <pre>
 *   ① 数据粒度  ：真实操作 16KB 数据页，Demo 用文本 key=val 行代替
 *   ② Undo 存储 ：真实分层结构（Rollback Seg→Undo Seg→Undo Page），Demo 用文本行
 *   ③ Log Buffer：真实 16MB 连续内存，按 512B Log Block 对齐；Demo 用对象数组，写满触发 flush
 *   ④ ib_logfile ：真实环形两文件循环写；Demo 用 64B 定长 block 模拟环形（head 取模 CAPACITY）
 *   ⑤ LSN        ：真实按字节数单调递增；Demo 每条日志 +1
 *   ⑥ 并发       ：真实有锁/MVCC/trx_sys；Demo 单线程
 *   ⑦ Purge 线程 ：真实异步回收 Undo Log；Demo 仅打印说明
 * </pre>
 */
public class InnoDBAtomicityDemo {

    public static void main(String[] args) {

        // ══════════════════════════════════════════════════════════════════
        // 场景 1：正常转账 —— 完整 2PC 流程（Alice 转 2000 给 Bob）
        // 演示：加锁 → Undo Log → Redo Log Buffer → Binlog Cache → 两阶段提交
        // ══════════════════════════════════════════════════════════════════
        System.out.println("\n" + "=".repeat(70));
        System.out.println("  场景1：正常转账 —— 完整 2PC 流程");
        System.out.println("=".repeat(70));
        Scenarios.resetDisk();
        Scenarios.scenario1_normalTransfer();

        // ══════════════════════════════════════════════════════════════════
        // 场景 2：多步回滚 —— 5次 UPDATE + ROLLBACK
        // 演示：Undo Log 的 undoNo 链 + roll_pointer 版本链 + 逆序回滚
        // ══════════════════════════════════════════════════════════════════
        System.out.println("\n" + "=".repeat(70));
        System.out.println("  场景2：多步回滚 —— 5次UPDATE + ROLLBACK");
        System.out.println("=".repeat(70));
        Scenarios.resetDisk();
        Scenarios.scenario2_rollback();

        // ══════════════════════════════════════════════════════════════════
        // 场景 3：COMMIT 后崩溃 —— Redo Log WAL 保证已提交事务不丢失
        // 演示：ib_logfile0 已 fsync → crashRecovery Redo Phase 重放
        // ══════════════════════════════════════════════════════════════════
        System.out.println("\n" + "=".repeat(70));
        System.out.println("  场景3：COMMIT 后崩溃 —— Redo Log WAL 保证数据");
        System.out.println("=".repeat(70));
        Scenarios.resetDisk();
        Scenarios.scenario3_crashAfterCommit();

        // ══════════════════════════════════════════════════════════════════
        // 场景 4：COMMIT 前崩溃 —— 分两个子场景
        //   4a：Log Buffer 完全未 fsync，无需恢复
        //   4b：Redo for Undo 已 fsync，Undo Phase 回滚脏页
        // ══════════════════════════════════════════════════════════════════
        System.out.println("\n" + "=".repeat(70));
        System.out.println("  场景4：COMMIT 前崩溃（4a: Redo未落盘 / 4b: Undo已落盘）");
        System.out.println("=".repeat(70));
        Scenarios.resetDisk();
        Scenarios.scenario4_crashBeforeCommit();

        // ══════════════════════════════════════════════════════════════════
        // 场景 5：innodb_flush_log_at_trx_commit=2 的风险
        // 演示：OS 崩溃后 page cache 丢失，已提交事务数据丢失
        // ══════════════════════════════════════════════════════════════════
        System.out.println("\n" + "=".repeat(70));
        System.out.println("  场景5：innodb_flush_log_at_trx_commit=2 的风险");
        System.out.println("=".repeat(70));
        Scenarios.resetDisk();
        Scenarios.scenario5_flushPolicy2Risk();

        // ══════════════════════════════════════════════════════════════════
        // 场景 6：2PC 崩溃点1 —— Redo PREPARE 已落盘，Binlog 未写
        // 演示：PREPARE 有 XID 无 → 2PC Check 判定回滚
        // ══════════════════════════════════════════════════════════════════
        System.out.println("\n" + "=".repeat(70));
        System.out.println("  场景6：2PC 崩溃点1（PREPARE后、Binlog前崩溃）→ 回滚");
        System.out.println("=".repeat(70));
        Scenarios.resetDisk();
        Scenarios.scenario6_crashAfterPrepareBeforeBinlog();

        // ══════════════════════════════════════════════════════════════════
        // 场景 7：2PC 崩溃点2 —— Binlog XID_COMMIT 已落盘，Redo COMMIT 未写
        // 演示：PREPARE 有 XID → 2PC Check 判定补提交
        // ══════════════════════════════════════════════════════════════════
        System.out.println("\n" + "=".repeat(70));
        System.out.println("  场景7：2PC 崩溃点2（Binlog后、Redo COMMIT前崩溃）→ 补提交");
        System.out.println("=".repeat(70));
        Scenarios.resetDisk();
        Scenarios.scenario7_crashAfterBinlogBeforeCommit();

        System.out.println("\n" + "=".repeat(70));
        System.out.println("  全部场景演示完毕");
        System.out.println("  磁盘文件位于: " + DiskStore.DISK_DIR);
        System.out.println("    cat disk/data.ibd                 — 查看当前数据页");
        System.out.println("    cat disk/binlog                   — 查看 Binlog 事件");
        System.out.println("    xxd disk/ib_logfile0 | head -32   — 查看 Redo Log 二进制块");
        System.out.println("    cat disk/ib_logfile_header        — 查看 checkpoint_lsn");
        System.out.println("=".repeat(70));
    }
}

