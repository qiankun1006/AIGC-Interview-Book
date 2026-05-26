/**
 * ============================================================
 * MySQL Demo 公共基础类 —— 三个 Demo 共用的常量 & 实体类
 * ============================================================
 *
 * 这里定义了所有演示场景共用的账户初始值、以及
 * InnoDB 内部三大核心数据结构的 Java 模型：
 *   · UndoLogRecord  → Undo Log 里的一条记录
 *   · RedoLogRecord  → Redo Log 里的一条记录
 *   · LockEntry      → 锁表里的一条记录
 *   · BinlogEntry    → Binlog 里的一条事件
 *
 * ★ 面试口诀：
 *   "改之前写 Undo（保旧值），改之后写 Redo（记新值），提交时写 Binlog（通知从库）"
 */
public class MysqlDemoBase {

    // ==================== 账户常量 ====================

    /** Alice 初始余额 */
    public static final int ALICE_INIT    = 10000;
    /** Bob 初始余额 */
    public static final int BOB_INIT      = 5000;
    /** Charlie 初始余额（场景2 多步回滚演示用） */
    public static final int CHARLIE_INIT  = 8000;
    /** 每次转账金额（Alice → Bob） */
    public static final int TRANSFER_AMT  = 2000;
    /** 转账后 Alice 余额 = 10000 - 2000 = 8000 */
    public static final int ALICE_AFTER   = ALICE_INIT - TRANSFER_AMT;
    /** 转账后 Bob 余额 = 5000 + 2000 = 7000 */
    public static final int BOB_AFTER     = BOB_INIT   + TRANSFER_AMT;

    // ==================== Undo Log Record ====================

    /**
     * Undo Log Record —— InnoDB 用来"后悔"和"时光机"的那条记录
     *
     * ★ 面试怎么说：
     *   "InnoDB 每次 UPDATE/DELETE 之前，会先把旧值写到 Undo Log 里。
     *    这条记录有两个用途：
     *    ① 事务 ROLLBACK 时，沿 roll_pointer 链逆序读旧值，把数据改回去（保原子性）；
     *    ② MVCC 快照读时，沿 roll_pointer 链向前找符合 ReadView 的历史版本（保隔离性）。"
     *
     * 关键字段说明：
     *   txId        → 哪个事务写的（对应行上的隐藏列 DB_TRX_ID）
     *   undoNo      → 本事务内第几次操作，从 100 开始每次 +10（100→110→120...）
     *                 回滚时按 undoNo 降序撤销，确保逆序（先撤最后一步）
     *   key         → 改的是哪一行（这里用账户名模拟主键）
     *   oldValue    → 改之前的值（回滚就是把这个值写回去）
     *   rollPointer → 指向"本事务上一条 Undo Record"的位置（-1 表示这是第一条，链头）
     *                 MVCC 就是沿这条链一路向前"穿越"到历史版本
     *
     * undoNo vs rollPointer 的区别（很多面试者会搞混）：
     *   undoNo      → 逻辑编号，只属于本事务，决定"回滚顺序"
     *   rollPointer → 物理地址，全局编号，构成跨版本的"历史链"，MVCC 靠它读历史
     *
     * 存储位置（真实 MySQL）：
     *   MySQL 8.0 默认存在独立的 undo_001.ibu / undo_002.ibu 文件里
     *   (5.7 及更早存在 ibdata1 系统表空间的 Rollback Segment 中)
     */
    public static class UndoLogRecord {

        public enum UndoType { INSERT, UPDATE, DELETE }

        public long     txId;
        public int      undoNo;       // 事务内操作序号，回滚时按此逆序
        public String   key;          // 主键（这里用账户名）
        public int      oldValue;     // 改之前的旧值，回滚时写回
        public UndoType type;
        public long     rollPointer;  // 指向本事务上一条 Undo Record 的位置，-1 = 链头（第一条）

        public UndoLogRecord(long txId, int undoNo, String key, int oldValue,
                             UndoType type, long rollPointer) {
            this.txId        = txId;
            this.undoNo      = undoNo;
            this.key         = key;
            this.oldValue    = oldValue;
            this.type        = type;
            this.rollPointer = rollPointer;
        }

        /** 序列化为文本行，写入 undo_001.ibu */
        public String serialize() {
            // 格式: txId|undoNo|type|key|oldValue|rollPointer
            return txId + "|" + undoNo + "|" + type + "|" + key + "|" + oldValue + "|" + rollPointer;
        }

        public static UndoLogRecord deserialize(String line) {
            String[] p = line.split("\\|");
            // 顺序: txId|undoNo|type|key|oldValue|rollPointer
            return new UndoLogRecord(
                    Long.parseLong(p[0]),
                    Integer.parseInt(p[1]),
                    p[3],
                    Integer.parseInt(p[4]),
                    UndoType.valueOf(p[2]),
                    Long.parseLong(p[5])
            );
        }

        @Override
        public String toString() {
            // rollPointer=-1 → 本事务第一条记录（链头），没有更早的版本
            // rollPointer>=1000 → 指向上一条 Undo 的物理地址（MVCC 沿此找历史版本）
            String ptr = rollPointer < 0 ? "-1(链头,无前驱)" : "addr=" + rollPointer;
            return String.format(
                    "UndoRecord[tx=%d, undoNo=%d, type=%s, key=%s, oldVal=%d, rollPtr->%s]",
                    txId, undoNo, type, key, oldValue, ptr);
        }
    }

    // ==================== Redo Log Record ====================

    /**
     * Redo Log Record —— InnoDB 用来"重放崩溃前操作"的那条记录
     *
     * ★ 面试怎么说：
     *   "Redo Log 是物理日志，记录的是'某个数据页上哪个位置改成了什么新值'。
     *    它的核心作用是 WAL（Write-Ahead Logging，先写日志再写数据）：
     *    提交时只需把 Redo Log fsync 到磁盘，内存中的脏页可以晚点再写。
     *    即使机器崩溃，重启后重放 Redo Log 就能把数据恢复到提交时的状态。"
     *
     * 为什么叫"物理日志"？
     *   记录的是"page_no=5, offset=128, 把这 4 个字节改成 8000"这样的字节级变化，
     *   可以幂等重放（重放多少次结果一样）。
     *   Binlog 是"逻辑日志"，记录的是 SQL 语句或行变更前后值，需要按顺序执行。
     *
     * 关键字段说明：
     *   lsn        → Log Sequence Number，全局单调递增的序号，代表"日志写到哪儿了"
     *                三个 LSN 的关系（面试常考）：
     *                checkpointLsn ≤ flushedToDisklsn ≤ currentLsn
     *   isCommit   → 是否是 COMMIT 标记（代表这个事务已完整提交）
     *   isPrepare  → 是否是 PREPARE 标记（两阶段提交的第一阶段，等待 Binlog fsync）
     *   isRedoForUndo → 是否是"给 Undo Page 写的 Redo"（MLOG_UNDO_INSERT 类型）
     *                   Undo Log 也存在数据页上，改 Undo Page 同样需要 WAL 保护
     *   isFlushed  → 是否已 fsync 到 ib_logfile0（崩溃恢复只能重放已落盘的记录）
     *
     * WAL 约束一句话总结：
     *   "数据脏页落盘之前，该页对应的 Redo Log 必须已 fsync"——
     *   这样即使脏页还没写盘就崩溃，重启后 Redo 也能把它还原出来。
     */
    public static class RedoLogRecord {
        public long    lsn;             // Log Sequence Number，单调递增
        public long    txId;
        public String  key;             // 模拟 space_id+page_no+offset（改的是哪一页哪个位置）
        public int     newValue;        // 改成了什么新值（物理新值）
        public boolean isCommit;        // 是 COMMIT 标记（真实：MLOG_MULTI_REC_END 类型）
        public boolean isFlushed;       // 已 fsync 到磁盘（true = 崩溃也不丢）

        /**
         * isPrepare —— 两阶段提交第一阶段的标记
         *
         * ★ 面试怎么说：
         *   "COMMIT 时 InnoDB 先写一个 PREPARE 标记并 fsync（Phase1），
         *    然后 MySQL Server 层写 Binlog 并 fsync（Phase2），
         *    最后 InnoDB 再写 COMMIT 标记（Phase3）。
         *    崩溃恢复时，如果看到 PREPARE 但没有 COMMIT：
         *      → 去 Binlog 里查，有 XID 就'补提交'，没有就'回滚'。
         *    这样无论在哪一步崩溃，主库和从库的数据都能保持一致。"
         */
        public boolean isPrepare;       // 两阶段提交 Phase1 的 PREPARE 标记

        /**
         * isRedoForUndo —— 给 Undo Page 写的特殊 Redo（MLOG_UNDO_INSERT）
         *
         * ★ 面试怎么说：
         *   "Undo Log 不是直接写文件的，它先写到 Buffer Pool 的 Undo Page（内存页）上，
         *    改 Undo Page 也是物理修改，同样需要 WAL 保护，
         *    所以写 Undo Page 的同时会向 Log Buffer 追加一条 Redo for Undo。
         *    崩溃重启时，Redo Phase 先把这条 Redo for Undo 重放，
         *    把 Undo Page 还原出来，Undo Phase 才能用它来回滚未提交的事务。"
         */
        public boolean      isRedoForUndo; // true = MLOG_UNDO_INSERT（给 Undo Page 的 Redo）
        public UndoLogRecord undoRecord;   // isRedoForUndo=true 时，携带对应的 Undo Record

        /** 普通数据变更记录（对应 data.ibd 数据页） */
        public RedoLogRecord(long lsn, long txId, String key, int newValue) {
            this.lsn          = lsn;
            this.txId         = txId;
            this.key          = key;
            this.newValue     = newValue;
            this.isCommit     = false;
            this.isFlushed    = false;
            this.isRedoForUndo = false;
            this.undoRecord   = null;
        }

        /** 创建 COMMIT 标记（无 key/value，只标记事务已提交） */
        public static RedoLogRecord commit(long lsn, long txId) {
            RedoLogRecord r = new RedoLogRecord(lsn, txId, null, 0);
            r.isCommit = true;
            return r;
        }

        /**
         * 创建 Redo for Undo 记录（MLOG_UNDO_INSERT 类型）
         *
         * 崩溃恢复时，Redo Phase 重放此条记录，
         * 把 undoRecord 写回内存 undoLog（相当于重建了 Undo Page 的内容），
         * 这样后续 Undo Phase 就能用它来回滚未提交事务，
         * 不需要 undo_001.ibu 文件是否已落盘。
         */
        public static RedoLogRecord redoForUndo(long lsn, long txId, UndoLogRecord undoRec) {
            RedoLogRecord r   = new RedoLogRecord(lsn, txId, "UNDO_PAGE", 0);
            r.isRedoForUndo   = true;
            r.undoRecord      = undoRec;
            return r;
        }

        @Override
        public String toString() {
            String loc = isFlushed ? "[ib_logfile0✓]" : "[LogBuffer○  ]";
            if (isPrepare)
                return String.format("Redo%s lsn=%-4d tx=%d PREPARE (2PC Phase1 完成，等待 Binlog fsync)", loc, lsn, txId);
            if (isCommit)
                return String.format("Redo%s lsn=%-4d tx=%d COMMIT  (2PC Phase2 完成)", loc, lsn, txId);
            if (isRedoForUndo)
                return String.format("Redo%s lsn=%-4d tx=%d MLOG_UNDO_INSERT undoNo=%d key=%s oldVal=%d",
                        loc, lsn, txId, undoRecord.undoNo, undoRecord.key, undoRecord.oldValue);
            return String.format("Redo%s lsn=%-4d tx=%d key=%-8s newVal=%d",
                    loc, lsn, txId, key, newValue);
        }
    }

    // ==================== Lock Entry ====================

    /**
     * InnoDB 锁记录实体 —— 锁表里的一条记录
     *
     * ★ 面试怎么说（InnoDB 锁的两层结构）：
     *   "InnoDB 的锁分两层：表级意向锁 和 行级锁。
     *
     *    表级意向锁（Intention Lock）是 InnoDB 自动加的'占位符'：
     *      加行 S 锁之前，先在表上加 IS（意向共享锁）；
     *      加行 X 锁之前，先在表上加 IX（意向排他锁）。
     *      IS 和 IX 互相兼容，不会互相阻塞。
     *      它的作用是：LOCK TABLE 时不用逐行检查有没有行锁，看一眼意向锁就知道了。
     *
     *    行级锁（Row-Level Lock）分三种：
     *      Record Lock（行锁）：锁住一条索引记录，防止并发修改。
     *      Gap Lock（间隙锁）：锁住两条记录之间的间隙，防止其他事务往里插数据（防幻读）。
     *      Next-Key Lock = Record Lock + 前面的 Gap Lock，是 RR 隔离级别的默认策略。
     *
     *    什么时候加什么锁：
     *      SELECT ... FOR SHARE  → IS + Record S Lock
     *      SELECT ... FOR UPDATE → IX + Record X Lock + Gap Lock（RR 级别）
     *      UPDATE / DELETE       → 同上
     *      普通 SELECT           → 不加锁，走 MVCC 读历史版本（快照读）"
     *
     * Demo 说明：只模拟单事务加锁，不模拟锁等待和死锁检测。
     */
    public static class LockEntry {

        public enum LockType {
            /** 表级意向共享锁：准备加行 S 锁前先占位，告知表锁"我有行要共享读" */
            IS,
            /** 表级意向排他锁：准备加行 X 锁前先占位，告知表锁"我有行要独占写" */
            IX,
            /** 行级共享锁（S锁）：多个事务可同时持有，但阻塞任何 X 锁请求 */
            RECORD_S,
            /** 行级排他锁（X锁）：独占，阻塞其他事务的任何 S 锁或 X 锁请求 */
            RECORD_X,
            /**
             * 间隙锁（Gap Lock）：锁住两条记录之间的间隙（不含端点），只在 RR 隔离级别生效
             * 作用：防止其他事务在此区间 INSERT，从而防止幻读
             * RC 级别不加 Gap Lock（所以 RC 有幻读风险，但并发性更好）
             */
            GAP,
            /**
             * Next-Key Lock = Record Lock + 前面的 Gap Lock
             * InnoDB RR 级别下 UPDATE/DELETE/SELECT FOR UPDATE 的默认加锁策略
             * 例：UPDATE ... WHERE name='Bob' → Next-Key Lock = Record X(Bob) + Gap(-∞, Bob)
             */
            NEXT_KEY,
            /** 表级共享锁：LOCK TABLES ... READ，与 IX 冲突，阻塞写操作 */
            TABLE_S,
            /** 表级排他锁：LOCK TABLES ... WRITE 或 DDL，与所有锁冲突，整表独占 */
            TABLE_X
        }

        public long     txId;       // 持锁的事务 ID
        public LockType type;       // 锁类型
        public String   resource;   // 锁住的资源（表名 / 行 key / 间隙描述）
        public boolean  isTableLock; // true = 表级锁，false = 行级锁

        public LockEntry(long txId, LockType type, String resource, boolean isTableLock) {
            this.txId        = txId;
            this.type        = type;
            this.resource    = resource;
            this.isTableLock = isTableLock;
        }

        @Override
        public String toString() {
            String scope = isTableLock ? "[TABLE]" : "[ROW  ]";
            return String.format("Lock%s tx=%-2d %-10s on %-20s", scope, txId, type, resource);
        }
    }

    // ==================== Binlog Entry ====================

    /**
     * Binlog 事件实体 —— MySQL Server 层写的那条日志
     *
     * ★ 面试怎么说（Redo Log vs Binlog 的区别）：
     *   "Redo Log 是 InnoDB 存储引擎自己的日志，是物理日志（记录页内字节变化），
     *    循环写，空间有限，主要用于崩溃恢复。
     *    Binlog 是 MySQL Server 层的日志，是逻辑日志（记录 SQL 或行前后值），
     *    追加写，保存完整变更历史，主要用于主从复制和按时间点恢复。"
     *
     * ★ 面试怎么说（Binlog 在两阶段提交中的角色）：
     *   "Binlog 的 XID_COMMIT 事件落盘是整个事务提交的真正分界线。
     *    只要 XID_COMMIT 写入 Binlog 文件并 fsync（sync_binlog=1），
     *    从库就会/已经执行了这个事务。
     *    所以崩溃恢复时，InnoDB 发现 Redo Log 里有 PREPARE 标记，
     *    就去 Binlog 查：有 XID → 必须补提交；没有 XID → 必须回滚。
     *    这样主库和从库永远保持一致。"
     *
     * ★ 面试怎么说（Binlog 三种格式，经常被问）：
     *   "STATEMENT 格式：记录 SQL 语句，但含 NOW()、UUID() 等函数时可能主从不一致。
     *    ROW 格式：记录每行修改前后的完整值，最精确，生产环境推荐（本 Demo 模拟此格式）。
     *    MIXED 格式：自动切换，简单 SQL 用 STATEMENT，不确定函数用 ROW。"
     *
     * ★ 面试怎么说（sync_binlog 参数）：
     *   "sync_binlog=1 表示每次事务提交都 fsync Binlog，最安全；
     *    sync_binlog=0 交给 OS 自动刷，OS 崩溃时可能丢 Binlog 导致主从不一致；
     *    生产金融场景推荐 sync_binlog=1 + innodb_flush_log_at_trx_commit=1，即'双1配置'。"
     */
    public static class BinlogEntry {

        public enum Type {
            /** 事务开始标记（每个事务的第一条 Binlog 事件） */
            BEGIN,
            /** 行变更事件：ROW 格式，记录修改前的旧值（before image）和修改后的新值（after image） */
            ROW_CHANGE,
            /**
             * 事务提交标记，携带 XID（与 Redo Log 里的 txId 对应）
             * 这是崩溃恢复的关键分界线：
             *   Binlog 有 XID_COMMIT → 从库已/将要执行 → 主库必须补提交
             *   Binlog 无 XID_COMMIT → 从库从未执行   → 主库必须回滚
             * 真实 Binlog 中叫 XID_EVENT
             */
            XID_COMMIT
        }

        /** 事务 XID（与 Redo Log PREPARE 中的 txId 对应，是连接两套日志的桥梁） */
        public long txId;
        public Type type;
        /** ROW_CHANGE 时改的是哪一行（模拟 table_id + 主键） */
        public String key;
        /** ROW_CHANGE 时改之前的值（before image，ROW 格式特有，可用于闪回） */
        public int oldValue;
        /** ROW_CHANGE 时改之后的值（after image） */
        public int newValue;
        /**
         * 是否已 fsync 到磁盘 binlog 文件
         * sync_binlog=1 时写入即 fsync，置为 true；
         * sync_binlog=0 时只到 OS cache，置为 false（OS 崩溃则丢失）
         */
        public boolean isFlushed;

        /** 创建 BEGIN 事件 */
        public static BinlogEntry begin(long txId) {
            BinlogEntry e = new BinlogEntry();
            e.txId = txId;
            e.type = Type.BEGIN;
            e.isFlushed = false;
            return e;
        }

        /** 创建 ROW_CHANGE 事件（ROW 格式，记录行变更前后值） */
        public static BinlogEntry rowChange(long txId, String key, int oldVal, int newVal) {
            BinlogEntry e = new BinlogEntry();
            e.txId = txId;
            e.type = Type.ROW_CHANGE;
            e.key = key;
            e.oldValue = oldVal;
            e.newValue = newVal;
            e.isFlushed = false;
            return e;
        }

        /**
         * 创建 XID_COMMIT 事件 —— 这是两阶段提交最关键的那一步
         * 调用此方法后，只要 fsync 成功（isFlushed=true），
         * 就代表事务已提交，主从均必须保有此变更，不可回滚。
         */
        public static BinlogEntry xidCommit(long txId) {
            BinlogEntry e = new BinlogEntry();
            e.txId = txId;
            e.type = Type.XID_COMMIT;
            e.isFlushed = false;
            return e;
        }

        /** 序列化为文本行，写入 binlog 文件 */
        public String serialize() {
            // 格式：txId|type|key|oldValue|newValue|isFlushed
            String keyStr = (key != null) ? key : "";
            return txId + "|" + type + "|" + keyStr + "|" + oldValue + "|" + newValue + "|" + isFlushed;
        }

        public static BinlogEntry deserialize(String line) {
            String[] p = line.split("\\|", -1);
            BinlogEntry e = new BinlogEntry();
            e.txId = Long.parseLong(p[0]);
            e.type = Type.valueOf(p[1]);
            e.key = p[2].isEmpty() ? null : p[2];
            e.oldValue = Integer.parseInt(p[3]);
            e.newValue = Integer.parseInt(p[4]);
            e.isFlushed = Boolean.parseBoolean(p[5]);
            return e;
        }

        @Override
        public String toString() {
            String loc = isFlushed ? "[binlog✓]" : "[cache○ ]";
            switch (type) {
                case BEGIN:
                    return String.format("Binlog%s tx=%d BEGIN", loc, txId);
                case ROW_CHANGE:
                    return String.format("Binlog%s tx=%d ROW_CHANGE key=%-6s %d→%d", loc, txId, key, oldValue, newValue);
                case XID_COMMIT:
                    return String.format("Binlog%s tx=%d XID_COMMIT ← 崩溃恢复的关键分界：有此事件则补提交，无则回滚", loc, txId);
                default:
                    return "";
            }
        }
    }
}

