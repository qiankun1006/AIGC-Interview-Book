package demo;

import java.util.*;

/**
 * ============================================================
 * MySQL 两阶段提交（2PC）完整模拟
 * ============================================================
 *
 * 【为什么需要两阶段提交？】
 *
 * MySQL 有两套日志：
 *   - Redo Log（InnoDB 引擎层）：记录物理变更，用于崩溃恢复
 *   - Binlog（Server 层）：记录逻辑变更，用于主从复制 / 数据恢复 / 审计
 *
 * 如果不做协调，两者会出现不一致：
 *   场景A：先写 Redo Log 提交，再写 Binlog → Binlog 前崩溃
 *          → InnoDB 认为已提交，Binlog 没有这条记录
 *          → 主从复制丢失这次变更，主从不一致
 *   场景B：先写 Binlog，再写 Redo Log 提交 → Redo Log 前崩溃
 *          → Binlog 有记录，InnoDB 未提交
 *          → 从库执行了这次变更，主库没有 → 主从不一致
 *
 * 两阶段提交解决方案：把 Redo Log 的写入拆成两个阶段
 *   Prepare 阶段：Redo Log 写入 prepare 状态，fsync
 *   Binlog 阶段：写入 Binlog，fsync
 *   Commit 阶段：Redo Log 写入 commit 状态，fsync（或不 fsync，看参数）
 *
 * 【崩溃恢复判断逻辑（面试核心！）】
 * MySQL 重启扫描 Redo Log，对每个 prepare 状态的事务：
 *   ① 去 Binlog 中查找对应的 XID（事务ID）
 *   ② 找到了 → 说明 Binlog 已写，提交事务（commit Redo Log）
 *   ③ 找不到 → 说明 Binlog 未写，回滚事务
 *
 * 这样无论在哪个阶段崩溃，重启后都能恢复到一致状态。
 *
 * 【三个崩溃点分析】
 *   崩溃点1：Redo Log prepare 之后，Binlog 之前崩溃
 *     → 重启：Redo Log 有 prepare，Binlog 无 XID → 回滚 ✓
 *   崩溃点2：Binlog 之后，Redo Log commit 之前崩溃
 *     → 重启：Redo Log 有 prepare，Binlog 有 XID → 提交 ✓
 *   崩溃点3：Redo Log commit 之后崩溃
 *     → 重启：Redo Log 有 commit → 正常提交 ✓
 *
 * 【相关参数（面试常问）】
 *   sync_binlog=1：每次事务提交都 fsync Binlog（最安全，主从一致性保证）
 *   sync_binlog=0：OS 自动刷（性能好，崩溃可能丢 Binlog）
 *   innodb_flush_log_at_trx_commit=1：每次 COMMIT 都 fsync Redo Log
 *   双1配置（两者都=1）：最安全，生产环境金融场景标配
 *
 * 【组提交（Group Commit）—— 性能优化】
 *   问题：双1配置下每个事务都做 2 次 fsync（prepare + binlog），高并发下 IOPS 成瓶颈
 *   优化：多个并发事务的 Binlog 合并成一次 fsync（Binlog Group Commit）
 *   MySQL 5.6+ 引入三阶段 Binlog 组提交：flush → sync → commit
 *
 * 【XID（Transaction ID in Binlog）】
 *   Redo Log 里的 prepare 记录和 Binlog 里的事务都带同一个 XID
 *   崩溃恢复时就靠 XID 关联 Redo Log 和 Binlog
 */
public class TwoPhaseCommitDemo {

    // ==================== 组件定义 ====================

    /**
     * Redo Log 记录（InnoDB 引擎层）
     * 两阶段提交中有三种状态：DATA / PREPARE / COMMIT
     */
    static class RedoLogEntry {
        enum State { DATA, PREPARE, COMMIT }

        long xid;          // 事务 XID，与 Binlog 中的 XID 对应，崩溃恢复靠它关联
        String key;
        int newValue;
        State state;
        boolean flushed;   // 是否已 fsync 到 ib_logfile

        // 数据变更记录
        RedoLogEntry(long xid, String key, int newValue) {
            this.xid = xid; this.key = key; this.newValue = newValue;
            this.state = State.DATA; this.flushed = false;
        }

        // Prepare/Commit 标记
        RedoLogEntry(long xid, State state) {
            this.xid = xid; this.state = state; this.flushed = false;
        }

        @Override public String toString() {
            String f = flushed ? "✓落盘" : "○内存";
            if (state == State.DATA)
                return String.format("[%s] RedoLog DATA  xid=%-3d key=%-6s newVal=%d", f, xid, key, newValue);
            return String.format("[%s] RedoLog %-8s xid=%-3d", f, state, xid);
        }
    }

    /**
     * Binlog 记录（MySQL Server 层）
     * 记录逻辑操作（SQL 语句或行变更），用于主从复制和数据恢复
     *
     * 真实 Binlog 有三种格式：
     *   STATEMENT：记录 SQL 语句（可能不确定，如 NOW()）
     *   ROW：记录行变更前后的值（最精确，主从一致性最好）
     *   MIXED：自动选择
     * 生产环境推荐 ROW 格式
     */
    static class BinlogEntry {
        enum Type { BEGIN, ROW_CHANGE, XID_COMMIT }

        long xid;          // 事务 XID，与 Redo Log prepare 中的 XID 对应
        Type type;
        String key;
        int oldValue, newValue;
        boolean flushed;   // 是否已 fsync 到 binlog 文件

        // BEGIN 事件
        static BinlogEntry begin(long xid) {
            BinlogEntry e = new BinlogEntry();
            e.xid = xid; e.type = Type.BEGIN; e.flushed = false;
            return e;
        }

        // 行变更事件（ROW 格式）
        static BinlogEntry rowChange(long xid, String key, int oldVal, int newVal) {
            BinlogEntry e = new BinlogEntry();
            e.xid = xid; e.type = Type.ROW_CHANGE;
            e.key = key; e.oldValue = oldVal; e.newValue = newVal; e.flushed = false;
            return e;
        }

        // XID 事件（Binlog 的 commit 标记，带 XID）
        static BinlogEntry xidCommit(long xid) {
            BinlogEntry e = new BinlogEntry();
            e.xid = xid; e.type = Type.XID_COMMIT; e.flushed = false;
            return e;
        }

        @Override public String toString() {
            String f = flushed ? "✓落盘" : "○内存";
            switch (type) {
                case BEGIN:      return String.format("[%s] Binlog BEGIN      xid=%d", f, xid);
                case XID_COMMIT: return String.format("[%s] Binlog XID_COMMIT xid=%-3d  ← 这是崩溃恢复的关键标志", f, xid);
                case ROW_CHANGE: return String.format("[%s] Binlog ROW_CHANGE xid=%-3d key=%-6s %d→%d", f, xid, key, oldValue, newValue);
                default: return "";
            }
        }
    }

    // ==================== 全局状态 ====================

    /** 磁盘数据（.ibd 文件） */
    static Map<String, Integer> diskData   = new HashMap<>();
    /** Buffer Pool（内存脏页） */
    static Map<String, Integer> bufferPool = new HashMap<>();
    /** Redo Log 文件（ib_logfile，循环写） */
    static List<RedoLogEntry>   redoLog    = new ArrayList<>();
    /** Binlog 文件（mysql-bin.000001 等） */
    static List<BinlogEntry>    binlog     = new ArrayList<>();

    /** sync_binlog 配置：1=每次提交 fsync，0=OS 自动刷 */
    static int syncBinlog = 1;
    /** innodb_flush_log_at_trx_commit：1=每次 fsync */
    static int flushRedoPolicy = 1;

    /** XID 计数器（真实 MySQL 中 XID 是 server_id + 序号） */
    static long xidCounter = 100;

    // ==================== 核心流程 ====================

    static long beginTransaction(String name) {
        long xid = xidCounter++;
        System.out.printf("  [BEGIN] %s  xid=%d%n", name, xid);
        // Binlog 写入 BEGIN 事件
        BinlogEntry beginEvt = BinlogEntry.begin(xid);
        binlog.add(beginEvt);
        System.out.println("  " + beginEvt + "  （Binlog 写入 BEGIN 事件，标记事务开始）");
        return xid;
    }

    /**
     * 数据变更：同时写 Buffer Pool + Redo Log Data + Binlog RowChange
     * 注意：Binlog RowChange 在事务未提交时已写入 Binlog cache（内存），
     *       提交时才 fsync 到磁盘文件
     */
    static void update(long xid, String key, int newValue) {
        int oldValue = bufferPool.containsKey(key)
                ? bufferPool.get(key)
                : diskData.getOrDefault(key, 0);

        System.out.printf("    旧值: %s=%d%n", key, oldValue);

        // 1. 写 Redo Log Data（仅在内存 Log Buffer）
        RedoLogEntry redoData = new RedoLogEntry(xid, key, newValue);
        redoLog.add(redoData);
        System.out.println("    " + redoData + "  → 记录新值，崩溃时用于重放");

        // 2. 修改 Buffer Pool 脏页
        bufferPool.put(key, newValue);
        System.out.printf("    [Buffer Pool] %s: %d → %d（脏页，磁盘仍是 %d）%n",
                key, oldValue, newValue, oldValue);

        // 3. 写 Binlog Row Change（写入 Binlog cache，提交时才真正 fsync）
        BinlogEntry rowEvt = BinlogEntry.rowChange(xid, key, oldValue, newValue);
        binlog.add(rowEvt);
        System.out.println("    " + rowEvt + "  → 记录行变更，供从库回放");
    }

    /**
     * 两阶段提交完整流程
     *
     * ─────────────────────────────────────────────────────────
     * Phase 1 - Prepare（InnoDB 内部）
     * ─────────────────────────────────────────────────────────
     *   1a. InnoDB 将事务的 Redo Log 标记为 PREPARE 状态
     *   1b. fsync Redo Log（含 prepare 标记）到磁盘
     *   完成后：即使崩溃，InnoDB 也知道有个 prepare 状态的事务
     *
     * ─────────────────────────────────────────────────────────
     * Phase 2 - Commit（跨引擎层和 Server 层）
     * ─────────────────────────────────────────────────────────
     *   2a. Server 层写 Binlog（含 XID_COMMIT 事件），fsync
     *       完成后：Binlog 落盘，从库可以复制这笔事务
     *   2b. InnoDB 将 Redo Log 标记为 COMMIT 状态
     *       （此步骤是否 fsync 取决于 innodb_flush_log_at_trx_commit）
     *   2c. 返回客户端 "提交成功"
     *
     * 关键点：Binlog fsync 成功 = 事务对外可见（主从一致的分界线）
     */
    static void twoPhaseCommit(long xid, String txName) {
        System.out.println("\n┌─────────────────────────────────────────────────┐");
        System.out.println("│  两阶段提交：" + txName + " xid=" + xid);
        System.out.println("└─────────────────────────────────────────────────┘");

        // ── Phase 1: Prepare ──────────────────────────────
        System.out.println("\n  ▶ Phase 1 - Prepare（InnoDB 引擎层）");
        System.out.println("    目的：让 InnoDB 先持久化本次变更意图，为 Binlog 写入兜底");

        // 1a. 写 Redo Log Prepare 标记
        RedoLogEntry prepareEntry = new RedoLogEntry(xid, RedoLogEntry.State.PREPARE);
        redoLog.add(prepareEntry);
        System.out.println("    Step1a: 写入 Redo Log PREPARE 标记（含 XID=" + xid + "）");
        System.out.println("           " + prepareEntry);

        // 1b. fsync Redo Log
        System.out.println("    Step1b: fsync Redo Log（含 DATA + PREPARE）到磁盘 ib_logfile");
        for (RedoLogEntry e : redoLog) { if (e.xid == xid) e.flushed = true; }
        System.out.println("           fsync 完成 ✓");
        System.out.println("    ★ 此时若崩溃：重启发现 prepare 状态，去 Binlog 找 XID=" + xid);
        System.out.println("      → 找不到 XID → 说明 Binlog 未写 → 回滚本事务");

        printState("    ", xid);

        // ── Phase 2: Commit ───────────────────────────────
        System.out.println("\n  ▶ Phase 2 - Commit（Server 层 + InnoDB 引擎层）");

        // 2a. 写 Binlog XID_COMMIT 事件，fsync
        System.out.println("\n    Step2a: Server 层写 Binlog XID_COMMIT 事件，fsync binlog 文件");
        BinlogEntry xidEvt = BinlogEntry.xidCommit(xid);
        binlog.add(xidEvt);

        if (syncBinlog == 1) {
            // sync_binlog=1：每次提交 fsync，最安全
            for (BinlogEntry e : binlog) { if (e.xid == xid) e.flushed = true; }
            System.out.println("           " + xidEvt);
            System.out.println("           Binlog fsync 完成（sync_binlog=1）✓");
            System.out.println("    ★ 此时若崩溃：重启发现 prepare 状态，去 Binlog 找 XID=" + xid);
            System.out.println("      → 找到 XID → 说明 Binlog 已写 → 提交本事务（补写 commit 到 Redo Log）");
            System.out.println("    ★ Binlog fsync 成功 = 事务提交的真正分界线（主从一致性在此保证）");
        } else {
            System.out.println("           " + xidEvt + "  （sync_binlog=0，OS 自动刷，未立即落盘！）");
            System.out.println("    [警告] Binlog 未立即落盘，若此时崩溃，从库会丢失这笔事务");
        }

        printState("    ", xid);

        // 2b. InnoDB 写 Redo Log COMMIT 标记
        System.out.println("\n    Step2b: InnoDB 写 Redo Log COMMIT 标记");
        RedoLogEntry commitEntry = new RedoLogEntry(xid, RedoLogEntry.State.COMMIT);
        redoLog.add(commitEntry);

        if (flushRedoPolicy == 1) {
            commitEntry.flushed = true;
            System.out.println("           " + commitEntry + "  （innodb_flush_log_at_trx_commit=1，立即 fsync）");
            System.out.println("           注意：即使这步 fsync 失败/崩溃，因为 Binlog 已落盘，");
            System.out.println("           重启时 Redo Log 还是 prepare 状态，但 Binlog 有 XID → 补提交");
        } else {
            System.out.println("           " + commitEntry + "  （Redo Log commit 标记暂不 fsync）");
        }

        // 2c. 脏页异步刷回磁盘（后台 checkpoint）
        diskData.putAll(bufferPool);
        System.out.println("\n    Step2c: 脏页由后台 checkpoint 线程异步刷盘，不阻塞提交");
        System.out.println("           返回客户端：提交成功 ✓");
    }

    /**
     * 崩溃恢复：MySQL 重启时 InnoDB 自动执行
     *
     * 扫描 Redo Log 中所有 PREPARE 状态的事务：
     *   → 去 Binlog 中查找对应 XID
     *   → 有 XID_COMMIT → 提交（补写 Redo Log commit）
     *   → 无 XID_COMMIT → 回滚
     */
    static void crashRecovery(String scenario) {
        System.out.println("\n╔══════════════════════════════════════════════════════════╗");
        System.out.println("║  [Crash Recovery] MySQL 重启 - " + scenario);
        System.out.println("╚══════════════════════════════════════════════════════════╝");

        System.out.println("\n  Step1: 扫描 Redo Log，找出所有 PREPARE 状态的事务...");
        Set<Long> preparedXids  = new LinkedHashSet<>();
        Set<Long> committedXids = new LinkedHashSet<>();
        for (RedoLogEntry e : redoLog) {
            if (e.flushed && e.state == RedoLogEntry.State.PREPARE)  preparedXids.add(e.xid);
            if (e.flushed && e.state == RedoLogEntry.State.COMMIT)   committedXids.add(e.xid);
        }
        // 已有 commit 标记的不需要处理
        Set<Long> needCheck = new LinkedHashSet<>(preparedXids);
        needCheck.removeAll(committedXids);
        System.out.println("  Redo Log PREPARE 状态（需检查）：" + needCheck);
        System.out.println("  Redo Log COMMIT  状态（已完成）：" + committedXids);

        System.out.println("\n  Step2: 扫描 Binlog，收集所有已落盘的 XID_COMMIT...");
        Set<Long> binlogXids = new LinkedHashSet<>();
        for (BinlogEntry e : binlog) {
            if (e.flushed && e.type == BinlogEntry.Type.XID_COMMIT) binlogXids.add(e.xid);
        }
        System.out.println("  Binlog 中已落盘的 XID_COMMIT：" + binlogXids);

        System.out.println("\n  Step3: 对每个 PREPARE 事务做决策...");
        for (long xid : needCheck) {
            if (binlogXids.contains(xid)) {
                // Binlog 有 XID → 补提交（InnoDB 将 Redo Log 改为 commit）
                RedoLogEntry commitEntry = new RedoLogEntry(xid, RedoLogEntry.State.COMMIT);
                commitEntry.flushed = true;
                redoLog.add(commitEntry);
                // 将 Buffer Pool 数据写入磁盘
                System.out.println("  [XID=" + xid + "] Binlog 有 XID_COMMIT → 补提交（重放 Redo Log 修改到磁盘）");
                for (RedoLogEntry e : redoLog) {
                    if (e.xid == xid && e.state == RedoLogEntry.State.DATA && e.flushed) {
                        diskData.put(e.key, e.newValue);
                        System.out.println("    重放: " + e.key + " = " + e.newValue);
                    }
                }
                System.out.println("    ✓ 事务已提交，主从一致");
            } else {
                // Binlog 无 XID → 回滚
                System.out.println("  [XID=" + xid + "] Binlog 无 XID_COMMIT → 回滚（Undo Log 撤销修改）");
                System.out.println("    （这里简化，真实会读 Undo Log 逐条撤销）");
                // 从磁盘移除相关数据（简化）
                for (RedoLogEntry e : redoLog) {
                    if (e.xid == xid && e.state == RedoLogEntry.State.DATA) {
                        System.out.println("    回滚: " + e.key + " 恢复原值");
                    }
                }
                System.out.println("    ✓ 事务已回滚，主从一致");
            }
        }
        System.out.println("\n  [Recovery 完成] 磁盘数据: " + diskData);
    }

    /** 打印当前 Redo Log 和 Binlog 中与 xid 相关的条目 */
    static void printState(String indent, long xid) {
        System.out.println(indent + "当前日志状态 (xid=" + xid + "):");
        System.out.println(indent + "  Redo Log:");
        for (RedoLogEntry e : redoLog) {
            if (e.xid == xid) System.out.println(indent + "    " + e);
        }
        System.out.println(indent + "  Binlog:");
        for (BinlogEntry e : binlog) {
            if (e.xid == xid) System.out.println(indent + "    " + e);
        }
    }

    static void reset() {
        diskData.clear(); bufferPool.clear();
        redoLog.clear();  binlog.clear();
        diskData.put("Alice", 1000);
        diskData.put("Bob",   500);
        syncBinlog = 1; flushRedoPolicy = 1;
        System.out.println("─────────────────────────────────────");
        System.out.println("重置：Alice=1000, Bob=500");
        System.out.println("配置：sync_binlog=1, innodb_flush_log_at_trx_commit=1（双1）");
        System.out.println("─────────────────────────────────────");
    }

    // ==================== 场景演示 ====================

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║        MySQL 两阶段提交（2PC）完整演示                      ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");

        reset();
        System.out.println("\n══════ 场景1：正常转账，完整两阶段提交流程 ══════");
        scenario1_normalCommit();

        reset();
        System.out.println("\n══════ 场景2：崩溃点1 —— Redo Log prepare 后、Binlog 前崩溃 ══════");
        scenario2_crashAfterPrepareBeforeBinlog();

        reset();
        System.out.println("\n══════ 场景3：崩溃点2 —— Binlog 后、Redo Log commit 前崩溃 ══════");
        scenario3_crashAfterBinlogBeforeCommit();

        reset();
        System.out.println("\n══════ 场景4：sync_binlog=0 的风险 ══════");
        scenario4_syncBinlog0Risk();

        reset();
        System.out.println("\n══════ 场景5：组提交（Group Commit）原理说明 ══════");
        scenario5_groupCommit();
    }

    /** 场景1：正常转账，演示完整两阶段提交 */
    static void scenario1_normalCommit() {
        long xid = xidCounter++;
        System.out.println("\n[BEGIN] Alice 转 200 元给 Bob，xid=" + xid);

        System.out.println("\n  ── 事务执行阶段（数据修改）──");
        update(xid, "Alice", 800);
        update(xid, "Bob", 700);

        twoPhaseCommit(xid, "Alice转账给Bob");

        System.out.println("\n最终磁盘数据: " + diskData);
        System.out.println("✓ 两阶段提交保证 Redo Log 和 Binlog 完全一致");
        System.out.println("✓ 主库和从库（通过 Binlog 复制）数据一致");
    }

    /**
     * 场景2：崩溃点1 —— Redo Log prepare 后，Binlog 写入前崩溃
     * 重启后判断：Redo Log 有 prepare，Binlog 无 XID → 回滚
     * 结果：主从都没有这次变更，一致 ✓
     */
    static void scenario2_crashAfterPrepareBeforeBinlog() {
        long xid = xidCounter++;
        System.out.println("\n[BEGIN] Alice 转 200 元给 Bob，xid=" + xid);

        update(xid, "Alice", 800);
        update(xid, "Bob", 700);

        System.out.println("\n  ▶ Phase 1 - Prepare（写 Redo Log prepare，fsync）");
        RedoLogEntry prepareEntry = new RedoLogEntry(xid, RedoLogEntry.State.PREPARE);
        redoLog.add(prepareEntry);
        for (RedoLogEntry e : redoLog) { if (e.xid == xid) e.flushed = true; }
        System.out.println("  " + prepareEntry + "  Redo Log fsync ✓");

        System.out.println("\n  ★ [CRASH!] 崩溃点1：Redo Log prepare 已落盘，Binlog 还没写！");
        System.out.println("  Buffer Pool 丢失，磁盘数据仍为 Alice=1000, Bob=500");
        // Binlog 里没有 XID_COMMIT，因为还没写到那一步
        bufferPool.clear();
        System.out.println("  Redo Log 中：有 PREPARE（xid=" + xid + "），无 COMMIT");
        System.out.println("  Binlog  中：无 XID_COMMIT（xid=" + xid + "）");

        crashRecovery("崩溃点1：prepare后Binlog前");
        System.out.println("✓ 回滚后：主库 Alice=1000（没有转账），从库也没有这次变更（Binlog无记录）");
        System.out.println("✓ 主从一致，数据安全");
    }

    /**
     * 场景3：崩溃点2 —— Binlog 写入后，Redo Log commit 前崩溃
     * 重启后判断：Redo Log 有 prepare，Binlog 有 XID → 补提交
     * 结果：主从都有这次变更，一致 ✓
     */
    static void scenario3_crashAfterBinlogBeforeCommit() {
        long xid = xidCounter++;
        System.out.println("\n[BEGIN] Alice 转 200 元给 Bob，xid=" + xid);

        update(xid, "Alice", 800);
        update(xid, "Bob", 700);

        System.out.println("\n  ▶ Phase 1 - Prepare（写 Redo Log prepare，fsync）");
        RedoLogEntry prepareEntry = new RedoLogEntry(xid, RedoLogEntry.State.PREPARE);
        redoLog.add(prepareEntry);
        for (RedoLogEntry e : redoLog) { if (e.xid == xid) e.flushed = true; }
        System.out.println("  " + prepareEntry + "  Redo Log fsync ✓");

        System.out.println("\n  ▶ Phase 2a - 写 Binlog，fsync");
        BinlogEntry xidEvt = BinlogEntry.xidCommit(xid);
        binlog.add(xidEvt);
        for (BinlogEntry e : binlog) { if (e.xid == xid) e.flushed = true; }
        System.out.println("  " + xidEvt + "  Binlog fsync ✓");

        System.out.println("\n  ★ [CRASH!] 崩溃点2：Binlog 已落盘，Redo Log commit 还没写！");
        System.out.println("  Buffer Pool 丢失，磁盘数据仍为 Alice=1000, Bob=500");
        bufferPool.clear();
        diskData.put("Alice", 1000);
        diskData.put("Bob", 500);
        System.out.println("  Redo Log 中：有 PREPARE（xid=" + xid + "），无 COMMIT");
        System.out.println("  Binlog  中：有 XID_COMMIT（xid=" + xid + "）← 这是关键！");

        crashRecovery("崩溃点2：Binlog后commit前");
        System.out.println("✓ 补提交后：主库 Alice=800, Bob=700（转账成功）");
        System.out.println("✓ 从库已通过 Binlog 复制了这次变更");
        System.out.println("✓ 主从一致，数据安全");
    }

    /**
     * 场景4：sync_binlog=0 的风险
     * Binlog 未立即 fsync，OS 崩溃时 Binlog 丢失
     * 导致：主库 Redo Log 有 prepare，Binlog 无 XID → 回滚
     *       但从库已经收到并执行了 Binlog → 主从不一致！
     *
     * 注：真实场景中从库收 Binlog 是通过 binlog dump 实时推送，
     *     如果 Binlog 写入 OS cache 后崩溃，从库可能已经收到，也可能没收到
     */
    static void scenario4_syncBinlog0Risk() {
        syncBinlog = 0;
        System.out.println("配置切换：sync_binlog=0（Binlog 不立即 fsync，OS 自动刷）");
        System.out.println("风险：Binlog 在 OS cache，若 OS 崩溃则丢失");

        long xid = xidCounter++;
        System.out.println("\n[BEGIN] Alice 转 200 元给 Bob，xid=" + xid);
        update(xid, "Alice", 800);
        update(xid, "Bob", 700);

        System.out.println("\n  ▶ Phase 1: Redo Log prepare，fsync ✓");
        RedoLogEntry prepareEntry = new RedoLogEntry(xid, RedoLogEntry.State.PREPARE);
        redoLog.add(prepareEntry);
        for (RedoLogEntry e : redoLog) { if (e.xid == xid) e.flushed = true; }
        System.out.println("  " + prepareEntry);

        System.out.println("\n  ▶ Phase 2a: 写 Binlog XID_COMMIT，但 sync_binlog=0，未立即 fsync");
        BinlogEntry xidEvt = BinlogEntry.xidCommit(xid);
        binlog.add(xidEvt);
        // sync_binlog=0，不 fsync，flushed 保持 false
        System.out.println("  " + xidEvt);
        System.out.println("  Binlog 在 OS page cache，尚未落盘");

        System.out.println("\n  ★ [CRASH!] OS 崩溃！OS page cache 丢失，Binlog 未落盘");
        bufferPool.clear();
        diskData.put("Alice", 1000);
        diskData.put("Bob", 500);
        System.out.println("  Redo Log：有 PREPARE，有 DATA（已落盘）");
        System.out.println("  Binlog ：XID_COMMIT 在 OS cache 里，随 OS 崩溃丢失了");

        crashRecovery("sync_binlog=0，OS崩溃");
        System.out.println("✗ 主库回滚：Alice=1000, Bob=500（没有转账记录）");
        System.out.println("  如果从库恰好在崩溃前收到了 Binlog → 从库 Alice=800, Bob=700");
        System.out.println("  主从数据不一致！这是 sync_binlog=0 的核心风险。");
        System.out.println("  生产环境金融场景必须使用 sync_binlog=1 + innodb_flush_log_at_trx_commit=1（双1配置）");
        syncBinlog = 1;
    }

    /**
     * 场景5：组提交（Group Commit）原理说明
     *
     * 问题：双1配置下，每个事务需要 2 次 fsync（prepare + binlog），
     *       fsync 是随机 IO，高并发下 IOPS 成瓶颈
     *
     * 解决：多个并发事务的 Binlog 合并成一次 fsync（Binlog Group Commit）
     *       MySQL 5.6+ 引入三阶段 Binlog 组提交：
     *         flush stage：各事务把 Binlog 写到 OS cache，leader 负责统一 fsync
     *         sync  stage：leader 对多个事务的 Binlog 做一次 fsync
     *         commit stage：依次提交 InnoDB（写 Redo Log commit）
     *       N 个事务只需 1 次 Binlog fsync，大幅提升吞吐量
     */
    static void scenario5_groupCommit() {
        System.out.println("  组提交是高并发下 2PC 的性能优化，不改变正确性，只合并 fsync 次数");
        System.out.println();

        long xid1 = xidCounter++;
        long xid2 = xidCounter++;
        long xid3 = xidCounter++;
        System.out.println("  假设 3 个并发事务同时到达提交阶段：");
        System.out.printf("    tx1(xid=%d): Alice 转 100 给 Bob%n", xid1);
        System.out.printf("    tx2(xid=%d): Bob  转 50  给 Charlie%n", xid2);
        System.out.printf("    tx3(xid=%d): Alice 转 200 给 Charlie%n", xid3);

        System.out.println("\n  ── 无组提交（每个事务各自 fsync）──");
        System.out.println("    tx1: Redo prepare fsync → Binlog fsync → Redo commit fsync  共 3 次 fsync");
        System.out.println("    tx2: Redo prepare fsync → Binlog fsync → Redo commit fsync  共 3 次 fsync");
        System.out.println("    tx3: Redo prepare fsync → Binlog fsync → Redo commit fsync  共 3 次 fsync");
        System.out.println("    总计：9 次 fsync（3 个事务 × 3 次）");

        System.out.println("\n  ── 有组提交（Binlog Group Commit，MySQL 5.6+）──");
        System.out.println("  [flush stage]  tx1, tx2, tx3 各自把 Binlog 写到 OS cache");
        System.out.println("  [sync  stage]  leader(tx1) 对 3 个事务的 Binlog 做 1 次 fsync");
        System.out.println("                 tx2, tx3 等待这次 fsync 完成即可（无需自己 fsync）");
        System.out.println("  [commit stage] tx1, tx2, tx3 依次写 Redo Log commit（InnoDB 层）");
        System.out.println("  总计：3（prepare） + 1（Binlog 组 fsync） + 3（commit）= 7 次 fsync");
        System.out.println("  并发事务越多，组提交收益越大（极端情况可将 Binlog fsync 从 N 次降到 1 次）");

        System.out.println("\n  相关参数：");
        System.out.println("    binlog_group_commit_sync_delay=N  ：等待 N 微秒再组提交（积累更多事务，fsync 更少）");
        System.out.println("    binlog_group_commit_sync_no_delay_count=M：积累 M 个事务就不等了，直接提交");
        System.out.println("    → 两者都是在 fsync 次数和延迟之间取平衡");

        System.out.println("\n  ✓ 组提交不改变两阶段提交的正确性");
        System.out.println("  ✓ 组提交大幅提高双1配置下的写入吞吐量，是高并发 MySQL 的标配优化");
    }
}

