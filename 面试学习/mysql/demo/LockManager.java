import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================
 * LockManager —— InnoDB 锁管理器模拟
 * ============================================================
 *
 * 职责：维护一张"全局锁表"，记录所有事务当前持有的锁，
 * 提供加锁（acquireLock）和解锁（releaseLocks）两个接口。
 *
 * ★ 面试怎么说（InnoDB 锁体系的两层结构，必背）：
 *
 *   第一层：表级意向锁（Intention Lock）—— 不阻塞行锁，只是"占位通知"
 *     IS（Intention Shared，意向共享锁）：
 *       加行 S 锁之前，InnoDB 自动在表上加 IS。
 *       作用：告诉 LOCK TABLE "这张表里有行要被共享读，你别给我加表 X 锁"。
 *     IX（Intention Exclusive，意向排他锁）：
 *       加行 X 锁/Gap 锁之前，InnoDB 自动在表上加 IX。
 *       作用：告诉 LOCK TABLE "这张表里有行要被独占写，你别给我加表 S 锁或 X 锁"。
 *     IS 和 IX 之间互相兼容（多个事务都可以同时持有），
 *     只和 Table S / Table X 冲突（LOCK TABLES 的场景，日常业务几乎不触发）。
 *
 *   第二层：行级锁（Row-Level Lock）—— 锁的是索引记录，不是物理行
 *     Record Lock（行锁）：锁住单条索引记录，S 锁允许并发读，X 锁独占。
 *     Gap Lock（间隙锁）：锁住两条记录之间的"间隙"（不含端点），防止其他事务往里插数据。
 *                         只在 RR（REPEATABLE READ）隔离级别生效，RC 不用 Gap Lock。
 *     Next-Key Lock = Record Lock + 前面的 Gap Lock，是 RR 级别 UPDATE/DELETE 的默认策略。
 *
 * ★ 面试高频问：什么操作加什么锁？
 *   SELECT ... FOR SHARE  → IS（表） + Record S Lock（行）
 *   SELECT ... FOR UPDATE → IX（表） + Record X Lock（行） + Gap Lock（间隙，RR 级别）
 *   UPDATE / DELETE       → IX（表） + Record X Lock（行） + Gap Lock（间隙，RR 级别）
 *   普通 SELECT           → 不加任何锁！通过 MVCC 读历史版本（快照读）
 *
 * ★ 面试高频问：RC 和 RR 在加锁上的区别？
 *   "RC 不加 Gap Lock，所以 RC 会有幻读（两次查范围结果不同）；
 *    RR 加 Gap Lock，防止了幻读，但 Gap Lock 范围更大，死锁概率也更高。"
 *
 * Demo 简化说明：
 *   只模拟单事务加锁，不模拟多事务锁等待（Lock Wait）和死锁检测（Deadlock Detection）。
 *   行锁绑定到主键 key（账户名），等同于真实 PRIMARY KEY 上的记录锁。
 */
public class LockManager extends MysqlDemoBase {

    /**
     * 全局锁表：记录所有事务当前持有的锁（按加锁顺序排列）
     *
     * 真实 InnoDB lock_sys：
     *   用哈希表，key = page_id（space_id + page_no），value = 该页上所有锁的链表。
     *   行锁信息存在 lock_t.heap_no 位图中（每一位对应页内的一行）。
     *   并发访问由 lock_sys->mutex 保护（MySQL 8.0 拆分为 sharded 读写锁降低竞争）。
     *
     * Demo 简化：用 List 按加锁顺序记录，事务结束时 removeIf 批量释放。
     */
    static List<LockEntry> lockManager = new ArrayList<>();

    /**
     * 加锁（模拟 InnoDB 的 lock_rec_lock / lock_table 系列函数）
     *
     * ★ 面试知识点（加锁的底层原理）：
     *   "InnoDB 的行锁锁的不是物理行，而是索引记录上的锁。
     *    如果查询走了全表扫描（没有合适索引），会把所有扫描到的行都加锁，
     *    性能非常差，所以 UPDATE/DELETE 的 WHERE 条件一定要走索引。"
     *
     * ★ 面试知识点（Gap Lock 的范围怎么确定）：
     *   "Gap Lock 锁的是'当前命中记录之前的间隙'。
     *    比如表里有 Alice=10000 和 Bob=5000，
     *    UPDATE WHERE name='Bob' 会加 Gap Lock(-∞, Bob)，
     *    阻止其他事务在 Alice 和 Bob 之间插入新行（防幻读）。"
     *
     * @param txId        持锁的事务 ID
     * @param type        锁类型（见 LockEntry.LockType）
     * @param resource    锁资源（表名 / 行 key / 间隙描述）
     * @param isTableLock true = 表级意向锁；false = 行级锁
     */
    static void acquireLock(long txId, LockEntry.LockType type, String resource, boolean isTableLock) {
        LockEntry lock = new LockEntry(txId, type, resource, isTableLock);
        lockManager.add(lock);
        System.out.println("  [LockMgr]    加锁: " + lock);
    }

    /**
     * 释放事务的所有锁（COMMIT / ROLLBACK 时调用）
     *
     * ★ 面试知识点（锁什么时候释放）：
     *   "InnoDB 遵循两阶段锁协议（2PL）：加锁阶段和解锁阶段严格分开。
     *    事务执行期间只加锁不释放，直到 COMMIT 或 ROLLBACK 完成后统一释放所有锁。
     *    这样才能保证事务的隔离性（否则锁提前释放，其他事务可能读到中间状态）。
     *    锁释放后，MySQL 会唤醒所有在等待这些锁的事务，让它们继续执行。"
     *
     * ★ 面试知识点（Gap Lock 释放后的影响）：
     *   "Gap Lock 在事务提交后释放，释放后就不再阻止其他事务往那个间隙里插数据了。
     *    所以 Gap Lock 只是'这个事务执行期间'的防幻读，不是永久的。"
     *
     * @param txId 要释放锁的事务 ID
     */
    static void releaseLocks(long txId) {
        long count = lockManager.stream().filter(l -> l.txId == txId).count();
        lockManager.removeIf(l -> l.txId == txId);
        System.out.println("  [LockMgr]    释放 tx=" + txId
                + " 持有的全部锁（共 " + count + " 个），等待此锁的事务现在可继续执行");
    }

    /**
     * 清空全局锁表（场景切换时调用，防止上一个场景的锁状态影响下一个场景）
     */
    static void clearAll() {
        lockManager.clear();
    }
}

