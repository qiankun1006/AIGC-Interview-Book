
import java.util.*;

/**
 * ============================================================
 * MySQL InnoDB 事务隔离级别 —— MVCC 完整模拟（含转账场景）
 * ============================================================
 *
 * 【核心机制：MVCC（Multi-Version Concurrency Control）多版本并发控制】
 *
 * 1. 每行数据的隐藏字段（InnoDB 实际存储）：
 *    ┌──────────────┬──────────────────────────────────────────┐
 *    │ DB_TRX_ID    │ 6B，最后一次修改该行的事务 ID              │
 *    │ DB_ROLL_PTR  │ 7B，回滚指针，指向 Undo Log 中的旧版本    │
 *    │ DB_ROW_ID    │ 6B，隐式主键（无主键时使用）               │
 *    └──────────────┴──────────────────────────────────────────┘
 *
 * 2. 版本链（Undo Log 版本链）：
 *    每次 UPDATE/DELETE 都会在 Undo Log 写入旧版本，
 *    通过 DB_ROLL_PTR 形成链：新版本 → 旧版本 → 更老版本 → ...
 *
 * 3. ReadView（快照读的核心，InnoDB 源码：read0read.cc）：
 *    ┌────────────────┬────────────────────────────────────────┐
 *    │ creator_trx_id │ 创建本 ReadView 的事务 ID              │
 *    │ m_ids          │ 创建时活跃（未提交）的其他事务 ID 集合  │
 *    │ min_trx_id     │ m_ids 中最小值（小于此值的版本已提交）  │
 *    │ max_trx_id     │ 下一个将分配的事务 ID（尚未存在）       │
 *    └────────────────┴────────────────────────────────────────┘
 *
 * 4. ReadView 可见性判断（对版本链上每个版本的 trx_id 判断）：
 *    ① trx_id == creator_trx_id                     → 自己写的，可见 ✓
 *    ② trx_id <  min_trx_id                         → 已提交的老事务，可见 ✓
 *    ③ trx_id >= max_trx_id                         → ReadView 创建后才开启，不可见 ✗
 *    ④ min_trx_id <= trx_id < max_trx_id：
 *       · trx_id 在 m_ids 中   → 活跃未提交，不可见 ✗
 *       · trx_id 不在 m_ids 中 → 已提交，可见 ✓
 *    → 沿 roll_pointer 往链上继续找，直到找到可见版本
 *
 * 5. 四种隔离级别的实现方式：
 *    ┌─────────────────────┬──────────────────────────────────────────────┐
 *    │ READ UNCOMMITTED    │ 不用 MVCC，直接读最新版本（有脏读）            │
 *    │ READ COMMITTED (RC) │ 每次 SELECT 重新生成 ReadView（能看新提交）    │
 *    │ REPEATABLE READ (RR)│ 事务首次 SELECT 创建 ReadView，之后复用（默认）│
 *    │ SERIALIZABLE        │ 所有读加 S 锁，退化为串行，吞吐量最低          │
 *    └─────────────────────┴──────────────────────────────────────────────┘
 *
 * 6. 幻读的处理：
 *    · 快照读（普通 SELECT）：RR 下 ReadView 固定，天然避免幻读
 *    · 当前读（SELECT FOR UPDATE/LOCK IN SHARE MODE/UPDATE/DELETE）：
 *      需要 Gap Lock + Next-Key Lock 防止其他事务在间隙中插入
 *
 * 【本 Demo 演示的 7 个场景】
 *   场景1：脏读（READ UNCOMMITTED）
 *   场景2：不可重复读（READ COMMITTED）
 *   场景3：可重复读（REPEATABLE READ）
 *   场景4：幻读（快照读 vs 当前读）
 *   场景5：串行化（SERIALIZABLE）
 *   场景6：ReadView 四条可见性规则完整验证
 *   场景7：转账一致性验证（Alice→Bob，并发读写下的隔离保证）
 *
 * 【与真实 InnoDB 的差异】
 *   · 真实 Undo Log 存储在独立表空间，这里用 RowVersion 链模拟
 *   · 真实版本清理依赖 Purge 线程，这里版本链无限增长
 *   · 真实当前读需要加锁（Gap Lock/Next-Key Lock），这里仅文字说明
 *   · 真实事务 ID 是 6B 整数，这里用 long 模拟
 */
public class IsolationLevelMVCCDemo extends MysqlDemoBase {

    // ==================== 隔离级别枚举 ====================

    enum IsolationLevel {
        READ_UNCOMMITTED,  // 读未提交：直接读最新版本，有脏读
        READ_COMMITTED,    // 读已提交：每次 SELECT 创建新 ReadView
        REPEATABLE_READ,   // 可重复读：首次 SELECT 创建 ReadView，之后复用（MySQL 默认）
        SERIALIZABLE       // 串行化：所有读加锁，退化为串行
    }

    // ==================== 数据行的版本链节点 ====================

    /**
     * 数据行的一个版本（对应 InnoDB 数据页中的行 + Undo Log 中的旧版本）
     *
     * 真实 InnoDB 每行隐藏字段：
     *   DB_TRX_ID(6B)  + DB_ROLL_PTR(7B) + DB_ROW_ID(6B)
     * 这里用 trxId + prev 模拟前两个字段。
     */
    static class RowVersion {
        String key;
        int    value;
        long   trxId;    // DB_TRX_ID：写入本版本的事务 ID
        RowVersion prev; // DB_ROLL_PTR：指向 Undo Log 中的上一个版本（旧版本）

        RowVersion(String key, int value, long trxId, RowVersion prev) {
            this.key   = key;
            this.value = value;
            this.trxId = trxId;
            this.prev  = prev;
        }

        @Override public String toString() {
            return String.format("v[%s=%d, tx=%d]", key, value, trxId);
        }
    }

    // ==================== ReadView ====================

    /**
     * ReadView：快照读的"眼镜"，决定能看见版本链上的哪个版本。
     *
     * 创建时机：
     *   RC  → 每次 SELECT 都创建一个新的（所以能看到最新提交）
     *   RR  → 事务第一次 SELECT 时创建，之后复用（快照固定）
     *
     * 对应 InnoDB 源码：storage/innobase/read/read0read.cc
     */
    static class ReadView {
        long      creatorTrxId; // 创建本 ReadView 的事务 ID（自己）
        Set<Long> mIds;         // 创建时活跃（未提交）的其他事务 ID
        long      minTrxId;     // min(mIds)，小于此值的版本一定已提交可见
        long      maxTrxId;     // 下一个将分配的 trx_id，>= 此值的版本一定不可见

        ReadView(long creatorTrxId, Set<Long> activeTxIds, long nextTrxId) {
            this.creatorTrxId = creatorTrxId;
            this.mIds         = new HashSet<>(activeTxIds);
            this.mIds.remove(creatorTrxId); // 自己不算"活跃他人"
            this.minTrxId     = mIds.isEmpty() ? nextTrxId : Collections.min(mIds);
            this.maxTrxId     = nextTrxId;
        }

        /**
         * 判断某个版本（trxId）对本 ReadView 是否可见
         *
         * 规则①：trxId == creatorTrxId → 自己写的，可见
         * 规则②：trxId <  minTrxId     → 已提交老版本，可见
         * 规则③：trxId >= maxTrxId     → ReadView 创建后才开启，不可见
         * 规则④：minTrxId <= trxId < maxTrxId：
         *          在 mIds → 活跃未提交，不可见
         *          不在    → 已提交，可见
         */
        boolean isVisible(long trxId) {
            if (trxId == creatorTrxId) return true;   // 规则①
            if (trxId <  minTrxId)     return true;   // 规则②
            if (trxId >= maxTrxId)     return false;  // 规则③
            return !mIds.contains(trxId);             // 规则④
        }

        String ruleApplied(long trxId) {
            if (trxId == creatorTrxId)  return "规则①:自己写的";
            if (trxId <  minTrxId)      return "规则②:已提交老版本";
            if (trxId >= maxTrxId)      return "规则③:未来事务";
            if (mIds.contains(trxId))   return "规则④:活跃未提交";
            return                             "规则④:已提交";
        }

        @Override public String toString() {
            return String.format("ReadView[creator=%d, m_ids=%s, min=%d, max=%d]",
                    creatorTrxId, mIds, minTrxId, maxTrxId);
        }
    }

    // ==================== 全局状态 ====================

    /** 数据库：key → 版本链头（最新版本） */
    static Map<String, RowVersion> database = new LinkedHashMap<>();

    /** 活跃事务集合（已 BEGIN 但未 COMMIT/ROLLBACK） */
    static Set<Long> activeTxIds = new LinkedHashSet<>();

    /** 已提交事务集合（用于日志） */
    static Set<Long> committedTxIds = new HashSet<>();

    /** 全局事务 ID 分配器（真实 InnoDB 中由 trx_sys->max_trx_id 维护） */
    static long trxIdCounter = 1;

    // ==================== 事务操作 ====================

    /** 开启事务，返回 trxId */
    static long beginTransaction(String txName) {
        long trxId = trxIdCounter++;
        activeTxIds.add(trxId);
        System.out.printf("  [BEGIN]    %-12s trxId=%-3d  当前活跃事务=%s%n",
                txName, trxId, activeTxIds);
        return trxId;
    }

    /**
     * 写入数据（INSERT/UPDATE）
     * 真实流程：
     *   1. 将旧版本写入 Undo Log（保存 DB_TRX_ID、DB_ROLL_PTR、字段值）
     *   2. 修改数据页中的行，更新 DB_TRX_ID = 当前事务，DB_ROLL_PTR 指向 Undo Log
     */
    static void write(long trxId, String key, int newValue) {
        RowVersion prev    = database.get(key);
        RowVersion newVer  = new RowVersion(key, newValue, trxId, prev);
        database.put(key, newVer);
        String prevDesc = prev == null ? "∅" : String.valueOf(prev.value);
        System.out.printf("  [WRITE]    trxId=%-3d  %s: %s → %d  " +
                          "（旧版本存入 Undo Log，版本链头更新）%n",
                trxId, key, prevDesc, newValue);
    }

    /** 提交事务（释放锁，trxId 从活跃集合移除） */
    static void commit(long trxId, String txName) {
        activeTxIds.remove(trxId);
        committedTxIds.add(trxId);
        System.out.printf("  [COMMIT]   %-12s trxId=%-3d  当前活跃事务=%s%n",
                txName, trxId, activeTxIds);
    }

    /** 回滚事务（通过 Undo Log 版本链恢复旧版本） */
    static void rollback(long trxId, String txName) {
        activeTxIds.remove(trxId);
        // 从版本链中摘除本事务写入的所有版本（模拟 Undo Log 回滚）
        for (Map.Entry<String, RowVersion> entry : database.entrySet()) {
            RowVersion cur = entry.getValue();
            // 跳过所有属于本事务的版本
            while (cur != null && cur.trxId == trxId) {
                cur = cur.prev;
            }
            if (cur != null) {
                database.put(entry.getKey(), cur);
            }
        }
        System.out.printf("  [ROLLBACK] %-12s trxId=%-3d  （Undo Log 回滚，旧版本恢复）%n",
                txName, trxId);
    }

    // ==================== MVCC 核心：快照读 ====================

    /**
     * 快照读（Snapshot Read）：普通 SELECT，不加锁
     *
     * · READ_UNCOMMITTED → 直接读版本链头，不走 ReadView
     * · READ_COMMITTED   → 每次创建新 ReadView
     * · REPEATABLE_READ  → 复用传入的 readView（首次创建后固定）
     * · SERIALIZABLE     → 加共享锁（简化为文字说明）
     *
     * 返回：读到的值（找不到可见版本返回 -1）
     */
    static int snapshotRead(long trxId, String key, ReadView readView,
                            IsolationLevel level, String txName) {
        System.out.printf("  [SELECT]   %-12s trxId=%-3d  读 %-8s 隔离级别=%s%n",
                txName, trxId, key, level);

        // ---- READ UNCOMMITTED：无 ReadView，直接读版本链头 ----
        if (level == IsolationLevel.READ_UNCOMMITTED) {
            RowVersion latest = database.get(key);
            int val = (latest == null) ? -1 : latest.value;
            System.out.printf("  [RU]       无 ReadView，直接读最新版本（可能未提交）%s=%d%n",
                    key, val);
            return val;
        }

        // ---- SERIALIZABLE：加 S 锁逻辑（简化模拟） ----
        if (level == IsolationLevel.SERIALIZABLE) {
            System.out.println("  [SER]      申请共享锁 S-Lock...");
            // 检查是否有其他写事务（持有 X 锁）
            Set<Long> others = new HashSet<>(activeTxIds);
            others.remove(trxId);
            if (!others.isEmpty()) {
                System.out.printf("  [SER]      ⚠ 检测到写事务 %s 持有 X 锁，" +
                        "真实 MySQL 会在此阻塞等待！（Demo 继续模拟）%n", others);
            } else {
                System.out.println("  [SER]      无冲突，获得 S-Lock");
            }
            // 串行化也走 ReadView（但实际上因为完全串行，ReadView 意义不大）
        }

        // ---- RC / RR / SERIALIZABLE：走 ReadView 可见性判断 ----
        ReadView rv = readView;
        if (level == IsolationLevel.READ_COMMITTED) {
            // RC：每次 SELECT 重建 ReadView（能看到其他事务最新提交）
            rv = new ReadView(trxId, activeTxIds, trxIdCounter);
            System.out.printf("  [RC]       重新创建 %s%n", rv);
        } else {
            System.out.printf("  [RR/SER]   复用 %s%n", rv);
        }

        // 沿版本链逐一检查可见性
        RowVersion cur = database.get(key);
        int depth = 0;
        while (cur != null) {
            boolean visible = rv.isVisible(cur.trxId);
            System.out.printf("  [版本链]   %-3d %s  → %s  %s%n",
                    ++depth, cur, rv.ruleApplied(cur.trxId), visible ? "✓ 可见" : "✗ 跳过");
            if (visible) {
                System.out.printf("  [READ]     %s=%d  （来自 trxId=%d 的版本）%n",
                        key, cur.value, cur.trxId);
                return cur.value;
            }
            cur = cur.prev;
        }
        System.out.printf("  [READ]     %s 版本链遍历完毕，无可见版本（返回 -1）%n", key);
        return -1;
    }

    /** 创建 ReadView（RR 级别在第一次 SELECT 时调用） */
    static ReadView createReadView(long trxId, String txName) {
        ReadView rv = new ReadView(trxId, activeTxIds, trxIdCounter);
        System.out.printf("  [ReadView] %-12s trxId=%-3d  创建 %s%n", txName, trxId, rv);
        return rv;
    }

    /** 打印版本链 */
    static void printVersionChain(String key) {
        System.out.printf("  [版本链]   %s: ", key);
        RowVersion cur = database.get(key);
        List<String> chain = new ArrayList<>();
        while (cur != null) {
            chain.add(String.format("%d(tx=%d)", cur.value, cur.trxId));
            cur = cur.prev;
        }
        if (chain.isEmpty()) {
            System.out.println("（空）");
        } else {
            System.out.println(String.join(" → ", chain));
        }
    }

    /** 打印账户余额（模拟 SELECT * FROM account） */
    static void printBalance(String label) {
        System.out.printf("  [账户快照] %s  Alice=%s  Bob=%s%n",
                label,
                database.containsKey("Alice") ? database.get("Alice").value : "?",
                database.containsKey("Bob")   ? database.get("Bob").value   : "?");
    }

    /** 分隔线 */
    static void sep(String title) {
        System.out.println();
        System.out.println("  ┌─────────────────────────────────────────────────────┐");
        System.out.printf("  │ %-51s │%n", title);
        System.out.println("  └─────────────────────────────────────────────────────┘");
    }

    /** 重置全局状态，设置初始数据 */
    static void resetData() {
        database.clear();
        activeTxIds.clear();
        committedTxIds.clear();
        trxIdCounter = 1;
        // trxId=0 代表“系统初始化”，写入初始数据
        database.put("Alice", new RowVersion("Alice", ALICE_INIT, 0, null));  // 10000
        database.put("Bob",   new RowVersion("Bob",   BOB_INIT,   0, null));  // 5000
    }

    // ==================== 主程序 ====================

    public static void main(String[] args) {
        header("MVCC + 四种隔离级别完整演示  初始：Alice=" + ALICE_INIT + ", Bob=" + BOB_INIT);

        resetData();
        System.out.println("\n══════════════ 场景1：脏读（READ UNCOMMITTED）══════════════");
        demo_dirtyRead();

        resetData();
        System.out.println("\n══════════════ 场景2：不可重复读（READ COMMITTED）══════════════");
        demo_nonRepeatableRead();

        resetData();
        System.out.println("\n══════════════ 场景3：可重复读（REPEATABLE READ，MySQL 默认）══════════════");
        demo_repeatableRead();

        resetData();
        System.out.println("\n══════════════ 场景4：幻读（RR 下快照读 vs 当前读）══════════════");
        demo_phantomRead();

        resetData();
        System.out.println("\n══════════════ 场景5：串行化（SERIALIZABLE）══════════════");
        demo_serializable();

        resetData();
        System.out.println("\n══════════════ 场景6：ReadView 四条可见性规则完整验证══════════════");
        demo_readViewVisibility();

        resetData();
        System.out.println("\n══════════════ 场景7：转账一致性验证（Alice→Bob " + TRANSFER_AMT + " 元）══════════════");
        demo_transferConsistency();
    }

    static void header(String title) {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.printf( "║  %-56s  ║%n", title);
        System.out.println("╚══════════════════════════════════════════════════════════╝");
    }

    // ==================== 场景实现 ====================

    /**
     * 场景1：脏读（READ UNCOMMITTED）
     *
     * 时间线（竖轴为时间顺序）：
     *
     *   T1  txA BEGIN
     *   T2  txB BEGIN
     *   T3  txA WRITE Alice=8000（转出 2000），但未提交
     *   T4  txB READ  Alice → 读到 8000  ← 脏读！
     *   T5  txA ROLLBACK
     *   T6  txB READ  Alice → 读到 10000（8000 从未真实存在）
     *
     * 问题：txB 基于 8000 做业务决策（如"余额足够，允许转账"），
     *       txA 一旦回滚，txB 的决策就基于了根本不存在的数据。
     */
    static void demo_dirtyRead() {
        sep("时间线：txA 写未提交，txB 用 READ_UNCOMMITTED 读");
        System.out.println("  预期：txB 读到未提交的 " + ALICE_AFTER + "（脏读），txA 回滚后又变回 " + ALICE_INIT);

        long txA = beginTransaction("txA");
        long txB = beginTransaction("txB");

        sep("T3: txA 将 Alice 改为 " + ALICE_AFTER + "（转出 " + TRANSFER_AMT + "），但未提交");
        write(txA, "Alice", ALICE_AFTER);  // 8000
        printVersionChain("Alice");

        sep("T4: txB 读 Alice（READ_UNCOMMITTED = 直接读最新版本）");
        int val1 = snapshotRead(txB, "Alice", null, IsolationLevel.READ_UNCOMMITTED, "txB");
        System.out.println("  ⚡ 脏读！txB 看到 Alice=" + val1 + "，但 txA 还没提交");
        System.out.println("  ⚡ txB 可能据此做出错误的业务决策（如：认为转账已扣款成功）");

        sep("T5: txA 回滚（Undo Log 恢复 Alice=" + ALICE_INIT + "）");
        rollback(txA, "txA");
        printVersionChain("Alice");

        sep("T6: txB 再读 Alice");
        int val2 = snapshotRead(txB, "Alice", null, IsolationLevel.READ_UNCOMMITTED, "txB");
        System.out.println("  第一次读=" + val1 + "，第二次读=" + val2 + "  ← 数据变了！");
        System.out.println("  结论：READ_UNCOMMITTED 存在脏读，txB 之前看到的 " + ALICE_AFTER + " 从未真实提交过");

        commit(txB, "txB");
        System.out.println("\n  [总结] ✗ READ_UNCOMMITTED 存在脏读（生产几乎不用此级别）");
    }

    /**
     * 场景2：不可重复读（READ COMMITTED）
     *
     * RC 级别每次 SELECT 都创建新 ReadView，能看到其他事务最新提交。
     * 好处：消除脏读（只能看到已提交的数据）。
     * 代价：同一事务两次读同一行可能结果不同（不可重复读）。
     *
     * 时间线：
     *   T1  txB BEGIN，第1次读 Alice → 10000
     *   T2  txA BEGIN，修改 Alice=8000，COMMIT
     *   T3  txB 第2次读 Alice → 8000（不可重复读！）
     */
    static void demo_nonRepeatableRead() {
        sep("时间线：txB 两次读 Alice，中间 txA 修改并提交");
        System.out.println("  预期：RC 下两次读到不同的值（不可重复读），但不会出现脏读");

        long txA = beginTransaction("txA");
        long txB = beginTransaction("txB");

        sep("T1: txB 第1次读 Alice（RC：每次 SELECT 创建新 ReadView）");
        // RC 传入 null，snapshotRead 内部自动创建
        int val1 = snapshotRead(txB, "Alice", null, IsolationLevel.READ_COMMITTED, "txB");
        System.out.println("  → txB 第1次读到 Alice=" + val1);

        sep("T2: txA 将 Alice 改为 " + ALICE_AFTER + " 并提交");
        write(txA, "Alice", ALICE_AFTER);  // 8000
        commit(txA, "txA");
        printVersionChain("Alice");

        sep("T3: txB 第2次读 Alice（RC：重新创建 ReadView，能看到 txA 的最新提交）");
        int val2 = snapshotRead(txB, "Alice", null, IsolationLevel.READ_COMMITTED, "txB");
        System.out.println("  → txB 第2次读到 Alice=" + val2);

        System.out.printf("%n  第1次=%d  第2次=%d  结果不同！← 不可重复读%n", val1, val2);
        System.out.println("  原因：RC 每次 SELECT 都重新创建 ReadView，" +
                           "txA 提交后 txB 的新 ReadView 能看到 txA 写入的版本");
        commit(txB, "txB");
        System.out.println("\n  [总结] ✓ RC 解决了脏读（只读已提交）");
        System.out.println("  [总结] ✗ RC 存在不可重复读（每次 SELECT 重建 ReadView）");
    }

    /**
     * 场景3：可重复读（REPEATABLE READ，MySQL 默认）
     *
     * RR 级别：事务第一次 SELECT 时创建 ReadView，之后所有 SELECT 复用同一个。
     * 无论其他事务如何提交，本事务看到的"快照"永远不变。
     *
     * 时间线：
     *   T1  txB BEGIN，第1次读 Alice → 10000（创建 ReadView，m_ids={txA}）
     *   T2  txA 修改 Alice=8000 并提交
     *   T3  txB 第2次读 Alice → 仍然 10000（复用 ReadView，txA 在 m_ids=不可见）
     */
    static void demo_repeatableRead() {
        sep("时间线：txB 两次读 Alice，中间 txA 修改并提交");
        System.out.println("  预期：RR 下两次读到相同的值（可重复读）");

        long txA = beginTransaction("txA");
        long txB = beginTransaction("txB");

        sep("T1: txB 第1次读 Alice（RR：创建 ReadView，之后固定不变）");
        ReadView rv = createReadView(txB, "txB");
        int val1 = snapshotRead(txB, "Alice", rv, IsolationLevel.REPEATABLE_READ, "txB");
        System.out.println("  → txB 第1次读到 Alice=" + val1);

        sep("T2: txA 将 Alice 改为 " + ALICE_AFTER + " 并提交");
        write(txA, "Alice", ALICE_AFTER);  // 8000
        commit(txA, "txA");
        printVersionChain("Alice");

        sep("T3: txB 第2次读 Alice（RR：复用同一个 ReadView）");
        System.out.println("  txA.trxId=" + (trxIdCounter - 2) + " 在 m_ids=" + rv.mIds +
                           " 中，所以 txA 写入的 val=" + ALICE_AFTER + " 对 txB 不可见");
        int val2 = snapshotRead(txB, "Alice", rv, IsolationLevel.REPEATABLE_READ, "txB");
        System.out.println("  → txB 第2次读到 Alice=" + val2);

        System.out.printf("%n  第1次=%d  第2次=%d  结果相同！← 可重复读%n", val1, val2);
        System.out.println("  原因：RR 复用同一个 ReadView，txA 在 m_ids 中=未提交=不可见");
        commit(txB, "txB");
        System.out.println("\n  [总结] ✓ RR 解决了脏读和不可重复读");
        System.out.println("  [总结]   原理：ReadView 首次创建后固定，" +
                           "其他事务的提交对本 ReadView 不可见");
    }

    /**
     * 场景4：幻读（RR 下快照读 vs 当前读）
     *
     * 幻读：同一事务两次查询，第二次出现了第一次没有的行（或消失了行）。
     *
     * RR 下快照读（普通 SELECT）：ReadView 固定，天然避免幻读
     * RR 下当前读（SELECT FOR UPDATE 等）：读最新版本，需要 Gap Lock 防幻读
     */
    static void demo_phantomRead() {
        System.out.println("\n----- 情况A：快照读（普通 SELECT），RR 完全避免幻读 -----");
        {
            long txA = beginTransaction("txA");
            long txB = beginTransaction("txB");

            sep("T1: txB 快照读，统计余额 > 5000 的账户（此时只有 Alice=" + ALICE_INIT + "）");
            ReadView rv = createReadView(txB, "txB");
            int aliceVal = snapshotRead(txB, "Alice", rv, IsolationLevel.REPEATABLE_READ, "txB");
            System.out.println("  txB 看到 Alice=" + aliceVal + "（满足>5000），共 1 个账户");

            sep("T2: txA 新增 Charlie=8800 并提交（幻影行）");
            // 注意：Charlie 由 txA 写入（trxId=txA），txA 在 rv.mIds 中
            database.put("Charlie", new RowVersion("Charlie", 8800, txA, null));
            commit(txA, "txA");
            System.out.println("  Charlie 已插入且 txA 已提交");

            sep("T3: txB 再次快照读 Charlie");
            // txA 的 trxId 在 rv.mIds 中 → 不可见
            int charlieVal = snapshotRead(txB, "Charlie", rv, IsolationLevel.REPEATABLE_READ, "txB");
            System.out.println("  txB 看到 Charlie=" + charlieVal +
                    "（-1表示不可见，txA 在 m_ids 中）");
            System.out.println("  → 快照读下 txB 看到的仍是 1 个账户，无幻读 ✓");
            System.out.println("  原因：ReadView 固定，txA 的新插入对 txB 不可见");

            commit(txB, "txB");
            database.remove("Charlie");
        }

        System.out.println("\n----- 情况B：当前读（SELECT FOR UPDATE），无 Gap Lock 时幻读 -----");
        {
            long txA = beginTransaction("txA");
            long txB = beginTransaction("txB");

            sep("T1: txB 当前读（SELECT FOR UPDATE）统计余额 > 5000 的账户");
            System.out.println("  [当前读] 读最新已提交版本，并加 Next-Key Lock");
            System.out.println("  txB 当前读看到：Alice=" + ALICE_INIT + "，共 1 个（锁定 Alice 行及其间隙）");

            sep("T2: txA 在间隙插入 Charlie=8800 并提交（模拟无 Gap Lock 的情况）");
            database.put("Charlie", new RowVersion("Charlie", 8800, txA, null));
            commit(txA, "txA");
            System.out.println("  真实 MySQL RR 下：txA 的插入会被 Gap Lock 阻塞！");
            System.out.println("  这里模拟未正确加 Gap Lock 的情况（如旧版本 MySQL 或 Bug）");

            sep("T3: txB 再次当前读");
            System.out.println("  [当前读] 不走 ReadView，直接读最新已提交版本");
            System.out.println("  txB 当前读看到：Alice=" + ALICE_INIT + ", Charlie=8800，共 2 个！");
            System.out.println("  → 第一次1个，第二次2个 → 幻读！✗");

            commit(txB, "txB");
            database.remove("Charlie");
        }

        System.out.println();
        System.out.println("  [总结] ✓ RR 快照读（普通 SELECT）：ReadView 固定，不会幻读");
        System.out.println("  [总结] ✓ RR 当前读（SELECT FOR UPDATE）：" +
                           "依靠 Gap Lock + Next-Key Lock 防幻读");
        System.out.println("  [总结]   真实 MySQL 默认 RR 级别已通过以上机制解决了幻读问题");
    }

    /**
     * 场景5：串行化（SERIALIZABLE）
     *
     * 所有 SELECT 加 S 锁，所有写操作加 X 锁。
     * S 锁与 X 锁互斥 → 读写串行，彻底消除所有并发异常。
     * 代价：高并发下退化为单线程，吞吐量极低。
     */
    static void demo_serializable() {
        System.out.println("  SERIALIZABLE：SELECT → S 锁，INSERT/UPDATE/DELETE → X 锁");
        System.out.println("  S-X 互斥，并发事务必须等待对方释放锁");

        long txA = beginTransaction("txA(写)");
        long txB = beginTransaction("txB(读)");

        sep("T1: txA 执行 UPDATE Alice=" + ALICE_AFTER + "（持有 X 锁）");
        write(txA, "Alice", ALICE_AFTER);  // 8000
        System.out.println("  txA 持有 Alice 的 X 锁，其他事务的 S/X 锁均需等待");

        sep("T2: txB 执行 SELECT Alice（需要 S 锁，被 X 锁阻塞）");
        // 传入一个临时 ReadView（但 SERIALIZABLE 模式下会检测到冲突）
        ReadView tempRv = createReadView(txB, "txB");
        snapshotRead(txB, "Alice", tempRv, IsolationLevel.SERIALIZABLE, "txB");
        System.out.println("  真实 MySQL：txB 会在此处阻塞，直到 txA COMMIT 释放 X 锁");
        System.out.println("  Demo 继续模拟后续流程...");

        sep("T3: txA COMMIT，释放 X 锁");
        commit(txA, "txA(写)");

        sep("T4: txB 获得 S 锁，读 Alice");
        ReadView rv2 = createReadView(txB, "txB");
        int val = snapshotRead(txB, "Alice", rv2, IsolationLevel.SERIALIZABLE, "txB");
        System.out.println("  → txB 读到 Alice=" + val + "（txA 提交后的值）");
        commit(txB, "txB(读)");

        System.out.println("\n  [总结] ✓ SERIALIZABLE 彻底消除脏读、不可重复读、幻读");
        System.out.println("  [总结] ✗ 性能最差：高并发下退化为串行执行，吞吐量极低");
        System.out.println("  [总结]   适用场景：对数据一致性要求极高且并发量低的场景");
    }

    /**
     * 场景6：ReadView 四条可见性规则完整验证
     *
     * 精心构造事务执行顺序，逐一触发四条规则：
     *   规则①: trxId == creatorTrxId → 自己写的，可见
     *   规则②: trxId <  min_trx_id   → 老已提交版本，可见
     *   规则③: trxId >= max_trx_id   → 未来事务，不可见
     *   规则④: min <= trxId < max：在 m_ids=不可见，不在=可见
     */
    static void demo_readViewVisibility() {
        System.out.println("  构造4个事务，覆盖 ReadView 的4条可见性规则");

        // txOld 先提交（trxId=1），代表"老的已提交版本"，用于验证规则②
        sep("准备：txOld 写入 Alice=" + ALICE_INIT + " 并提交（老已提交版本，验证规则②）");
        long txOld = beginTransaction("txOld");
        write(txOld, "Alice", ALICE_INIT);  // 10000
        commit(txOld, "txOld");

        // 启动三个并发事务
        sep("启动 txA, txB, txC 三个并发事务");
        long txA = beginTransaction("txA");
        long txB = beginTransaction("txB(创ReadView)");
        long txC = beginTransaction("txC");

        // txA 写 ALICE_AFTER(8000)：和 10000 差距明显，在 m_ids 中=不可见
        sep("txA 写 Alice=" + ALICE_AFTER + "（未提交，验证规则④：在 m_ids 中=不可见）");
        write(txA, "Alice", ALICE_AFTER);  // 8000

        // txC 写 9000：和 10000/8000 都不同，不在 m_ids 中=可见
        sep("txC 写 Alice=9000 并提交（验证规则④：不在 m_ids 中=可见）");
        write(txC, "Alice", 9000);
        commit(txC, "txC");

        sep("txB 创建 ReadView（此时活跃事务：txA, txB；txC 已提交，不在 m_ids）");
        ReadView rv = createReadView(txB, "txB");
        System.out.println("  m_ids=" + rv.mIds + "（只有 txA，txC 已提交故不在其中）");

        sep("txB 自己写一个版本（验证规则①：自己写的=可见）");
        // 先把 txB 自己的版本放到 Bob 上，不影响 Alice 的验证
        // 5999：和 BOB_INIT(5000)、BOB_AFTER(7000) 明显不同，看到能立即区分
        write(txB, "Bob", 5999);
        System.out.println("  txB 读 Bob（期望看到自己写的 5999）");
        int bobVal = snapshotRead(txB, "Bob", rv, IsolationLevel.REPEATABLE_READ, "txB");
        System.out.println("  → txB 读到 Bob=" + bobVal + "（期望 5999）  ← 规则①：自己写的 ✓");

        sep("验证规则③：txFuture 在 txB 创建 ReadView 之后开启，txB 不应看到它的写入");
        // txFuture 写 7777：和 10000/9000/8000 明显不同，能立即识别"不应被 txB 读到"
        long txFuture = beginTransaction("txFuture");
        write(txFuture, "Alice", 7777);
        System.out.println("  txFuture.trxId=" + txFuture + " >= maxTrxId=" + rv.maxTrxId +
                           "，txB 不应看到 7777（规则③）");
        // txFuture 保持未提交（注意：这里为了demo不commit txFuture，确保它在活跃集合里）

        sep("txB 读 Alice，沿版本链验证四条规则");
        printVersionChain("Alice");
        System.out.println("  版本链头到尾：7777(txFuture) → 9000(txC) → 8000(txA) → 10000(txOld) → 10000(trx0)");
        int aliceVal = snapshotRead(txB, "Alice", rv, IsolationLevel.REPEATABLE_READ, "txB");
        System.out.println("  → txB 最终读到 Alice=" + aliceVal);

        System.out.println();
        System.out.println("  ┌─────────────────────────────────────────────────────────────┐");
        System.out.println("  │ 规则验证总结                                                 │");
        System.out.printf( "  │ 规则① creatorTrxId=%d，txB 写 Bob=5999 自己可见      ✓      │%n", txB);
        System.out.printf( "  │ 规则② txOld.trxId=%d < min=%d，Alice=%d 可见  ✓      │%n",
                txOld, rv.minTrxId, ALICE_INIT);
        System.out.printf( "  │ 规则③ txFuture.trxId=%d >= max=%d，Alice=7777 不可见 ✓      │%n",
                txFuture, rv.maxTrxId);
        System.out.printf( "  │ 规则④ txA.trxId=%d 在 m_ids → 不可见（%d）      ✓      │%n", txA, ALICE_AFTER);
        System.out.printf( "  │ 规则④ txC.trxId=%d 不在 m_ids → 可见（9000）        ✓      │%n", txC);
        System.out.println("  └─────────────────────────────────────────────────────────────┘");

        // 清理
        commit(txFuture, "txFuture");
        commit(txA, "txA");
        commit(txB, "txB");
    }

    /**
     * 场景7：转账一致性验证（Alice → Bob 转账 TRANSFER_AMT 元）
     *
     * 演示在 RR 隔离级别下，一个读事务（auditor）在转账进行中和完成后看到的数据：
     *   · 读事务应始终看到一致的快照（总和不变）
     *   · 不会出现"Alice 扣了但 Bob 没收到"的中间状态
     *
     * 时间线：
     *   T1  auditor BEGIN，第1次读 Alice+Bob 总额（= ALICE_INIT + BOB_INIT = 15000）
     *   T2  txTransfer BEGIN，扣 Alice TRANSFER_AMT，加 Bob TRANSFER_AMT
     *   T3  auditor 第2次读（txTransfer 未提交）→ 总额不变（RR 快照隔离）
     *   T4  txTransfer COMMIT
     *   T5  auditor 第3次读（txTransfer 已提交）→ 总额不变（RR 复用 ReadView）
     *   T6  auditor COMMIT，新事务读 → 总额不变，但单项变为 Alice=8000 Bob=7000
     */
    static void demo_transferConsistency() {
        System.out.println("  转账场景：Alice → Bob 转账 " + TRANSFER_AMT + " 元（Alice: "
                + ALICE_INIT + "→" + ALICE_AFTER + "，Bob: " + BOB_INIT + "→" + BOB_AFTER + "）");
        System.out.println("  验证：审计事务（auditor）在 RR 下看到的总额始终一致");

        long auditor     = beginTransaction("auditor");
        long txTransfer  = beginTransaction("txTransfer");

        sep("T1: auditor 第1次读（RR：创建 ReadView，快照固定）");
        ReadView rv = createReadView(auditor, "auditor");
        int aliceV1 = snapshotRead(auditor, "Alice", rv, IsolationLevel.REPEATABLE_READ, "auditor");
        int bobV1   = snapshotRead(auditor, "Bob",   rv, IsolationLevel.REPEATABLE_READ, "auditor");
        int total1  = aliceV1 + bobV1;
        System.out.printf("  auditor 看到：Alice=%d, Bob=%d, 总额=%d（期望 %d）%n",
                aliceV1, bobV1, total1, ALICE_INIT + BOB_INIT);

        sep("T2: txTransfer 执行转账（Alice-" + TRANSFER_AMT + ", Bob+" + TRANSFER_AMT + "，未提交）");
        write(txTransfer, "Alice", ALICE_AFTER);  // 10000 - 2000 = 8000
        write(txTransfer, "Bob",   BOB_AFTER);    // 5000  + 2000 = 7000
        printVersionChain("Alice");
        printVersionChain("Bob");
        System.out.println("  转账已写入，但 txTransfer 未提交（其他事务不应看到中间状态）");

        sep("T3: auditor 第2次读（RR：复用同一 ReadView，txTransfer 在 m_ids 中不可见）");
        int aliceV2 = snapshotRead(auditor, "Alice", rv, IsolationLevel.REPEATABLE_READ, "auditor");
        int bobV2   = snapshotRead(auditor, "Bob",   rv, IsolationLevel.REPEATABLE_READ, "auditor");
        int total2  = aliceV2 + bobV2;
        System.out.printf("  auditor 看到：Alice=%d, Bob=%d, 总额=%d%n", aliceV2, bobV2, total2);
        System.out.println("  ✓ 总额不变（RR 隔离了未提交的转账）");

        sep("T4: txTransfer COMMIT（转账成功）");
        commit(txTransfer, "txTransfer");

        sep("T5: auditor 第3次读（RR：仍复用同一 ReadView）");
        int aliceV3 = snapshotRead(auditor, "Alice", rv, IsolationLevel.REPEATABLE_READ, "auditor");
        int bobV3   = snapshotRead(auditor, "Bob",   rv, IsolationLevel.REPEATABLE_READ, "auditor");
        int total3  = aliceV3 + bobV3;
        System.out.printf("  auditor 看到：Alice=%d, Bob=%d, 总额=%d%n", aliceV3, bobV3, total3);
        System.out.println("  ✓ 虽然 txTransfer 已提交，RR 下 auditor 的 ReadView 不变，" +
                           "看不到新版本");

        sep("T6: auditor COMMIT，新事务读（看到转账后的最终状态）");
        commit(auditor, "auditor");

        long newReader = beginTransaction("newReader");
        ReadView rvNew = createReadView(newReader, "newReader");
        int aliceNew = snapshotRead(newReader, "Alice", rvNew, IsolationLevel.REPEATABLE_READ, "newReader");
        int bobNew   = snapshotRead(newReader, "Bob",   rvNew, IsolationLevel.REPEATABLE_READ, "newReader");
        int totalNew = aliceNew + bobNew;
        System.out.printf("  newReader 看到：Alice=%d, Bob=%d, 总额=%d（期望 Alice=%d Bob=%d 总=%d）%n",
                aliceNew, bobNew, totalNew, ALICE_AFTER, BOB_AFTER, ALICE_AFTER + BOB_AFTER);
        System.out.println("  ✓ 新事务能看到转账结果（Alice 扣 " + TRANSFER_AMT
                + "，Bob 加 " + TRANSFER_AMT + "，总额不变）");
        commit(newReader, "newReader");

        int totalExpected = ALICE_INIT + BOB_INIT;  // 15000
        System.out.println();
        System.out.printf("  ┌─────────────────────────────────────────────────────┐%n");
        System.out.printf("  │ 转账一致性验证汇总（转账金额 %d，总额恒为 %d）         │%n",
                TRANSFER_AMT, totalExpected);
        System.out.printf("  │ T1 快照（转账前）：Alice=%-5d Bob=%-5d 总额=%-5d │%n",
                aliceV1, bobV1, total1);
        System.out.printf("  │ T3 快照（转账中）：Alice=%-5d Bob=%-5d 总额=%-5d │%n",
                aliceV2, bobV2, total2);
        System.out.printf("  │ T5 快照（提交后）：Alice=%-5d Bob=%-5d 总额=%-5d │%n",
                aliceV3, bobV3, total3);
        System.out.printf("  │ T6 新事务快照  ：Alice=%-5d Bob=%-5d 总额=%-5d │%n",
                aliceNew, bobNew, totalNew);
        System.out.printf("  │ ✓ auditor 三次读到的总额完全一致（MVCC 快照保证）   │%n");
        System.out.printf("  │ ✓ 新事务正确看到转账结果（ReadView 创建时机决定）   │%n");
        System.out.printf("  └─────────────────────────────────────────────────────┘%n");

        System.out.println("\n  [总结] MVCC 通过版本链 + ReadView 保证：");
        System.out.println("         1. 读不阻塞写，写不阻塞读（高并发）");
        System.out.println("         2. 事务内多次读取结果一致（RR 级别）");
        System.out.println("         3. 不会看到并发事务的中间状态（快照隔离）");
    }
}

