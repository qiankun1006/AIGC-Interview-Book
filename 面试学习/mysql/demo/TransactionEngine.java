import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * ============================================================
 * TransactionEngine —— InnoDB 核心事务引擎
 * ============================================================
 *
 * 这个类是整个 Demo 的"发动机"，模拟了 InnoDB 最核心的几件事：
 *   1. 维护内存状态（Buffer Pool / Log Buffer / Undo Log / LSN 等）
 *   2. 执行 DML：read / readWithLock / update
 *   3. 事务控制：commit（两阶段提交） / rollback（沿 Undo 链回滚）
 *   4. 崩溃恢复：crashRecovery（Redo Phase → 2PC Check → Undo Phase）
 *
 * ★ 面试怎么说（InnoDB 内存结构一句话总结）：
 *   "InnoDB 的内存主要有三块：
 *    Buffer Pool（缓存数据页，减少磁盘 IO）、
 *    Log Buffer（缓存 Redo Log，批量写盘提高性能）、
 *    以及 Undo Page（在 Buffer Pool 里，记录旧值用于回滚和 MVCC）。"
 *
 * 内存状态和真实 InnoDB 的对应关系：
 * <pre>
 *  bufferPool       → Buffer Pool（innodb_buffer_pool_size，默认 128MB，16KB 页）
 *  undoLog          → Buffer Pool 中的 Undo Page 内存副本
 *  undoPageDirty    → Undo Page 是否是脏页（在 flush list 中等待刷盘）
 *  redoLogBuffer[]  → Log Buffer（innodb_log_buffer_size，默认 16MB）
 *  logBufPos        → Log Buffer 下一个写入位置（真实叫 buf_free 指针）
 *  currentLsn       → 当前最新 LSN（全局单调递增，每写一条日志 +1）
 *  flushedToDisklsn → 已 fsync 到 ib_logfile0 的最大 LSN
 *  checkpointLsn    → 已刷盘数据页的最大 LSN（这之前的日志文件空间可复用）
 *  redoWriteHead    → ib_logfile0 环形写头（下一个写入 block 的序号）
 *  flushPolicy      → innodb_flush_log_at_trx_commit（0/1/2）
 *  syncBinlog       → sync_binlog（0/1/N）
 *  binlogCache      → Binlog Cache（每线程私有，COMMIT 时才 fsync 到文件）
 * </pre>
 */
public class TransactionEngine extends MysqlDemoBase {

    // ==================== Buffer Pool ====================

    /**
     * Buffer Pool —— 数据库最重要的内存缓存
     *
     * ★ 面试怎么说：
     *   "Buffer Pool 是 InnoDB 最重要的内存结构，默认 128MB，可以配到物理内存的 70%-80%。
     *    所有数据读写都先经过 Buffer Pool，命中就直接用（零 IO），未命中才从磁盘加载。
     *    Buffer Pool 里有三条链：
     *    · LRU 链：决定哪些页可以被淘汰（young 区热页 5 : old 区冷页 3）
     *    · Free 链：空闲页链表，有空位才能加载新页
     *    · Flush 链：脏页链表，Page Cleaner 线程按需把脏页写回磁盘"
     */
    static Map<String, Integer> bufferPool = new HashMap<>();

    // ==================== Undo Log 内存结构 ====================

    /**
     * Undo Log 内存副本 —— Buffer Pool 中 Undo Page 的模拟
     *
     * ★ 面试怎么说（Undo Log 完整落盘路径，面试高频）：
     *   "很多人以为 Undo Log 直接写文件，其实不是。
     *    真正的路径是：
     *    ① 先把 Undo Record 写到 Buffer Pool 里的 Undo Page（内存脏页），
     *    ② 同时向 Log Buffer 写一条'Redo for Undo'（MLOG_UNDO_INSERT 类型），
     *       告诉 InnoDB'我改了 Undo Page，崩溃后要重放出来'，
     *    ③ 提交时 Redo Log fsync，Undo 变更就有了 WAL 保证，
     *    ④ Page Cleaner 之后异步把 Undo Page 刷入 undo_001.ibu 文件。
     *    关键点：Undo 的持久性不依赖 undo_001.ibu 有没有落盘，
     *    而是依赖'Redo for Undo'有没有 fsync。
     *    即使崩溃时 undo_001.ibu 还没写，重启后 Redo Phase 也能通过重放 Redo for Undo
     *    把 Undo Page 重建出来，然后 Undo Phase 再用它回滚。"
     */
    static List<UndoLogRecord> undoLog = new ArrayList<>();

    /**
     * Undo Page 脏页标志（模拟 Buffer Pool flush list 中的 Undo Page 条目）
     *
     * 为 true 表示 Undo Page 被修改过，等待 Page Cleaner 异步刷盘。
     * 刷盘的前提：Redo for Undo 已 fsync（WAL 约束同样适用于 Undo Page）。
     */
    static boolean undoPageDirty = false;

    // ==================== Log Buffer（内存 Redo Log 缓冲区）====================

    /**
     * Log Buffer 容量（Demo 用 64 个槽，真实默认 16MB）
     *
     * Log Buffer 什么时候会触发 flush（写入 ib_logfile0）？
     *   ① 事务 COMMIT（innodb_flush_log_at_trx_commit=1 时还 fsync）
     *   ② Log Buffer 占用超过 1/2（防止写满）
     *   ③ 后台线程每秒定时刷（flushPolicy=0/2 时的保底机制）
     *   ④ 脏页要落盘前必须先 flush Log Buffer（WAL 约束）
     */
    static final int REDO_LOG_BUFFER_CAPACITY = 64;

    /** Log Buffer 数组（模拟 buf_free 之前的连续内存区域） */
    static RedoLogRecord[] redoLogBuffer = new RedoLogRecord[REDO_LOG_BUFFER_CAPACITY];

    /** Log Buffer 下一个写入位置（真实叫 log_sys->buf_free 指针） */
    static int logBufPos = 0;

    // ==================== LSN 相关全局变量 ====================

    /**
     * currentLsn —— 当前已写入 Log Buffer 的最新 LSN
     *
     * ★ 面试知识点：
     *   LSN（Log Sequence Number）是全局单调递增的序号，
     *   代表"Redo Log 已经写到哪里了"。
     *   三个 LSN 始终满足：checkpointLsn ≤ flushedToDisklsn ≤ currentLsn
     */
    static long currentLsn = 0;

    /**
     * flushedToDisklsn —— 已 fsync 到 ib_logfile0 的最大 LSN
     *
     * 这个值 ≤ currentLsn，超过这个值的日志还在 Log Buffer 里，
     * 机器崩溃会丢失。
     * Page Cleaner 刷脏页之前要检查：page.newest_modification ≤ flushedToDisklsn，
     * 确保对应的 Redo 已落盘，才允许刷脏页（WAL 约束）。
     */
    static long flushedToDisklsn = 0;

    /**
     * checkpointLsn —— 已刷盘数据页对应的最大 LSN（"安全线"）
     *
     * ★ 面试怎么说：
     *   "checkpointLsn 之前的 ib_logfile0 空间可以被新日志覆盖复用。
     *    如果脏页积压、checkpoint 推进太慢，ib_logfile0 写满就会阻塞写入，
     *    这就是 MySQL 的'checkpoint age 告警'，需要调大 innodb_log_file_size 或优化写入。"
     *
     * 真实：写在 ib_logfile 文件头，两个 checkpoint 页交替写，带 CRC32 保证原子性。
     */
    static long checkpointLsn = 0;

    /** ib_logfile0 环形写的当前写头（下一个 block 的序号，模拟 write_head 指针） */
    static int redoWriteHead = 0;

    // ==================== 日志策略参数 ====================

    /**
     * innodb_flush_log_at_trx_commit —— 控制 Redo Log 的落盘策略
     *
     * ★ 面试必背：
     *   0 = 后台线程每秒 write + fsync：MySQL 崩溃或机器断电都可能丢最近 1 秒的数据
     *   1 = 每次 COMMIT 都 write + fsync（默认值）：最安全，金融/支付标配
     *   2 = 每次 COMMIT write 到 OS cache，后台线程每秒 fsync：
     *         MySQL 进程崩溃不丢（OS cache 还在），机器断电丢最近 1 秒
     *
     * ★ 面试口诀：
     *   "=1 最安全，每次提交都落盘；=2 折中，进程挂不丢，断电丢；=0 最快，任何崩溃都可能丢"
     */
    static int flushPolicy = 1;

    /**
     * sync_binlog —— 控制 Binlog 的 fsync 策略
     *
     * ★ 面试必背：
     *   0 = OS 自动决定何时刷（最快，OS 崩溃时 Binlog 可能丢，导致主从不一致）
     *   1 = 每次事务提交都 fsync（最安全，与 innodb_flush_log_at_trx_commit=1 合称"双1配置"）
     *   N = 每 N 次提交 fsync 一次（折中，N 次内崩溃最多丢 N 个事务的 Binlog）
     *
     * ★ 面试口诀：
     *   "双1配置（sync_binlog=1 + innodb_flush_log_at_trx_commit=1）= 最安全，生产金融标配"
     *
     * MySQL 5.7.7 之后默认值从 0 改为 1（更安全的默认配置）。
     */
    static int syncBinlog = 1;

    // ==================== Binlog Cache ====================

    /**
     * Binlog Cache —— 事务提交前暂存 Binlog 事件的内存缓冲区
     *
     * ★ 面试知识点：
     *   "Binlog Cache 是每个线程私有的，binlog_cache_size 默认 32KB，
     *    超出后溢写到临时文件。
     *    事务执行期间，所有行变更先写到 Binlog Cache（内存，不落盘）；
     *    COMMIT 时，一次性把整个 Cache（BEGIN + ROW_CHANGE×N + XID_COMMIT）
     *    write → fsync 到 binlog 文件。
     *    这样设计保证了 Binlog 里不会有'写到一半'的事务。"
     */
    static List<BinlogEntry> binlogCache = new ArrayList<>();

    // ==================== 事务管理 ====================

    /**
     * 事务 ID 计数器（全局自增，跨场景不重置，保证 txId 全局唯一）
     * 真实：trx_sys->max_trx_id，6 字节，持久化到系统表空间，MySQL 重启后继续累加
     */
    static long txIdCounter = 1;

    /** 每个事务的 undo_no 计数器（事务内操作序号，决定回滚时的逆序） */
    static Map<Long, Integer> txUndoNoCounter = new HashMap<>();

    // ==================== Log Buffer 核心操作 ====================

    /**
     * 向 Log Buffer 追加一条 Redo Log Record（只写内存，不落盘）
     *
     * Log Buffer 写满时强制 flush 到 ib_logfile0（真实会唤醒后台 log flusher 线程）。
     *
     * ★ 面试知识点：
     *   "写 Log Buffer 是在内存里操作，非常快。
     *    批量积累后再统一 fsync 到磁盘，是 InnoDB Group Commit 优化的基础。
     *    MySQL 8.0 中，Log Buffer 写入改用无锁 CAS，并发性能更好。"
     */
    static void writeToRedoLogBuffer(RedoLogRecord record) {
        if (logBufPos >= REDO_LOG_BUFFER_CAPACITY) {
            System.out.println("  [Log Buffer] 已满（" + REDO_LOG_BUFFER_CAPACITY
                    + " 条），强制 flush 到 ib_logfile0（真实：唤醒后台 log flusher 线程）");
            flushLogBufferToDisk();
        }
        redoLogBuffer[logBufPos++] = record;
    }

    /**
     * Log Buffer → ib_logfile0（环形磁盘文件）flush + fsync
     *
     * ★ 面试知识点（write 和 fsync 的区别，必须说清楚）：
     *   "write（pwrite）：把数据从进程内存写到 OS 的 page cache，速度快，但 OS 崩溃会丢。
     *    fsync（fdatasync）：把 OS page cache 强制刷到物理磁盘，慢但安全。
     *    innodb_flush_log_at_trx_commit=1 就是每次提交都 write + fsync，
     *    所以它最慢但最安全（毕竟每次要等磁盘 IO 完成）。"
     *
     * ★ 面试知识点（环形写，面试重点）：
     *   "ib_logfile0 是固定大小的环形文件，写满了从头覆盖。
     *    覆盖的前提：被覆盖位置的 LSN ≤ checkpointLsn（那部分数据页已安全落盘）。
     *    如果脏页写得慢，checkpoint 跟不上，环形文件就写满了，写日志会阻塞，
     *    这就是'checkpoint age 告警'，需要调大 ib_logfile_size 或优化 IO。"
     */
    static void flushLogBufferToDisk() {
        List<RedoLogRecord> toFlush = new ArrayList<>();
        for (int i = 0; i < logBufPos; i++) {
            if (redoLogBuffer[i] != null) {
                redoLogBuffer[i].isFlushed = true;
                toFlush.add(redoLogBuffer[i]);
            }
        }
        if (!toFlush.isEmpty()) {
            DiskStore.writeRedoBlocksToDisk(toFlush, redoWriteHead);
            redoWriteHead += toFlush.size();
            DiskStore.saveRedoHeader(checkpointLsn, redoWriteHead); // 持久化 write_head
        }
        flushedToDisklsn = currentLsn;
        Arrays.fill(redoLogBuffer, 0, logBufPos, null);
        logBufPos = 0;
    }

    // ==================== Undo Page 操作 ====================

    /**
     * 向 Undo Page 写入一条 Undo Record，同时写 Redo for Undo 到 Log Buffer
     *
     * ★ 面试知识点（这是高频考点，一定要说清楚"双写"机制）：
     *   "写 Undo Log 不是直接写文件，步骤是：
     *    ① 把 Undo Record 写到 Buffer Pool 里的 Undo Page（内存脏页）
     *    ② 同时向 Log Buffer 追加一条 Redo for Undo（MLOG_UNDO_INSERT 类型）
     *       ——这条'Redo for Undo'就是 Undo 持久性的保障，
     *       只要它 fsync 了，即使 Undo Page 还没写盘，
     *       崩溃重启后也能通过 Redo Phase 把 Undo Page 还原出来。
     *    ③ Undo Page 脏页异步由 Page Cleaner 刷入 undo_001.ibu 文件（可以很晚）"
     *
     * 简单说：Undo Log 的落盘靠的是对应的 Redo for Undo，不是 Undo Page 本身直接 fsync。
     */
    static void writeUndoToUndoPage(UndoLogRecord undoRecord) {
        // ① 把 Undo Record 写到内存 Undo Page（修改脏页，不落盘）
        undoLog.add(undoRecord);

        // ② 标记 Undo Page 为脏页（进入 flush list，等待 Page Cleaner 异步刷盘）
        undoPageDirty = true;
        System.out.println("  [Undo Page]  写入内存 Undo Page: " + undoRecord);
        System.out.println("  [Undo Page]  → 标记为脏页(undoPageDirty=true)，加入 flush list，等待 Page Cleaner 异步刷盘");

        // ③ 向 Log Buffer 追加 Redo for Undo（MLOG_UNDO_INSERT）
        //    WAL 约束：Undo Page 落盘前，这条 Redo for Undo 必须先 fsync
        currentLsn++;
        RedoLogRecord redoForUndo = RedoLogRecord.redoForUndo(currentLsn, undoRecord.txId, undoRecord);
        writeToRedoLogBuffer(redoForUndo);
        System.out.println("  [Log Buffer]  写 Redo for Undo: " + redoForUndo);
        System.out.println("  [Log Buffer]  → MLOG_UNDO_INSERT：保证崩溃后 Redo Phase 能重建 Undo Page");
    }

    /**
     * 模拟 Page Cleaner 将脏 Undo Page 异步刷入 undo_001.ibu（COMMIT 后某时刻）
     *
     * ★ 面试知识点（为什么 Undo Page 可以晚点落盘）：
     *   "Undo Page 的持久性不依赖它本身有没有写盘，
     *    而是依赖对应的 Redo for Undo 有没有 fsync。
     *    只要 Redo for Undo 落盘了，即使机器崩溃时 undo_001.ibu 还是旧数据，
     *    重启后 Redo Phase 会把 Redo for Undo 重放，把 Undo Page 的内容还原出来，
     *    Undo Phase 再用它回滚未提交事务，数据一致性完全可以保证。"
     *
     * 刷盘的前提（WAL 约束）：Redo for Undo 对应的 LSN ≤ flushedToDisklsn。
     */
    static void simulateUndoPageCleaner() {
        if (!undoPageDirty) return; // Undo Page 是干净页，无需刷盘

        // WAL 约束检查：确认 Redo for Undo 已 fsync，才允许 Undo Page 落盘
        if (flushedToDisklsn < currentLsn) {
            System.out.println("  [Page Cleaner] Undo Page 脏页：Redo for Undo 尚未 fsync，暂缓刷盘（等待 WAL 满足）");
            return;
        }

        System.out.println("  [Page Cleaner] Undo Page 脏页：Redo for Undo 已 fsync，开始刷盘 -> undo_001.ibu");
        try (PrintWriter pw = new PrintWriter(new FileWriter(DiskStore.UNDO_FILE, false))) {
            for (UndoLogRecord r : undoLog) {
                pw.println(r.serialize());
            }
            pw.flush();
        } catch (IOException e) {
            throw new RuntimeException("Page Cleaner 刷 Undo Page 失败", e);
        }
        undoPageDirty = false;
        System.out.println("  [Page Cleaner] Undo Page 落盘完成，undoPageDirty=false（Undo Page 从 flush list 移出）");
    }

    // ==================== Buffer Pool 读 ====================

    /**
     * 读取数据（优先走 Buffer Pool，缺页时从磁盘加载）
     *
     * ★ 面试知识点（快照读 vs 当前读）：
     *   "普通 SELECT 是'快照读'，通过 MVCC 读历史版本，不加任何行锁。
     *    SELECT ... FOR UPDATE / UPDATE / DELETE 是'当前读'，
     *    读的是最新版本，并且要加行锁（Record X Lock）防止并发修改。
     *    这个方法被 update() 调用，走的是当前读路径（加锁在 update() 里统一处理）。"
     *
     * Buffer Pool 缺页时（page fault）：
     *   从 data.ibd 把整个 16KB 数据页加载到 Buffer Pool，
     *   后续读同一行就不用再走磁盘了（这就是 Buffer Pool 的命中率的意义）。
     */
    static int read(String key) {
        if (bufferPool.containsKey(key)) {
            return bufferPool.get(key); // 命中，零 IO
        }
        Map<String, Integer> diskData = DiskStore.loadDataFromDisk();
        int val = diskData.getOrDefault(key, 0);
        bufferPool.put(key, val);
        System.out.println("  [Buffer Pool] page fault: " + key
                + " 不在内存，从 data.ibd 加载 16KB 数据页到 Buffer Pool，当前值=" + val);
        return val;
    }

    /**
     * 带锁的显式读（模拟 SELECT ... FOR SHARE / FOR UPDATE）
     *
     * ★ 面试知识点（加锁顺序，面试常问）：
     *   "InnoDB 加锁的顺序是固定的，先加表级意向锁，再加行级锁：
     *    FOR SHARE → 先加 IS（表）→ 再加 Record S Lock（行）
     *    FOR UPDATE → 先加 IX（表）→ 再加 Record X Lock（行）+ Gap Lock（间隙，RR 级别）"
     *
     * @param txId      当前事务 ID
     * @param key       查询的主键（账户名，模拟 WHERE name = key）
     * @param forUpdate true = FOR UPDATE（加 X 锁），false = FOR SHARE（加 S 锁）
     */
    static int readWithLock(long txId, String key, boolean forUpdate) {
        String tableName = "accounts";
        if (forUpdate) {
            // SELECT ... FOR UPDATE：先加 IX 意向锁，再加 Next-Key Lock（Record X + Gap）
            System.out.println("  [LockMgr]    SELECT ... FOR UPDATE: 当前读，需加 IX 意向锁 + Next-Key Lock");
            LockManager.acquireLock(txId, LockEntry.LockType.IX,       tableName,             true);
            LockManager.acquireLock(txId, LockEntry.LockType.RECORD_X, key,                   false);
            LockManager.acquireLock(txId, LockEntry.LockType.GAP,      "(-∞, " + key + ")",   false);
            System.out.println("  [LockMgr]    → Next-Key Lock = Record X(" + key + ") + Gap(-∞,"
                    + key + ")，RR 级别防幻读");
        } else {
            // SELECT ... FOR SHARE：先加 IS 意向锁，再加 Record S Lock
            System.out.println("  [LockMgr]    SELECT ... FOR SHARE: 当前读，需加 IS 意向锁 + Record S Lock");
            LockManager.acquireLock(txId, LockEntry.LockType.IS,       tableName,  true);
            LockManager.acquireLock(txId, LockEntry.LockType.RECORD_S, key,        false);
            System.out.println("  [LockMgr]    → Record S Lock 允许其他事务并发 FOR SHARE，但阻塞任何 FOR UPDATE");
        }
        return read(key);
    }

    // ==================== DML 操作 ====================

    /**
     * UPDATE 操作完整流程（严格按真实 InnoDB 执行顺序）
     *
     * ★ 面试必背（UPDATE 的六步流程，这是 InnoDB 最核心的执行流程）：
     *   第0步：加锁（IX 意向锁 + Record X Lock + Gap Lock，必须在修改前持有）
     *   第1步：从 Buffer Pool 读取当前值（当前读，缺页从磁盘加载）
     *   第2步：写 Undo Log（先记旧值！保证回滚时有据可查）
     *           → 同时写 Redo for Undo 到 Log Buffer（Undo 的持久性保障）
     *   第3步：修改 Buffer Pool 脏页（改内存，不落盘）
     *   第4步：写 Redo Log Buffer（记录"把哪页改成了什么"，也是内存，不落盘）
     *   第5步：写 Binlog Cache（也是内存，COMMIT 时才 fsync 到文件）
     *
     * 为什么先写 Undo 再改数据页？
     *   保证：如果第3步改了内存但还没 COMMIT 就崩溃，Undo Log 里一定有旧值，可以正确回滚。
     *
     * 为什么 Undo Log 也要写 Redo（Redo for Undo）？
     *   Undo Log 存在 Undo Page 上，Undo Page 是内存脏页，改 Undo Page 也是物理修改，
     *   同样需要 WAL 保护。"改 Undo Page 产生 Redo"就是为了保证崩溃后 Undo Page 能重建。
     */
    static void update(long txId, String key, int newValue) {

        // ── Step 0：加锁 ──────────────────────────────────────────────────────────
        //
        // ★ 面试知识点（UPDATE 加锁顺序）：
        //   ① 表上加 IX（Intention Exclusive）意向锁
        //      → 告诉 LOCK TABLE：这张表有行要被独占写，别给我加表 S/X 锁
        //      → IX 之间互相兼容，多个事务可同时持有（它们锁的是不同行）
        //   ② 聚簇索引记录上加 Record X Lock（行级排他锁）
        //      → 阻塞其他事务对该行的任何加锁读（FOR SHARE/FOR UPDATE）和写
        //      → 但不影响快照读（SELECT 不带锁，走 MVCC 不需要行锁）
        //   ③ 该记录前面的间隙上加 Gap Lock（RR 隔离级别，防幻读）
        //      → 阻止其他事务在 (-∞, key) 区间 INSERT 新行
        //      → RC 级别不加 Gap Lock（并发更好但有幻读风险）
        //   ② + ③ 合称 Next-Key Lock，是 RR 级别 UPDATE/DELETE 的默认策略
        String tableName = "accounts";
        boolean alreadyLocked = LockManager.lockManager.stream().anyMatch(
                l -> l.txId == txId && l.type == LockEntry.LockType.RECORD_X && key.equals(l.resource));
        if (!alreadyLocked) {
            System.out.println("  [LockMgr]    UPDATE 加锁：IX 意向锁 + Next-Key Lock on \"" + key + "\"");
            LockManager.acquireLock(txId, LockEntry.LockType.IX,       tableName,             true);
            LockManager.acquireLock(txId, LockEntry.LockType.RECORD_X, key,                   false);
            LockManager.acquireLock(txId, LockEntry.LockType.GAP,      "(-∞, " + key + ")",   false);
            System.out.println("  [LockMgr]    → IX 与其他事务的 IX 兼容（行锁互不阻塞），但阻塞 LOCK TABLE WRITE");
            System.out.println("  [LockMgr]    → Record X 阻塞其他事务对 " + key + " 的任何加锁读/写（快照读不受影响）");
            System.out.println("  [LockMgr]    → Gap Lock (-∞," + key + ") 防止其他事务在此间隙 INSERT，杜绝幻读（RR 级别）");
        } else {
            System.out.println("  [LockMgr]    UPDATE: tx=" + txId + " 已持有 " + key
                    + " 的 Record X Lock，直接复用（同一事务对同一行多次 UPDATE 不重复加锁）");
        }

        // Step 1：从 Buffer Pool 读取当前值（当前读：读最新版本）
        int oldValue = read(key);

        // Step 2：写 Undo Log（先于数据修改，记录旧值，同时写 Redo for Undo）
        //
        // undoNo（逻辑编号）设计说明：
        //   从 100 开始，每步 +10（100, 110, 120...）
        //   回滚时按 undoNo 降序遍历，确保逆序撤销（先撤最后一步）
        //   起点 100、步长 10 是为了和 rollPointer（从 1000 起）在数值上明显区分
        //
        // rollPointer（版本链指针）设计说明：
        //   指向"本事务上一条 Undo Record"的位置（-1 表示这是第一条，链头）
        //   真实：7 字节，编码了 Rollback Seg ID + Undo Page 页号 + 页内偏移
        //   MVCC 快照读就是沿这条链一路向前，找到满足 ReadView 的历史版本
        int rawCount = txUndoNoCounter.getOrDefault(txId, 0);
        int undoNo = 100 + rawCount * 10;
        long rollPointer = rawCount == 0 ? -1L : 1000L + (undoLog.size() - 1);
        UndoLogRecord undoRecord = new UndoLogRecord(txId, undoNo, key, oldValue,
                UndoLogRecord.UndoType.UPDATE, rollPointer);
        txUndoNoCounter.put(txId, rawCount + 1);

        writeUndoToUndoPage(undoRecord);
        System.out.println("  [Undo Page]  作用1: ROLLBACK 时用 oldVal=" + oldValue + " 把 " + key + " 恢复原值");
        System.out.println("  [Undo Page]  作用2: MVCC 快照读沿 roll_pointer 链向前找历史版本（不加锁）");
        System.out.println("  [Undo Page]  持久性: 依赖上面的 Redo for Undo 落盘，而非 Undo Page 直接 fsync");

        // Step 3：修改 Buffer Pool 脏页（只改内存，不落盘，靠 Redo Log 保障持久性）
        bufferPool.put(key, newValue);
        long pageLsn = currentLsn + 1;
        System.out.println("  [Buffer Pool] " + key + ": " + oldValue + " -> " + newValue
                + " (内存脏页，page.newest_modification=" + pageLsn
                + "，WAL 要求：此页落盘前 lsn<=" + pageLsn + " 的 Redo 必须先 fsync)");

        // Step 4：向 Log Buffer 追加 Redo Log（物理日志，只在内存，还未 fsync）
        currentLsn++;
        RedoLogRecord redoRecord = new RedoLogRecord(currentLsn, txId, key, newValue);
        writeToRedoLogBuffer(redoRecord);
        System.out.println("  [Log Buffer]  追加 " + redoRecord);
        System.out.println("  [Log Buffer]  仅在内存 Log Buffer 中，尚未 fsync 到 ib_logfile0");

        // Step 5：向 Binlog Cache 追加事件（Server 层逻辑日志，只在内存，COMMIT 时才落盘）
        //
        // 事务的第一条 DML 需要先写 BEGIN 事件，后续 DML 直接追加 ROW_CHANGE 事件。
        // COMMIT 时，整个 Cache（BEGIN + ROW_CHANGE×N + XID_COMMIT）一次性 fsync 到文件。
        boolean isFirstDml = binlogCache.isEmpty();
        if (isFirstDml) {
            binlogCache.add(BinlogEntry.begin(txId));
            System.out.println("  [Binlog Cache] 写入 BEGIN 事件 (tx=" + txId + ")");
        }
        BinlogEntry rowChange = BinlogEntry.rowChange(txId, key, oldValue, newValue);
        binlogCache.add(rowChange);
        System.out.println("  [Binlog Cache] 写入 ROW_CHANGE 事件: " + rowChange);
        System.out.println("  [Binlog Cache] 仅在内存 Cache 中，COMMIT 时才 fsync 到 binlog 文件");
    }

    // ==================== COMMIT（两阶段提交）====================

    /**
     * COMMIT 流程 —— 两阶段提交（2PC）完整实现
     *
     * ★ 面试必背（两阶段提交的三个 Phase，这是 MySQL 面试最高频考点之一）：
     *
     *   Phase 1 —— Redo PREPARE（InnoDB 引擎层）：
     *     ① 向 Log Buffer 写 PREPARE 标记（isPrepare=true）
     *     ② 把 Log Buffer 里这个事务的所有 Redo 一起 fsync 到 ib_logfile0
     *     此时事务处于 PREPARE 状态。
     *     → 如果在这之后、Phase 2 之前崩溃：Redo 有 PREPARE，Binlog 无 XID → 回滚
     *
     *   Phase 2 —— 写 Binlog（MySQL Server 层，这是提交的真正分界线）：
     *     ③ 把 Binlog Cache（BEGIN + ROW_CHANGE×N + XID_COMMIT）写入 binlog 文件
     *     ④ fsync binlog 文件（sync_binlog=1）
     *     只要 XID_COMMIT 落盘，无论后续是否崩溃，这个事务必须提交（从库已/将要执行）。
     *     → 如果在这之后、Phase 3 之前崩溃：Redo 有 PREPARE，Binlog 有 XID → 补提交
     *
     *   Phase 3 —— Redo COMMIT（InnoDB 引擎层）：
     *     ⑤ 向 Log Buffer 写 COMMIT 标记
     *     ⑥ fsync ib_logfile0（innodb_flush_log_at_trx_commit=1）
     *     即使这一步之前崩溃，凭 Phase 2 的 Binlog XID 也能在恢复时补提交，
     *     所以这一步对"正确性"来说是可选的（但写还是要写，不然恢复要重放更多 Redo）。
     *
     * ★ 面试一句话总结：
     *   "Binlog XID_COMMIT 落盘 = 事务提交的唯一真正分界线。
     *    之前崩就回滚，之后崩就补提交，主从永远一致。"
     *
     * ★ 面试延伸：为什么 COMMIT 不等脏页落盘才返回？
     *   "脏页落盘是随机 IO，很慢。Redo Log 是顺序追加写，很快。
     *    COMMIT 只需 Redo Log fsync，之后由 Page Cleaner 异步写脏页。
     *    即使崩溃时脏页没落盘，重放 Redo 也能还原，这就是 WAL 的核心价值。"
     */
    static void commit(long txId) {
        System.out.println("  [2PC] ══════════ 两阶段提交开始 tx=" + txId + " ══════════");

        // ── Phase 1：Redo PREPARE（InnoDB 引擎层）────────────────────────────────────
        System.out.println("  [2PC Phase1] Redo PREPARE: 写 PREPARE 标记到 Log Buffer，然后 fsync ib_logfile0");
        currentLsn++;
        RedoLogRecord prepareRecord = RedoLogRecord.commit(currentLsn, txId);
        prepareRecord.isCommit  = false;
        prepareRecord.isPrepare = true;
        writeToRedoLogBuffer(prepareRecord);

        if (flushPolicy == 1) {
            flushLogBufferToDisk();
            System.out.println("  [2PC Phase1] ib_logfile0 fsync 完成 (LSN=" + currentLsn + ", isPrepare=true)");
            System.out.println("  [2PC Phase1] → 此后若崩溃（Phase2 前）：Binlog 无 XID → 回滚");
        } else if (flushPolicy == 2) {
            flushLogBufferToDisk();
            DiskStore.markRedoBlocksUnflushed(txId);
            flushedToDisklsn = checkpointLsn;
            System.out.println("  [2PC Phase1] Redo PREPARE 写 OS cache (innodb_flush_log_at_trx_commit=2，未 fsync)");
            System.out.println("  [警告]     机器断电时 Redo PREPARE 可能丢，事务会被回滚（主从一致但丢数据）");
        } else {
            System.out.println("  [2PC Phase1] Redo PREPARE 未落盘 (innodb_flush_log_at_trx_commit=0)");
        }

        // ── Phase 2：写 Binlog（MySQL Server 层）—— 事务提交的真正分界线！────────────
        System.out.println("  [2PC Phase2] 写 Binlog: 将 Binlog Cache 刷入 binlog 文件并 fsync");
        binlogCache.add(BinlogEntry.xidCommit(txId));
        boolean binlogFsync = (syncBinlog == 1);
        DiskStore.writeBinlogEntries(binlogCache, binlogFsync);
        binlogCache.clear();

        if (binlogFsync) {
            System.out.println("  [2PC Phase2] binlog fsync 完成 ← 事务提交的真正分界线！");
            System.out.println("  [2PC Phase2] → 此后若崩溃（Phase3 前）：Binlog 有 XID → 恢复时补提交，主从均一致");
        } else {
            System.out.println("  [2PC Phase2] binlog 未 fsync (sync_binlog=0)，OS 崩溃可能丢 XID → 主从不一致风险！");
        }

        // ── Phase 3：Redo COMMIT（InnoDB 引擎层）────────────────────────────────────
        System.out.println("  [2PC Phase3] Redo COMMIT: InnoDB 补写 COMMIT 标记，事务正式完结");
        currentLsn++;
        RedoLogRecord commitRecord = RedoLogRecord.commit(currentLsn, txId);
        writeToRedoLogBuffer(commitRecord);

        if (flushPolicy == 1) {
            flushLogBufferToDisk();
            System.out.println("  [2PC Phase3] ib_logfile0 fsync 完成 (LSN=" + currentLsn + ", isCommit=true)");
            System.out.println("  [2PC Phase3] → 三阶段全部完成，返回客户端：提交成功");
            System.out.println("  [2PC]       即使 Phase3 fsync 前崩溃，凭 Binlog XID 也可在恢复时补写 COMMIT");
        } else if (flushPolicy == 2) {
            flushLogBufferToDisk();
            DiskStore.markRedoBlocksUnflushed(txId);
            flushedToDisklsn = checkpointLsn;
            System.out.println("  [2PC Phase3] COMMIT 写 OS cache (innodb_flush_log_at_trx_commit=2)");
            System.out.println("  [警告]     MySQL 进程崩溃可恢复，但机器断电会丢失最近约 1 秒内的已提交事务！");
        } else {
            System.out.println("  [2PC Phase3] COMMIT 未落盘 (innodb_flush_log_at_trx_commit=0)");
        }
        System.out.println("  [2PC] ══════════ 两阶段提交结束 tx=" + txId + " ══════════");

        // 两阶段提交完成后，释放本事务所有行锁和间隙锁
        // 真实：在 trx_commit_in_memory() 末尾调用 lock_trx_release_locks()
        LockManager.releaseLocks(txId);

        // 后台 Page Cleaner 异步把数据脏页写回 data.ibd（不阻塞 COMMIT 返回客户端）
        // ★ 这里是关键：COMMIT 不等脏页落盘，而是靠 Redo Log 保障持久性（WAL 的精髓）
        // 真实条件：page.newest_modification ≤ flushed_to_disk_lsn 才允许刷脏页
        System.out.println("  [Checkpoint] 后台 Page Cleaner 异步把数据脏页写回 data.ibd (不阻塞 COMMIT 返回)");
        Map<String, Integer> currentDisk = DiskStore.loadDataFromDisk();
        currentDisk.putAll(bufferPool);
        DiskStore.flushDataToDisk(currentDisk);
        checkpointLsn = currentLsn;
        DiskStore.saveRedoHeader(checkpointLsn, redoWriteHead);
        System.out.println("  [Checkpoint] checkpoint_lsn -> " + checkpointLsn
                + " (这之前的 ib_logfile0 空间可被循环覆盖)");

        // Undo Page 脏页也由 Page Cleaner 异步刷盘（和数据脏页独立调度）
        simulateUndoPageCleaner();
    }

    // ==================== ROLLBACK ====================

    /**
     * ROLLBACK 流程 —— 用 Undo Log 把数据改回去
     *
     * ★ 面试怎么说（回滚的原理）：
     *   "InnoDB 的回滚是通过 Undo Log 实现的。
     *    事务执行期间每次修改都记了旧值到 Undo Log，回滚时倒序读取这些记录，
     *    把每行改回旧值。
     *    回滚本身也会产生 Redo Log！
     *    因为回滚是对数据页的物理修改（把值改回去），同样需要 WAL 约束保护：
     *    如果回滚到一半崩溃了，重启后 Redo Phase 会把回滚操作也重放出来，
     *    保证数据最终回到正确状态。"
     *
     * 回滚步骤：
     *   1. 找到本事务在 Undo Log 里的所有记录
     *   2. 按 undoNo 降序排列（从最后一步开始往前撤）
     *   3. 每条记录：把旧值写回 Buffer Pool + 写 Redo Log（回滚也走 WAL）
     *   4. 释放所有锁
     */
    static void rollback(long txId) {
        System.out.println("  [Rollback] 开始回滚 tx=" + txId + "，沿 roll_pointer 链逆序读取 Undo Log ...");

        // 找出本事务所有 Undo 记录，按 undoNo 降序（逆序）遍历
        List<UndoLogRecord> txRecords = new ArrayList<>();
        for (UndoLogRecord r : undoLog) {
            if (r.txId == txId) txRecords.add(r);
        }
        txRecords.sort((a, b) -> b.undoNo - a.undoNo); // 降序 = 逆序撤销

        for (UndoLogRecord record : txRecords) {
            int curVal = bufferPool.getOrDefault(record.key,
                    DiskStore.loadDataFromDisk().getOrDefault(record.key, 0));
            bufferPool.put(record.key, record.oldValue); // 把旧值写回 Buffer Pool
            System.out.println("  [Undo Apply] undoNo=" + record.undoNo + ": " + record.key
                    + " " + curVal + " -> " + record.oldValue
                    + " (从 Undo Record 读 oldValue，写回 Buffer Pool)");
            // 回滚也写 Redo Log：保证回滚到一半崩溃时，重启能通过 Redo 继续完成回滚
            currentLsn++;
            RedoLogRecord rollbackRedo = new RedoLogRecord(currentLsn, txId, record.key, record.oldValue);
            writeToRedoLogBuffer(rollbackRedo);
            System.out.println("  [Log Buffer]  回滚也写 Redo Log (LSN=" + currentLsn
                    + "): 防止回滚到一半崩溃导致数据不一致");
        }
        System.out.println("  [Rollback] 完成，所有修改已撤销，数据回到事务开始前状态");
        System.out.println("  [Purge]    undo_001.ibu 中的 Undo Segment 标记为可重用；"
                + "purge 后台线程确认无活跃 MVCC 读者后再物理清理（不影响读）");

        // ROLLBACK 完成后释放所有锁
        LockManager.releaseLocks(txId);
    }

    // ==================== 崩溃恢复 ====================

    /**
     * 崩溃恢复 —— MySQL 重启时 InnoDB 自动执行，用户无感知
     *
     * ★ 面试必背（崩溃恢复的三个阶段，这是 InnoDB 原子性 + 持久性的最终保障）：
     *
     *   阶段1 —— Redo Phase（前滚/重建，必须先做）：
     *     ① 读 ib_logfile_header 拿到 checkpoint_lsn
     *     ② 从 ib_logfile0 读取 checkpoint_lsn 之后所有已落盘的 Redo 记录
     *     ③ 先重放 Redo for Undo（MLOG_UNDO_INSERT）→ 重建内存 Undo Page
     *        （这样 Undo Phase 才有据可查，不依赖 undo_001.ibu 是否落盘）
     *     ④ 再重放普通数据 Redo → 重建数据页（data.ibd）
     *        （有 COMMIT 或 PREPARE 标记的事务都重放，后续再决定是保留还是回滚）
     *
     *   阶段2 —— 2PC Check（决定 PREPARE 态事务的命运）：
     *     ⑤ InnoDB 把所有 PREPARE 状态的 XID 报告给 Server 层
     *     ⑥ Server 层扫描 Binlog 文件，返回有 XID_COMMIT 的集合
     *     ⑦ 有 XID → 补提交（Binlog 已落盘，从库已/将执行，必须提交）
     *        无 XID → 标记为需回滚（Binlog 无记录，从库从未执行，必须回滚）
     *
     *   阶段3 —— Undo Phase（回滚未提交的事务）：
     *     ⑧ 用阶段1 重建的 Undo Page，逆序回滚所有未提交事务（含 2PC 判定回滚的）
     *
     * ★ 面试一句话总结：
     *   "Redo Phase 先把数据'往前拉'到崩溃前的状态，
     *    然后 2PC Check 决定谁该保留谁该撤，
     *    最后 Undo Phase 把不该保留的改回去，
     *    三步走完，数据库恢复到一致性状态。"
     *
     * 为什么 Binlog 有 XID 就必须提交？
     *   "因为 Binlog 有 XID 说明从库已经/将要执行这个事务，
     *    如果主库回滚，主从就不一致了。
     *    所以，以 Binlog XID 为准，有就必须提交，没有才能回滚。"
     */
    static void crashRecovery() {
        System.out.println("\n========== [Crash Recovery] MySQL 重启，InnoDB 自动恢复 ==========");

        // Step 0：读 ib_logfile_header，拿到 checkpoint_lsn（"上次安全线"）
        // 真实：读文件头中 CRC 校验通过的那个 checkpoint 页（LOG_CHECKPOINT_1/2 中较新的）
        long[] header = DiskStore.loadRedoHeader();
        checkpointLsn  = header[0];
        redoWriteHead  = (int) header[1];
        System.out.println("  从 ib_logfile_header 读取: checkpoint_lsn=" + checkpointLsn
                + "  write_head=" + redoWriteHead);
        System.out.println("  恢复顺序：Redo Phase（重建）→ 2PC Check（决定谁提交谁回滚）→ Undo Phase（回滚）");

        // 从 ib_logfile0 读取全部已落盘的 Redo 记录
        List<RedoLogRecord> allRedo = DiskStore.readRedoBlocksFromDisk();
        System.out.println("  从 ib_logfile0 读取到 " + allRedo.size() + " 条 Redo 记录");

        // ── Redo Phase：扫描各事务状态，分类处理 ─────────────────────────────────────
        System.out.println("\n  -- Redo Phase：从 checkpoint_lsn=" + checkpointLsn
                + " 开始，重放已落盘日志 --");
        System.out.println("  重放分两类：Redo for Undo（重建 Undo Page）和普通数据日志（重建数据页）");

        // 扫描各事务的状态（三种可能）：
        //   有 isCommit=true：完整提交，直接重放数据 Redo
        //   有 isPrepare=true（无对应 COMMIT）：PREPARE 态，待 2PC Check 决策
        //   两者都没有：COMMIT 前崩溃，有 Binlog 则补提交，没有则回滚
        Set<Long> committedTxIds = new HashSet<>();
        Set<Long> preparedTxIds  = new HashSet<>();
        for (RedoLogRecord r : allRedo) {
            if (!r.isFlushed) continue;
            if (r.isCommit)  committedTxIds.add(r.txId);
            if (r.isPrepare) preparedTxIds.add(r.txId);
        }
        preparedTxIds.removeAll(committedTxIds); // 排除已有 COMMIT 的（不需要 2PC Check）
        System.out.println("  ib_logfile0 中完整提交（有 COMMIT 标记）的事务：" + committedTxIds);
        System.out.println("  ib_logfile0 中处于 PREPARE 态（无 COMMIT 标记）的事务：" + preparedTxIds + " → 待 2PC Check");

        // Step 1a：重放 Redo for Undo → 重建内存 Undo Page
        //
        // 为什么要先重建 Undo Page？
        //   因为 Undo Phase 需要用 Undo Page 来回滚未提交事务，
        //   但 undo_001.ibu 文件可能没来得及落盘，
        //   所以必须通过重放 Redo for Undo 把 Undo Page 先还原出来。
        System.out.println("\n  [Redo Phase - Step1a] 重放 Redo for Undo（MLOG_UNDO_INSERT）→ 重建内存 Undo Page ...");
        undoLog.clear();
        for (RedoLogRecord r : allRedo) {
            if (r.isRedoForUndo && r.isFlushed && r.lsn > checkpointLsn) {
                undoLog.add(r.undoRecord);
                System.out.println("  [Redo for Undo] lsn=" + r.lsn + " 重建 Undo Page: " + r.undoRecord);
            }
        }
        System.out.println("  重建后内存 Undo Page 共 " + undoLog.size() + " 条记录");

        // Step 1b：重放普通数据 Redo → 重建数据页（data.ibd）
        // 注意：PREPARE 态事务的数据 Redo 也一起重放（先重放，后面再决定是保留还是回滚）
        System.out.println("\n  [Redo Phase - Step1b] 重放数据日志 → 重建 data.ibd（含 PREPARE 态事务）...");
        Map<String, Integer> diskData = DiskStore.loadDataFromDisk();
        Set<Long> allKnownTxIds = new HashSet<>(committedTxIds);
        allKnownTxIds.addAll(preparedTxIds);
        boolean changed = false;
        for (RedoLogRecord r : allRedo) {
            if (!r.isCommit && !r.isPrepare && !r.isRedoForUndo
                    && r.isFlushed && r.lsn > checkpointLsn
                    && allKnownTxIds.contains(r.txId)) {
                diskData.put(r.key, r.newValue);
                System.out.println("  [Redo Apply] lsn=" + r.lsn + " 重放: " + r.key
                        + " = " + r.newValue + " (tx=" + r.txId + ")");
                changed = true;
            }
        }
        if (changed) DiskStore.flushDataToDisk(diskData);

        // ── 2PC Check：扫描 Binlog，决定 PREPARE 态事务该提交还是回滚 ─────────────────
        System.out.println("\n  -- 2PC Check：扫描 Binlog，决定 PREPARE 态事务的最终命运 --");
        System.out.println("  真实：InnoDB 报告所有 PREPARE XID → Server 层扫描 Binlog → 返回有 XID_COMMIT 的集合");

        Set<Long> binlogXids = DiskStore.loadCommittedXidsFromBinlog();
        System.out.println("  Binlog 中已落盘 XID_COMMIT 的事务：" + binlogXids);

        Set<Long> toCommit   = new HashSet<>(preparedTxIds);
        Set<Long> toRollback = new HashSet<>(preparedTxIds);
        toCommit.retainAll(binlogXids);    // 补提交 = PREPARE ∩ 有 XID（Binlog 已记录，必须提交）
        toRollback.removeAll(binlogXids);  // 回滚   = PREPARE - 有 XID（Binlog 无记录，必须回滚）

        if (!toCommit.isEmpty()) {
            System.out.println("  PREPARE 态事务 " + toCommit
                    + "：Binlog 有 XID → 补提交（Phase3 未完成但 Binlog 已落盘，从库已/将执行，必须提交）");
            committedTxIds.addAll(toCommit);
        }
        if (!toRollback.isEmpty()) {
            System.out.println("  PREPARE 态事务 " + toRollback
                    + "：Binlog 无 XID → 将由 Undo Phase 回滚（从库从未执行，必须回滚，保证主从一致）");
        }
        if (preparedTxIds.isEmpty()) {
            System.out.println("  无 PREPARE 态事务，跳过 2PC Check");
        }

        // ── Undo Phase：回滚未提交事务（含 2PC 判定为回滚的事务）─────────────────────
        // 直接用 Step1a 重建好的内存 undoLog（不依赖 undo_001.ibu 是否落盘！）
        System.out.println("\n  -- Undo Phase：用 Redo Phase 重建的 Undo Page 回滚未提交事务 --");
        System.out.println("  （不依赖 undo_001.ibu 是否落盘！Undo Page 已由 Redo for Undo 重建出来）");
        Set<Long> allUndoTxIds = new HashSet<>();
        for (UndoLogRecord r : undoLog) allUndoTxIds.add(r.txId);
        Set<Long> uncommitted = new HashSet<>(allUndoTxIds);
        uncommitted.removeAll(committedTxIds); // 去掉已提交/补提交的，剩下的都需要回滚
        System.out.println("  需回滚的事务：" + uncommitted);

        for (long txId : uncommitted) {
            List<UndoLogRecord> txRecords = new ArrayList<>();
            for (UndoLogRecord r : undoLog) {
                if (r.txId == txId) txRecords.add(r);
            }
            txRecords.sort((a, b) -> b.undoNo - a.undoNo); // 按 undoNo 降序（逆序撤销）
            for (UndoLogRecord r : txRecords) {
                diskData.put(r.key, r.oldValue);
                System.out.println("  [Undo Apply] tx=" + txId
                        + " 沿 roll_pointer 链回滚: " + r.key + " -> " + r.oldValue);
            }
        }
        if (!uncommitted.isEmpty()) DiskStore.flushDataToDisk(diskData);

        System.out.println("\n  [Recovery 完成] 数据库已恢复到一致性状态，可以对外服务");
        System.out.println("  当前 data.ibd: " + DiskStore.loadDataFromDisk());
    }
}

