import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * ============================================================
 * InnoDB 如何保证原子性？—— 更接近真实底层的转账场景模拟
 * ============================================================
 *
 * 【磁盘文件说明（本 Demo 真实写入 disk/ 目录）】
 *   disk/data.ibd          ── 模拟数据文件（.ibd），文本 key=val 格式，每行一条
 *                             真实：16KB 数据页，紧凑二进制，有页头/行格式/页目录
 *   disk/undo_001.ibu      ── 模拟 Undo 表空间文件，文本格式，每行一条 Undo Record
 *                             真实：Rollback Segment → Undo Page（16KB 二进制页）
 *   disk/ib_logfile0       ── 模拟环形 Redo Log 文件，二进制格式（每条 64B 定长 block）
 *                             真实：ib_logfile0/1 轮换循环写，每 block 512B（对齐扇区）
 *   disk/ib_logfile_header ── 模拟日志文件头，记录 checkpoint_lsn 和 write_head
 *                             真实：文件头 512B 含 LOG_CHECKPOINT_1/2 两个 checkpoint 页
 *
 * 【与真实 InnoDB 的整体差异说明】
 *   本 Demo 为演示目的做了以下简化，代码内每处也有对应注释：
 *   ① 数据粒度：真实操作的是 16KB 数据页，Demo 用文本 key=val 行代替
 *   ② Undo Log 存储：真实分层结构（Rollback Seg → Undo Seg → Undo Page），Demo 用文本行
 *   ③ Log Buffer（内存）：真实是一块连续内存（默认16MB）按 512B Log Block 对齐，不是环形；
 *                         Demo 用对象数组模拟，写满触发 flush
 *   ④ ib_logfile（磁盘）：真实是环形循环写两个物理文件，按 512B block 追加；
 *                          Demo 用二进制文件 + 64B 定长 block 模拟环形（head 取模 CAPACITY）
 *   ⑤ LSN：真实是按写入字节数单调递增，Demo 每条日志 +1 代替
 *   ⑥ 并发：真实有锁、MVCC、trx_sys 等并发控制，Demo 单线程
 *   ⑦ Purge 线程：真实异步回收 Undo Log，Demo 仅打印说明
 *
 * 【Undo Log 真实结构（面试考点）】
 *   - 存储位置：ibdata1（系统表空间）或独立 undo tablespace（MySQL 8.0 默认独立文件 undo_001.ibu）
 *   - 物理结构：Rollback Segment（128个）→ Undo Segment → Undo Page（16KB）→ Undo Record
 *     每个事务从某个 Rollback Segment 分配一个 Undo Segment
 *   - 记录类型（真实源码中的类型常量）：
 *     TRX_UNDO_UPD_EXIST_REC  —— UPDATE 已有行（本 Demo 模拟的类型）
 *     TRX_UNDO_DEL_MARK_REC   —— DELETE（标记删除，不立即物理删除，purge 线程延迟清理）
 *     TRX_UNDO_INSERT_REC     —— INSERT（回滚时直接物理删除该行）
 *   - 每条 Undo Record 包含：undo_type / undo_no / table_id / 旧列值 / roll_pointer
 *   - roll_pointer（7字节）：编码了 Rollback Segment ID + Undo Page 页号 + 页内偏移，
 *     形成版本链，MVCC 沿此链向前读历史版本；Demo 用 List index 简化
 *   - 生命周期：事务提交后不立即删除，purge 线程确认所有活跃事务的 ReadView 都不再需要后才清理
 *   - 双重用途：① ROLLBACK 回滚  ② MVCC 快照读版本链
 *   - Undo Log 自身的变更也会产生 Redo Log（保证 Undo Log 本身在崩溃后可恢复）
 *
 * 【Redo Log 真实结构（面试考点）】
 *   内存侧（Log Buffer）：
 *     - 大小：innodb_log_buffer_size，默认 16MB（MySQL 8.0）
 *     - 结构：一块连续内存，按 Log Block（512B，对齐扇区）组织；不是环形
 *     - flush 时机：① COMMIT（innodb_flush_log_at_trx_commit=1）② Log Buffer 满 1/2
 *                   ③ 后台线程每秒 flush ④ 脏页刷盘前（WAL 约束）
 *
 *   磁盘侧（ib_logfile）：
 *     - 文件：ib_logfile0、ib_logfile1（默认 2 个，每个默认 48MB，MySQL 8.0 改为自动扩容）
 *     - 结构：环形循环写（ib_logfile0 写满 → ib_logfile1 → 回头覆盖 ib_logfile0）
 *     - 覆盖条件：被覆盖位置的 LSN <= checkpoint_lsn（对应数据页已安全落盘）
 *     - Log Block（512B）：含 block_header（12B）+ 日志内容（496B）+ block_trailer（4B）
 *     - 每条 Log Record：type / space_id / page_no / offset / 新值（物理日志，非逻辑日志）
 *
 *   LSN（Log Sequence Number）：
 *     - 全局单调递增的字节偏移量（真实从 8704 开始，对应文件头大小）
 *     - current_lsn         ：Log Buffer 中已写入的最新位置
 *     - flushed_to_disk_lsn ：已 fsync 到 ib_logfile 的位置
 *     - checkpoint_lsn      ：已刷盘数据页对应的最大 LSN，这之前的日志文件空间可覆盖复用
 *     - page.newest_modification：某数据页最后一次被修改的 LSN（WAL 约束的关键）
 *
 *   innodb_flush_log_at_trx_commit（面试高频！）：
 *     0 = 后台线程每秒 write+fsync（性能最好，MySQL崩溃或OS崩溃都可能丢最近1秒）
 *     1 = 每次 COMMIT 都 write+fsync（最安全，默认值，金融场景标配）
 *     2 = 每次 COMMIT write 到 OS cache，后台线程每秒 fsync
 *         （MySQL进程崩溃不丢，OS/机器崩溃丢最近1秒）
 *
 *   WAL（Write-Ahead Logging）核心约束：
 *     数据页落盘前，该页的 newest_modification 对应的 Redo Log 必须已 fsync
 *     → 保证重放日志能还原数据，不会出现"日志没了但数据页不完整"的情况
 *
 * 【原子性保证路径】
 *   BEGIN
 *     → 写 Undo Log（先于数据修改，记录旧值+roll_pointer，为 Undo Log 本身写 Redo）
 *     → 修改 Buffer Pool 数据页（脏页，page.newest_modification = 当前LSN）
 *     → 写 Redo Log Buffer（物理日志+LSN）
 *   COMMIT
 *     → 写 Redo COMMIT 标记 → fsync Log Buffer 到 ib_logfile → 返回客户端成功
 *     → 脏页由后台 checkpoint 线程异步刷盘（不阻塞 COMMIT）
 *   ROLLBACK
 *     → 逆序读 Undo Log，按 roll_pointer 链逐条撤销（同时写 Redo）
 *   崩溃恢复（重启自动）
 *     → Redo Phase：从 checkpoint_lsn 重放已落盘日志（恢复已提交事务的数据页）
 *     → Undo Phase：用 Undo Log 回滚所有未提交事务（Undo Log 自身由第一步中的 Redo 恢复）
 */
public class InnoDBAtomicityDemo {

    // ==================== 磁盘文件路径（真实写入本地文件）====================

    /**
     * disk/ 目录的绝对路径（和 InnoDBAtomicityDemo.java 同级）
     * 直接写死，不依赖 Class 文件位置（IDE/命令行编译输出目录不同，Class 路径不可靠）
     * 真实 MySQL 的数据目录由 --datadir 参数指定
     */
    static final String DISK_DIR =
            "/Users/qiankun96/Desktop/面试/AIGC-Interview-Book/面试学习/mysql/demo/disk";

    /**
     * 数据文件，模拟 .ibd（InnoDB 数据表空间文件）
     * 格式：每行 "key=value\n"，每次写盘全量覆盖
     *
     * 真实 .ibd：16KB 二进制数据页，有页头(38B)/行格式/页目录/Free Space 等复杂结构
     * 【与真实差异】Demo 用简单文本行代替，不区分页粒度
     */
    static final String DATA_FILE = DISK_DIR + "/data.ibd";

    /**
     * Undo 表空间文件，模拟 undo_001.ibu（MySQL 8.0 独立 Undo 文件）
     * 格式：每行 "txId|undoNo|type|key|oldValue|rollPtr\n"，追加写
     *
     * 真实 undo_001.ibu：同样是 16KB Undo Page，嵌套在 Rollback Segment 结构中
     * 【与真实差异】Demo 用文本行追加写，不模拟 Rollback Segment / Undo Segment 分层
     */
    static final String UNDO_FILE = DISK_DIR + "/undo_001.ibu";

    /**
     * Redo Log 文件，模拟 ib_logfile0（环形循环写）
     * 格式：定长二进制块，每块 REDO_BLOCK_SIZE 字节
     *   offset 0  (8B): LSN (long)
     *   offset 8  (8B): txId (long)
     *   offset 16 (4B): flags: bit0=isCommit, bit1=isFlushed
     *   offset 20 (32B): key (UTF-8, 右填 0)
     *   offset 52 (4B): newValue (int)
     *   offset 56 (8B): 预留/padding
     * 环形：文件最多 REDO_FILE_CAPACITY 个块，写满后 head 取模回绕
     *
     * 真实 ib_logfile：每块 512B（= 1 磁盘扇区，保证原子写），有 block header/trailer 和 CRC32
     * 【与真实差异】Demo 块大小 64B（演示方便），无 CRC；真实 512B；写入用 FileChannel 直接 IO
     */
    static final String REDO_FILE = DISK_DIR + "/ib_logfile0";
    static final int REDO_BLOCK_SIZE = 64;    // 演示用，真实 512B（= 1 磁盘扇区）
    static final int REDO_FILE_CAPACITY = 128; // 最多 128 个 block，演示用，真实 2×48MB

    /**
     * Redo Log 文件头，记录 checkpoint_lsn 和环形写头指针 write_head
     * 格式：文本 "checkpoint_lsn=N\nwrite_head=N\n"
     *
     * 真实：文件头 4096B，含两个交替写的 LOG_CHECKPOINT_1/2 页，每页含 LSN + CRC32
     * 【与真实差异】Demo 用文本记录，无 CRC，不模拟双 checkpoint 页交替写
     */
    static final String REDO_HEADER_FILE = DISK_DIR + "/ib_logfile_header";

    // ==================== 内存状态 ====================

    /**
     * Buffer Pool（内存缓冲池）
     * 真实 InnoDB：innodb_buffer_pool_size 控制大小（默认128MB），16KB 数据页为单元，
     *   free list / flush list / LRU list 三条链管理，young/old 5:3 分区避免大查询污染热页
     * 【与真实差异】Demo 用 Map<key,int> 代替，不区分页，不模拟 LRU 淘汰
     */
    static Map<String, Integer> bufferPool = new HashMap<>();

    /**
     * Undo Log 内存副本（从 undo_001.ibu 加载，写时同步落盘）
     * 真实：Undo Page 也在 Buffer Pool 中缓冲，修改 Undo Page 也产生 Redo Log（Redo for Undo）
     * 【与真实差异】Demo 用 List，写时直接追加到 undo_001.ibu 文件
     */
    static List<UndoLogRecord> undoLog = new ArrayList<>();

    /**
     * Log Buffer（内存 Redo Log 缓冲区）
     * 真实：连续内存 16MB，按 512B Log Block 对齐；不是环形；写满触发 flush
     * flush 时机：① COMMIT ② 占用超 1/2 ③ 后台每秒 ④ 脏页刷盘前
     * 【与真实差异】Demo 用对象数组（LOG_BUFFER_CAPACITY 个槽），logBufPos 模拟 buf_free 指针
     */
    static final int LOG_BUFFER_CAPACITY = 64;
    static RedoLogRecord[] logBuffer = new RedoLogRecord[LOG_BUFFER_CAPACITY];
    static int logBufPos = 0;

    // ==================== LSN 相关全局变量 ====================

    /**
     * currentLsn：当前已写入 Log Buffer 的最新 LSN
     * 真实：全局字节偏移，从约 8704 开始；每条 Record 按实际字节数增长
     * 【与真实差异】Demo 每写一条 +1
     */
    static long currentLsn = 0;

    /**
     * flushedToDisklsn：已 fsync 到 ib_logfile0 的最大 LSN
     * 真实：对应 log_sys->flushed_to_disk_lsn
     * 不变式：checkpoint_lsn <= flushed_to_disk_lsn <= current_lsn
     */
    static long flushedToDisklsn = 0;

    /**
     * checkpointLsn：已刷盘数据页对应的最大 LSN
     * 这之前的 ib_logfile 空间可被循环覆盖
     * 真实：写在 ib_logfile 文件头（LOG_CHECKPOINT_1/2 交替写，带 CRC32）
     * 【与真实差异】Demo 用变量记录，持久化到 ib_logfile_header 文本文件
     */
    static long checkpointLsn = 0;

    /** ib_logfile0 环形写的当前写头（下一个写入 block 的序号） */
    static int redoWriteHead = 0;

    /** innodb_flush_log_at_trx_commit，默认 1 */
    static int flushPolicy = 1;

    /**
     * 事务 ID 计数器
     * 真实：trx_sys->max_trx_id，6 字节，全局自增，持久化到系统表空间
     * 【与真实差异】Demo 从 1 开始的简单 long 计数
     */
    static long txIdCounter = 1;

    // ==================== 日志条目结构 ====================

    /**
     * Undo Log Record（模拟真实 Undo Record 关键字段）
     *
     * 真实物理布局（UPDATE 类型，从 Undo Page 中读取）：
     *   offset  0: undo_type (1B) = TRX_UNDO_UPD_EXIST_REC
     *   offset  1: undo_no（可变长压缩整数）
     *   offset  N: table_id（可变长压缩整数）
     *   offset  M: info_bits (1B)
     *   offset  M+1: 旧列值列表（每列：列号 + 长度 + 内容）
     *   注：DATA_TRX_ID / DATA_ROLL_PTR 存在数据行的隐藏列上，不在 Undo Record 里
     *
     * 【与真实差异】Demo 只保留关键语义字段（txId/undoNo/key/oldValue/type/rollPointer）
     */
    static class UndoLogRecord {
        long txId;
        int  undoNo;       // 事务内操作序号，回滚时按此逆序；真实是压缩整数
        String key;        // 模拟 table_id + 主键；真实是行主键值
        int  oldValue;     // 被修改列的旧值；真实是变长字节序列（所有修改列）
        UndoType type;
        long rollPointer;  // 指向同事务上一条 Undo Record 的"地址"
                           // 真实是 7 字节，编码 rseg_id(1B)+page_no(4B)+offset(2B)
                           // Demo 用 undoLog List 的 index 模拟

        enum UndoType { INSERT, UPDATE, DELETE }

        UndoLogRecord(long txId, int undoNo, String key, int oldValue, UndoType type, long rollPointer) {
            this.txId = txId; this.undoNo = undoNo; this.key = key;
            this.oldValue = oldValue; this.type = type; this.rollPointer = rollPointer;
        }

        /** 序列化为文本行，写入 undo_001.ibu */
        String serialize() {
            return txId + "|" + undoNo + "|" + type + "|" + key + "|" + oldValue + "|" + rollPointer;
        }

        static UndoLogRecord deserialize(String line) {
            String[] p = line.split("\\|");
            // serialize 顺序: txId|undoNo|type|key|oldValue|rollPointer
            return new UndoLogRecord(Long.parseLong(p[0]), Integer.parseInt(p[1]),
                    p[3], Integer.parseInt(p[4]), UndoType.valueOf(p[2]), Long.parseLong(p[5]));
        }

        @Override public String toString() {
            String ptr = rollPointer < 0 ? "null(链头)" : String.valueOf(rollPointer);
            return String.format("UndoRecord[tx=%d, no=%d, type=%s, key=%s, oldVal=%d, rollPtr->%s]",
                    txId, undoNo, type, key, oldValue, ptr);
        }
    }

    /**
     * Redo Log Record（模拟物理 Redo 日志条目）
     *
     * 真实 Log Record 格式（以常见的 MLOG_8BYTES 为例）：
     *   type (1B)：日志类型，如 MLOG_1BYTE/2BYTE/4BYTE/8BYTE/WRITE_STRING/MULTI_REC
     *   space_id（压缩整数）：表空间 ID
     *   page_no （压缩整数）：数据页编号
     *   offset  (2B)：页内字节偏移
     *   新值    (变长)：写入的新内容（物理内容，不是 SQL）
     *   每条记录末尾还有 4B CRC32 校验
     *
     * "物理日志" vs "逻辑日志"：
     *   Redo Log 是物理日志（记录页内具体字节变化），幂等可重放
     *   Binlog 是逻辑日志（记录 SQL 或行变更事件），需要按序执行
     *
     * 【与真实差异】Demo 只保留 lsn/txId/key/newValue/isCommit/isFlushed 语义字段；
     *   真实没有 isCommit 字段，用特殊的 MLOG_MULTI_REC_END 类型标记事务提交；
     *   真实也没有 isFlushed，通过 flushed_to_disk_lsn 与本记录 lsn 比较来判断
     */
    static class RedoLogRecord {
        long    lsn;       // 本条记录的 LSN
        long    txId;
        String  key;       // 模拟 space_id + page_no + offset
        int     newValue;  // 新值（物理内容）
        boolean isCommit;  // 真实用 MLOG_MULTI_REC_END 类型标记，Demo 用 boolean
        boolean isFlushed; // 真实通过 flushed_to_disk_lsn 与 lsn 比较判断，Demo 用 boolean

        RedoLogRecord(long lsn, long txId, String key, int newValue) {
            this.lsn = lsn; this.txId = txId; this.key = key;
            this.newValue = newValue; this.isCommit = false; this.isFlushed = false;
        }

        static RedoLogRecord commit(long lsn, long txId) {
            RedoLogRecord r = new RedoLogRecord(lsn, txId, null, 0);
            r.isCommit = true;
            return r;
        }

        @Override public String toString() {
            String loc = isFlushed ? "[ib_logfile0✓]" : "[LogBuffer○  ]";
            if (isCommit)
                return String.format("Redo%s lsn=%-3d tx=%d COMMIT", loc, lsn, txId);
            return String.format("Redo%s lsn=%-3d tx=%d key=%-6s newVal=%d", loc, lsn, txId, key, newValue);
        }
    }

    /** 每个事务的 undo_no 计数器（事务内操作序号，用于逆序回滚） */
    static Map<Long, Integer> txUndoNoCounter = new HashMap<>();

    // ==================== 磁盘 IO 工具方法 ====================

    /**
     * 初始化磁盘目录和文件
     * 真实 MySQL 启动时也会做类似的文件初始化检查
     */
    static void initDiskDir() {
        try {
            Files.createDirectories(Paths.get(DISK_DIR));
        } catch (IOException e) {
            throw new RuntimeException("无法创建 disk 目录", e);
        }
    }

    /**
     * 将 diskData（内存）全量写入 data.ibd 文件
     * 模拟 checkpoint 触发的脏页刷盘（真实是按页粒度增量写，这里全量覆盖简化）
     *
     * 真实：Page Cleaner 线程将 Buffer Pool 中的脏页按 page_no 随机写入 .ibd 文件对应偏移
     * 【与真实差异】Demo 全量覆盖文本文件，不模拟随机页写入
     */
    static void flushDataToDisk(Map<String, Integer> data) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(DATA_FILE, false))) {
            for (Map.Entry<String, Integer> e : data.entrySet()) {
                pw.println(e.getKey() + "=" + e.getValue());
            }
            pw.flush();
            System.out.println("  [data.ibd]   脏页落盘完成 -> " + DATA_FILE);
            System.out.println("              内容: " + data);
        } catch (IOException e) {
            throw new RuntimeException("写 data.ibd 失败", e);
        }
    }

    /**
     * 从 data.ibd 文件读取数据到内存 Map
     * 真实：读取对应 page_no 的 16KB 页到 Buffer Pool
     * 【与真实差异】Demo 全量读取文本文件
     */
    static Map<String, Integer> loadDataFromDisk() {
        Map<String, Integer> data = new LinkedHashMap<>();
        Path p = Paths.get(DATA_FILE);
        if (!Files.exists(p)) return data;
        try {
            for (String line : Files.readAllLines(p)) {
                String[] kv = line.split("=", 2);
                if (kv.length == 2) data.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
            }
        } catch (IOException e) {
            throw new RuntimeException("读 data.ibd 失败", e);
        }
        return data;
    }

    /**
     * 追加写一条 Undo Record 到 undo_001.ibu 文件
     *
     * 真实：修改 Buffer Pool 中的 Undo Page（16KB），同时写 Redo Log for Undo（WAL 约束）；
     *       Undo Page 落盘由 Page Cleaner 负责
     * 【与真实差异】Demo 直接追加文本行到文件，没有 Undo Page 缓冲
     */
    static void appendUndoRecord(UndoLogRecord r) {
        try (FileWriter fw = new FileWriter(UNDO_FILE, true)) {
            fw.write(r.serialize() + "\n");
            fw.flush();
            System.out.println("  [undo_001.ibu] 追加 -> " + r.serialize());
        } catch (IOException e) {
            throw new RuntimeException("写 undo_001.ibu 失败", e);
        }
    }

    /**
     * 从 undo_001.ibu 读取全部 Undo Record 到内存
     * 真实：从 Rollback Segment 按 roll_pointer 链遍历 Undo Page
     * 【与真实差异】Demo 全量读取文本文件
     */
    static List<UndoLogRecord> loadUndoFromDisk() {
        List<UndoLogRecord> list = new ArrayList<>();
        Path p = Paths.get(UNDO_FILE);
        if (!Files.exists(p)) return list;
        try {
            for (String line : Files.readAllLines(p)) {
                if (!line.trim().isEmpty()) list.add(UndoLogRecord.deserialize(line.trim()));
            }
        } catch (IOException e) {
            throw new RuntimeException("读 undo_001.ibu 失败", e);
        }
        return list;
    }

    /**
     * 将一批 Redo Log Record 写入 ib_logfile0（环形二进制文件）
     *
     * 文件结构：顺序存放定长 block，每块 REDO_BLOCK_SIZE(64B)
     *   每块布局：lsn(8) | txId(8) | flags(4) | key(32,右填0) | newValue(4) | padding(8)
     *
     * 环形写：slot = writeHead % REDO_FILE_CAPACITY
     *   写满后从头覆盖（覆盖条件：被覆盖 block 的 lsn <= checkpoint_lsn，Demo 中简化不做检查）
     *
     * 真实：每块 512B（磁盘扇区对齐），有 block_header(12B)/block_trailer(4B) 和 CRC32；
     *       用 pwrite() 直接写到文件指定偏移（避免 seek+write 的非原子问题）；
     *       innodb_flush_log_at_trx_commit=1 时再调用 fsync()
     * 【与真实差异】Demo 块大小 64B，无 CRC，用 RandomAccessFile seek+write 代替 pwrite
     */
    static void writeRedoBlocksToDisk(List<RedoLogRecord> records, int startHead) {
        try (RandomAccessFile raf = new RandomAccessFile(REDO_FILE, "rw")) {
            // 确保文件足够大（预分配环形空间）
            long totalSize = (long) REDO_FILE_CAPACITY * REDO_BLOCK_SIZE;
            if (raf.length() < totalSize) raf.setLength(totalSize);

            int head = startHead;
            for (RedoLogRecord r : records) {
                int slot = head % REDO_FILE_CAPACITY;
                long offset = (long) slot * REDO_BLOCK_SIZE;
                raf.seek(offset);

                // 写入定长 block（64B）
                ByteBuffer buf = ByteBuffer.allocate(REDO_BLOCK_SIZE);
                buf.putLong(r.lsn);                        // offset 0 : lsn (8B)
                buf.putLong(r.txId);                       // offset 8 : txId (8B)
                int flags = (r.isCommit ? 1 : 0) | (r.isFlushed ? 2 : 0);
                buf.putInt(flags);                         // offset 16: flags (4B)
                byte[] keyBytes = new byte[32];
                if (r.key != null) {
                    byte[] kb = r.key.getBytes(StandardCharsets.UTF_8);
                    System.arraycopy(kb, 0, keyBytes, 0, Math.min(kb.length, 32));
                }
                buf.put(keyBytes);                         // offset 20: key (32B)
                buf.putInt(r.newValue);                    // offset 52: newValue (4B)
                buf.putLong(0L);                           // offset 56: padding (8B)
                raf.write(buf.array());
                head++;
            }
            // fsync（真实：调用 fdatasync()，只同步数据不同步元数据，更快）
            raf.getFD().sync();
        } catch (IOException e) {
            throw new RuntimeException("写 ib_logfile0 失败", e);
        }
    }

    /**
     * 从 ib_logfile0 读取全部有效 Redo Block 到内存
     * 真实：扫描 ib_logfile 从 checkpoint_lsn 开始，按 LSN 顺序重放
     * 【与真实差异】Demo 全量扫描所有 slot
     */
    static List<RedoLogRecord> readRedoBlocksFromDisk() {
        List<RedoLogRecord> list = new ArrayList<>();
        Path p = Paths.get(REDO_FILE);
        if (!Files.exists(p)) return list;
        try (RandomAccessFile raf = new RandomAccessFile(REDO_FILE, "r")) {
            for (int i = 0; i < REDO_FILE_CAPACITY; i++) {
                long offset = (long) i * REDO_BLOCK_SIZE;
                if (offset + REDO_BLOCK_SIZE > raf.length()) break;
                raf.seek(offset);
                byte[] buf = new byte[REDO_BLOCK_SIZE];
                raf.readFully(buf);
                ByteBuffer bb = ByteBuffer.wrap(buf);
                long lsn    = bb.getLong();
                long txId   = bb.getLong();
                int  flags  = bb.getInt();
                byte[] keyBytes = new byte[32];
                bb.get(keyBytes);
                int  newVal = bb.getInt();

                if (lsn == 0 && txId == 0) continue; // 空 slot

                boolean isCommit  = (flags & 1) != 0;
                boolean isFlushed = (flags & 2) != 0;
                String key = null;
                if (!isCommit) {
                    int len = 0;
                    while (len < 32 && keyBytes[len] != 0) len++;
                    key = new String(keyBytes, 0, len, StandardCharsets.UTF_8);
                }
                RedoLogRecord r = new RedoLogRecord(lsn, txId, key, newVal);
                r.isCommit = isCommit;
                r.isFlushed = isFlushed;
                list.add(r);
            }
        } catch (IOException e) {
            throw new RuntimeException("读 ib_logfile0 失败", e);
        }
        return list;
    }

    /**
     * 持久化 checkpoint_lsn 和 write_head 到 ib_logfile_header 文件
     *
     * 真实：写在 ib_logfile 文件头（LOG_CHECKPOINT_1/2 两个 512B checkpoint 页，交替写，
     *       每页含 checkpoint_lsn + checkpoint_no + CRC32，用双写保证原子性）
     * 【与真实差异】Demo 写简单文本，无双写、无 CRC
     */
    static void saveRedoHeader() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(REDO_HEADER_FILE, false))) {
            pw.println("checkpoint_lsn=" + checkpointLsn);
            pw.println("write_head=" + redoWriteHead);
            pw.flush();
        } catch (IOException e) {
            throw new RuntimeException("写 ib_logfile_header 失败", e);
        }
    }

    /**
     * 从 ib_logfile_header 恢复 checkpoint_lsn 和 write_head
     * 真实：读文件头中 CRC 校验通过的 checkpoint 页（选 checkpoint_no 更大的那个）
     * 【与真实差异】Demo 直接读文本，不做 CRC 校验
     */
    static void loadRedoHeader() {
        Path p = Paths.get(REDO_HEADER_FILE);
        if (!Files.exists(p)) return;
        try {
            for (String line : Files.readAllLines(p)) {
                if (line.startsWith("checkpoint_lsn="))
                    checkpointLsn = Long.parseLong(line.split("=")[1]);
                if (line.startsWith("write_head="))
                    redoWriteHead = Integer.parseInt(line.split("=")[1]);
            }
        } catch (IOException e) {
            throw new RuntimeException("读 ib_logfile_header 失败", e);
        }
    }

    // ==================== InnoDB 核心操作模拟 ====================

    /**
     * 写入 Redo Log Record 到 Log Buffer（内存）
     * 写满时强制 flush 到 ib_logfile0
     *
     * 真实：① 持有 log_sys mutex（MySQL 8.0 改为无锁 CAS）
     *       ② 在 buf_free 位置写 Log Record 字节流，更新 buf_free 指针
     *       ③ buf_free > innodb_log_buffer_size/2 则唤醒后台 log flusher
     * 【与真实差异】Demo 直接写对象到数组，不模拟互斥锁和字节写入
     */
    static void writeToLogBuffer(RedoLogRecord record) {
        if (logBufPos >= LOG_BUFFER_CAPACITY) {
            System.out.println("  [Log Buffer] 已满（" + LOG_BUFFER_CAPACITY
                    + " 条），强制 flush 到 ib_logfile0（真实：唤醒后台 log flusher 线程）");
            flushLogBufferToDisk();
        }
        logBuffer[logBufPos++] = record;
    }

    /**
     * Log Buffer → ib_logfile0（环形磁盘文件）flush + fsync
     *
     * 真实两步流程：
     *   write：pwrite() 把 Log Buffer 内容写到 ib_logfile0 的 write_lsn 位置（写到 OS page cache）
     *   fsync：fdatasync() 把 OS page cache 强制刷到磁盘（同步 IO，是 OLTP 性能瓶颈所在）
     *
     * 环形写入说明（面试重点！）：
     *   slot = write_head % REDO_FILE_CAPACITY
     *   write_head 单调递增，文件空间有限，必须保证 slot 对应的旧 LSN <= checkpoint_lsn 才能覆盖
     *   若 checkpoint 推进太慢（脏页积压），write_head 追上 → 写日志阻塞（实际报 checkpoint age 告警）
     *
     * 【与真实差异】Demo 不做 checkpoint_lsn 覆盖检查；真实写字节流，Demo 写 64B 定长 block
     */
    static void flushLogBufferToDisk() {
        List<RedoLogRecord> toFlush = new ArrayList<>();
        for (int i = 0; i < logBufPos; i++) {
            if (logBuffer[i] != null) {
                logBuffer[i].isFlushed = true;
                toFlush.add(logBuffer[i]);
            }
        }
        if (!toFlush.isEmpty()) {
            writeRedoBlocksToDisk(toFlush, redoWriteHead);
            redoWriteHead += toFlush.size();
            saveRedoHeader(); // 持久化 write_head
        }
        flushedToDisklsn = currentLsn;
        Arrays.fill(logBuffer, 0, logBufPos, null);
        logBufPos = 0;
    }

    /**
     * 读取数据（Buffer Pool 缺页处理）
     *
     * Buffer Pool 命中  → 直接返回（零 IO）
     * Buffer Pool 缺页 → 从磁盘 data.ibd 加载到 Buffer Pool（真实触发 IO wait）
     *
     * 真实：从 .ibd 文件读取整个 16KB 数据页到 Buffer Pool
     * 【与真实差异】Demo 按 key 精确读，不区分页粒度
     */
    static int read(String key) {
        if (bufferPool.containsKey(key)) {
            return bufferPool.get(key);
        }
        Map<String, Integer> diskData = loadDataFromDisk();
        int val = diskData.getOrDefault(key, 0);
        bufferPool.put(key, val);
        System.out.println("  [Buffer Pool] page fault: " + key
                + " 不在内存，从 data.ibd 加载 16KB 数据页到 Buffer Pool，当前值=" + val);
        return val;
    }

    /**
     * UPDATE 操作完整流程（严格按真实 InnoDB 顺序）：
     *
     * 真实执行顺序（面试考点）：
     *   1. 从 Buffer Pool 读取数据页（缺页则从磁盘加载）
     *   2. 写 Undo Log Record（记录旧值 + roll_pointer，同时写 Redo for Undo）
     *   3. 修改 Buffer Pool 数据页（page.newest_modification = 当前 LSN）
     *   4. 写 Redo Log Buffer（记录物理变更 + LSN）
     *
     * 为什么先写 Undo 再写数据页？
     *   保证：若崩溃发生在第3步之后、COMMIT 之前，
     *         Undo Log 一定已经记录了旧值，可以正确回滚
     *
     * "Undo Log 本身也产生 Redo Log" 的原因：
     *   Undo Log 存在数据页（Undo Page）上，修改 Undo Page 也是对页的物理修改，
     *   因此也需要 Redo Log 来保证崩溃后 Undo Page 本身可以恢复（Redo for Undo）
     */
    static void update(long txId, String key, int newValue) {
        int oldValue = read(key);

        // Step 1: 写 Undo Log（同时落盘到 undo_001.ibu）
        int undoNo = txUndoNoCounter.getOrDefault(txId, 0);
        long prevUndoPtr = undoNo == 0 ? -1L : undoLog.size() - 1;
        UndoLogRecord undoRecord = new UndoLogRecord(
                txId, undoNo, key, oldValue, UndoLogRecord.UndoType.UPDATE, prevUndoPtr);
        undoLog.add(undoRecord);
        txUndoNoCounter.put(txId, undoNo + 1);
        appendUndoRecord(undoRecord);  // 真实写磁盘

        System.out.println("  [Undo Log]  写入 " + undoRecord);
        System.out.println("  [Undo Log]  -> 作用1: ROLLBACK 时用 oldVal=" + oldValue + " 把 " + key + " 恢复回去");
        System.out.println("  [Undo Log]  -> 作用2: MVCC 快照读沿 roll_pointer 链往前找历史版本");
        System.out.println("  [Undo Log]  -> 注意: 写 Undo Page 本身也产生 Redo Log(Redo for Undo)，");
        System.out.println("                       保证崩溃后 Undo Log 自身也能通过 Redo 恢复");

        // Step 2: 修改 Buffer Pool 脏页
        bufferPool.put(key, newValue);
        long pageLsn = currentLsn + 1;
        System.out.println("  [Buffer Pool] " + key + ": " + oldValue + " -> " + newValue
                + " (内存脏页，page.newest_modification=" + pageLsn
                + "，WAL 要求：此页落盘前 lsn<=" + pageLsn + " 的 Redo 必须先 fsync)");

        // Step 3: 写 Redo Log Buffer（仅内存，未 fsync）
        currentLsn++;
        RedoLogRecord redoRecord = new RedoLogRecord(currentLsn, txId, key, newValue);
        writeToLogBuffer(redoRecord);
        System.out.println("  [Log Buffer]  追加 " + redoRecord);
        System.out.println("  [Log Buffer]  仅在内存中，尚未 fsync 到 ib_logfile0");
    }

    /**
     * COMMIT 流程（面试必考）：
     *
     * innodb_flush_log_at_trx_commit=1（默认，最安全）：
     *   写 COMMIT Redo Record -> Log Buffer flush -> fsync ib_logfile0 -> 返回客户端成功
     *   脏页不需要立即落盘（后台 checkpoint 线程异步完成）
     *
     * 为什么不等脏页落盘就返回？
     *   脏页落盘是随机 IO（磁盘寻道），而 Redo Log 是顺序追加写（快得多）
     *   Redo Log fsync 后，即使脏页还在内存崩溃了，重启重放 Redo 也能还原
     *   这是 InnoDB 高性能 + 强持久性的核心设计
     */
    static void commit(long txId) {
        currentLsn++;
        RedoLogRecord commitRecord = RedoLogRecord.commit(currentLsn, txId);
        writeToLogBuffer(commitRecord);

        if (flushPolicy == 1) {
            flushLogBufferToDisk();  // write + fsync 到 ib_logfile0
            System.out.println("  [ib_logfile0] 写入 COMMIT 标记 (LSN=" + currentLsn + ")，fsync 完成");
            System.out.println("  [ib_logfile0] -> 返回客户端 提交成功 (innodb_flush_log_at_trx_commit=1)");
            System.out.println("  [关键]     只要这次 fsync 成功，即使 MySQL 崩溃，");
            System.out.println("             重启 Redo Phase 重放日志就能恢复数据，已提交事务绝不丢失");
        } else if (flushPolicy == 2) {
            // write 到 OS page cache，不立即 fsync（Demo 中 flushLogBufferToDisk 仍实际落盘，
            // 但把 isFlushed 标记为 false 以模拟 OS cache 未 fsync 的语义）
            flushLogBufferToDisk();
            // 模拟 OS cache 未 fsync：从磁盘文件中把此事务相关 block 的 isFlushed 位清零
            markRedoBlocksUnflushed(txId);
            flushedToDisklsn = checkpointLsn;
            System.out.println("  [ib_logfile0] COMMIT 写入 OS page cache (innodb_flush_log_at_trx_commit=2)");
            System.out.println("  [警告]     MySQL 进程崩溃可恢复，但机器断电/OS崩溃会丢失最近1秒内的已提交事务！");
        } else {
            System.out.println("  [Log Buffer]  COMMIT 未写 OS cache (innodb_flush_log_at_trx_commit=0，后台每秒做 write+fsync)");
            System.out.println("  [警告]     MySQL 进程崩溃就可能丢最近 1 秒的已提交事务，风险最大！");
        }

        // 脏页异步刷盘（模拟 checkpoint 后台行为）
        // 真实条件：page.newest_modification <= flushed_to_disk_lsn 才允许刷脏页
        System.out.println("  [Checkpoint] 后台 Page Cleaner 异步把脏页写回 data.ibd (不阻塞 COMMIT 返回)");
        Map<String, Integer> currentDisk = loadDataFromDisk();
        currentDisk.putAll(bufferPool);
        flushDataToDisk(currentDisk);  // 真实写 data.ibd
        checkpointLsn = currentLsn;
        saveRedoHeader();  // 持久化 checkpoint_lsn
        System.out.println("  [Checkpoint] checkpoint_lsn -> " + checkpointLsn
                + " (这之前的 ib_logfile0 空间可被循环覆盖)");
    }

    /**
     * 将 ib_logfile0 中属于 txId 的 block 的 isFlushed 位清零
     * 用于模拟 flushPolicy=2 时 OS cache 尚未 fsync 的状态
     */
    static void markRedoBlocksUnflushed(long txId) {
        try (RandomAccessFile raf = new RandomAccessFile(REDO_FILE, "rw")) {
            for (int i = 0; i < REDO_FILE_CAPACITY; i++) {
                long offset = (long) i * REDO_BLOCK_SIZE;
                if (offset + REDO_BLOCK_SIZE > raf.length()) break;
                raf.seek(offset);
                byte[] buf = new byte[REDO_BLOCK_SIZE];
                raf.readFully(buf);
                ByteBuffer bb = ByteBuffer.wrap(buf);
                bb.getLong(); // lsn
                long tid = bb.getLong();
                if (tid == txId) {
                    int flags = bb.getInt() & ~2; // 清 isFlushed bit
                    ByteBuffer out = ByteBuffer.wrap(buf);
                    out.getLong(); out.getLong();
                    out.putInt(flags);
                    raf.seek(offset);
                    raf.write(buf);
                }
            }
            raf.getFD().sync();
        } catch (IOException e) {
            throw new RuntimeException("markRedoBlocksUnflushed 失败", e);
        }
    }

    /**
     * ROLLBACK 流程（原子性的直接保证者）：
     *
     * 真实执行顺序：
     *   1. InnoDB 通过行上的 DATA_ROLL_PTR 找到该事务最新的 Undo Record
     *   2. 读取 Undo Record 中的 oldValue，写回数据页（同时产生 Redo Log！）
     *   3. 沿 roll_pointer 链往前，重复直到 undo_no=0 的记录
     *   4. 释放 Undo Segment（标记为可重用，purge 线程稍后物理回收）
     *
     * 回滚本身也产生 Redo Log，原因：
     *   回滚是对数据页的物理修改（写旧值），必须满足 WAL 约束，
     *   保证回滚到一半崩溃时重启能通过 Redo 继续完成回滚
     *
     * 【与真实差异】真实通过 DATA_ROLL_PTR 隐藏列遍历；Demo 直接遍历 undoLog 列表
     */
    static void rollback(long txId) {
        System.out.println("  [Rollback] 开始回滚 tx=" + txId + "，沿 roll_pointer 链逆序读取 undo_001.ibu ...");

        List<UndoLogRecord> txRecords = new ArrayList<>();
        for (UndoLogRecord r : undoLog) { if (r.txId == txId) txRecords.add(r); }
        txRecords.sort((a, b) -> b.undoNo - a.undoNo);

        for (UndoLogRecord record : txRecords) {
            int curVal = bufferPool.getOrDefault(record.key,
                    loadDataFromDisk().getOrDefault(record.key, 0));
            bufferPool.put(record.key, record.oldValue);
            System.out.println("  [Undo Apply] undoNo=" + record.undoNo + ": " + record.key
                    + " " + curVal + " -> " + record.oldValue
                    + " (从 Undo Record 的 oldValue 字段读取旧值，写回 Buffer Pool)");
            currentLsn++;
            RedoLogRecord rollbackRedo = new RedoLogRecord(currentLsn, txId, record.key, record.oldValue);
            writeToLogBuffer(rollbackRedo);
            System.out.println("  [Log Buffer]  回滚也写 Redo Log (LSN=" + currentLsn
                    + "): 防止回滚到一半崩溃导致数据不一致");
        }
        System.out.println("  [Rollback] 完成，所有修改已撤销，数据回到事务开始前状态");
        System.out.println("  [Purge]    undo_001.ibu 中的 Undo Segment 标记为可重用；"
                + "purge 后台线程稍后确认无 MVCC 读者后物理清理");
    }

    /**
     * 崩溃恢复（MySQL 重启时 InnoDB 自动执行，用户无感知）：
     *
     * 真实步骤（简化版）：
     *   1. 读 ib_logfile_header 获取 checkpoint_lsn
     *   2. 扫描 ib_logfile0 从 checkpoint_lsn 开始的所有已落盘 block
     *   3. Redo Phase：重放有 COMMIT 标记事务的所有 DATA 日志到 data.ibd
     *   4. Undo Phase：用 undo_001.ibu 回滚没有 COMMIT 的事务
     *
     * 真实：step4 完成后还要更新 trx_sys，释放 Undo Segment，然后开放连接
     * 【与真实差异】Demo 从内存变量读 checkpoint_lsn，真实从文件头读取
     */
    static void crashRecovery() {
        System.out.println("\n========== [Crash Recovery] MySQL 重启，InnoDB 自动恢复 ==========");

        // 从文件头读取 checkpoint_lsn（真实：读 ib_logfile 文件头 CRC 校验通过的 checkpoint 页）
        loadRedoHeader();
        System.out.println("  从 ib_logfile_header 读取: checkpoint_lsn=" + checkpointLsn
                + "  write_head=" + redoWriteHead);
        System.out.println("  恢复原则：Redo Phase 先（前滚已提交）-> Undo Phase 后（回滚未提交）");

        // 从 ib_logfile0 读取全部已落盘日志
        List<RedoLogRecord> allRedo = readRedoBlocksFromDisk();
        System.out.println("  从 ib_logfile0 读取到 " + allRedo.size() + " 条 Redo 记录");

        // Phase 1: Redo Phase
        System.out.println("\n  -- Redo Phase：从 checkpoint_lsn=" + checkpointLsn + " 开始，重放已落盘日志 --");
        Set<Long> committedTxIds = new HashSet<>();
        for (RedoLogRecord r : allRedo) {
            if (r.isCommit && r.isFlushed) committedTxIds.add(r.txId);
        }
        System.out.println("  ib_logfile0 中已落盘已提交事务：" + committedTxIds);

        Map<String, Integer> diskData = loadDataFromDisk();
        boolean changed = false;
        for (RedoLogRecord r : allRedo) {
            if (!r.isCommit && r.isFlushed && r.lsn > checkpointLsn && committedTxIds.contains(r.txId)) {
                diskData.put(r.key, r.newValue);
                System.out.println("  [Redo Apply] lsn=" + r.lsn + " 重放: " + r.key + " = " + r.newValue);
                changed = true;
            }
        }
        if (changed) flushDataToDisk(diskData);

        // Phase 2: Undo Phase
        System.out.println("\n  -- Undo Phase：从 undo_001.ibu 回滚崩溃时未提交的事务 --");
        List<UndoLogRecord> allUndo = loadUndoFromDisk();
        Set<Long> allTxIds = new HashSet<>();
        for (UndoLogRecord r : allUndo) allTxIds.add(r.txId);
        Set<Long> uncommitted = new HashSet<>(allTxIds);
        uncommitted.removeAll(committedTxIds);
        System.out.println("  需回滚的事务：" + uncommitted);

        for (long txId : uncommitted) {
            List<UndoLogRecord> txRecords = new ArrayList<>();
            for (UndoLogRecord r : allUndo) { if (r.txId == txId) txRecords.add(r); }
            txRecords.sort((a, b) -> b.undoNo - a.undoNo);
            for (UndoLogRecord r : txRecords) {
                diskData.put(r.key, r.oldValue);
                System.out.println("  [Undo Apply] tx=" + txId
                        + " 沿 roll_pointer 链回滚: " + r.key + " -> " + r.oldValue);
            }
        }
        if (!uncommitted.isEmpty()) flushDataToDisk(diskData);

        System.out.println("\n  [Recovery 完成] 数据库已恢复到一致性状态，可以对外服务");
        System.out.println("  当前 data.ibd: " + loadDataFromDisk());
    }

    // ==================== 场景演示 ====================

    /** 清空磁盘文件并写入初始数据 */
    static void resetDisk() {
        initDiskDir();
        try {
            // 清空所有磁盘文件
            Files.deleteIfExists(Paths.get(DATA_FILE));
            Files.deleteIfExists(Paths.get(UNDO_FILE));
            Files.deleteIfExists(Paths.get(REDO_FILE));
            Files.deleteIfExists(Paths.get(REDO_HEADER_FILE));
        } catch (IOException e) {
            throw new RuntimeException("清理 disk 目录失败", e);
        }
        // 内存重置
        bufferPool.clear();
        undoLog.clear();
        Arrays.fill(logBuffer, null);
        logBufPos = 0;
        txUndoNoCounter.clear();
        currentLsn = 0;
        checkpointLsn = 0;
        flushedToDisklsn = 0;
        redoWriteHead = 0;

        // 写初始数据到 data.ibd
        Map<String, Integer> init = new LinkedHashMap<>();
        init.put("Alice", 1000);
        init.put("Bob", 500);
        flushDataToDisk(init);
        saveRedoHeader();
        System.out.println("初始化: Alice=1000, Bob=500 -> " + DATA_FILE);
        System.out.println("disk 目录: " + new File(DISK_DIR).getAbsolutePath());
    }

    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println(" InnoDB 原子性保证机制演示（磁盘文件真实写入）");
        System.out.println(" innodb_flush_log_at_trx_commit=" + flushPolicy + "（最安全，每次 COMMIT 都 fsync）");
        System.out.println("============================================================");

        resetDisk();
        System.out.println("\n==================== 场景1：正常转账（COMMIT 成功）====================");
        scenario1_normalTransfer();

        resetDisk();
        System.out.println("\n==================== 场景2：业务异常，ROLLBACK ====================");
        scenario2_rollback();

        resetDisk();
        System.out.println("\n==================== 场景3：COMMIT 后崩溃，Redo Log 恢复 ====================");
        scenario3_crashAfterCommit();

        resetDisk();
        System.out.println("\n==================== 场景4：COMMIT 前崩溃，Undo Log 回滚 ====================");
        scenario4_crashBeforeCommit();

        resetDisk();
        System.out.println("\n==================== 场景5：innodb_flush_log_at_trx_commit=2 的风险 ====================");
        scenario5_flushPolicy2Risk();
    }

    static void scenario1_normalTransfer() {
        long txId = txIdCounter++;
        System.out.println("[BEGIN tx=" + txId + "] Alice 转 200 元给 Bob");

        System.out.println("\n-- UPDATE Alice --");
        update(txId, "Alice", read("Alice") - 200);

        System.out.println("\n-- UPDATE Bob --");
        update(txId, "Bob", read("Bob") + 200);

        System.out.println("\n-- COMMIT --");
        commit(txId);

        System.out.println("\n最终 data.ibd: " + loadDataFromDisk());
        System.out.println("可以 cat " + new File(DATA_FILE).getAbsolutePath() + " 查看文件内容");
        System.out.println("可以 xxd " + new File(REDO_FILE).getAbsolutePath() + " | head 查看 ib_logfile0 二进制内容");
        System.out.println("✓ 原子性：两步要么全成功，要么全失败");
        System.out.println("✓ 持久性：Redo Log fsync 后即持久，脏页异步落盘");
    }

    static void scenario2_rollback() {
        long txId = txIdCounter++;
        System.out.println("[BEGIN tx=" + txId + "] Alice 转 200 元给 Bob");

        System.out.println("\n-- UPDATE Alice（扣款）--");
        update(txId, "Alice", read("Alice") - 200);

        System.out.println("\n-- Bob 账户冻结，业务异常 -> ROLLBACK --");
        rollback(txId);

        Map<String, Integer> finalData = loadDataFromDisk();
        finalData.putAll(bufferPool);  // 合并内存中的回滚结果
        System.out.println("\n最终数据（含 Buffer Pool）: Alice="
                + bufferPool.getOrDefault("Alice", loadDataFromDisk().getOrDefault("Alice", 0))
                + ", Bob=" + bufferPool.getOrDefault("Bob", loadDataFromDisk().getOrDefault("Bob", 0)));
        System.out.println("✓ Undo Log 按 undoNo 逆序 + roll_pointer 链撤销所有修改");
    }

    static void scenario3_crashAfterCommit() {
        long txId = txIdCounter++;
        System.out.println("[BEGIN tx=" + txId + "] Alice 转 200 元给 Bob");
        update(txId, "Alice", 800);
        update(txId, "Bob", 700);
        commit(txId);

        System.out.println("\n[CRASH!] 宕机，Buffer Pool 丢失；模拟脏页未落盘（手动覆盖 data.ibd 为旧值）");
        bufferPool.clear();
        // 手动把 data.ibd 改回旧值，模拟脏页没有落盘
        Map<String, Integer> stale = new LinkedHashMap<>();
        stale.put("Alice", 1000);
        stale.put("Bob", 500);
        flushDataToDisk(stale);
        System.out.println("崩溃后 data.ibd 被强制改回: Alice=1000, Bob=500");
        System.out.println("ib_logfile0 已 fsync (flushed_to_disk_lsn=" + flushedToDisklsn + ")");
        System.out.println("重启，开始 Crash Recovery，读取 ib_logfile_header + ib_logfile0 ...");

        // 模拟重启：清空内存，从磁盘恢复
        bufferPool.clear();
        undoLog.clear();
        currentLsn = 0;

        crashRecovery();
        System.out.println("恢复后 data.ibd: " + loadDataFromDisk());
        System.out.println("✓ Redo Log WAL：已 fsync 的日志必能重放，已提交事务不丢失");
    }

    static void scenario4_crashBeforeCommit() {
        long txId = txIdCounter++;
        System.out.println("[BEGIN tx=" + txId + "] Alice 转 200 元给 Bob（不提交）");
        update(txId, "Alice", 800);
        update(txId, "Bob", 700);
        // 没有 commit！Log Buffer 未 fsync，ib_logfile0 无 COMMIT block

        System.out.println("\n[CRASH!] 提交前宕机，ib_logfile0 无 COMMIT block");
        System.out.println("模拟最坏情况：Alice 脏页已落盘（Alice=800），Bob 未落盘（Bob=500）");
        Map<String, Integer> partial = new LinkedHashMap<>();
        partial.put("Alice", 800);
        partial.put("Bob", 500);
        flushDataToDisk(partial);
        System.out.println("崩溃后 data.ibd: Alice=800（已落），Bob=500（未落）");
        System.out.println("ib_logfile0 无 COMMIT 标记（Log Buffer 未 fsync）");

        bufferPool.clear();
        currentLsn = 0;

        crashRecovery();
        System.out.println("恢复后 data.ibd: " + loadDataFromDisk());
        System.out.println("✓ Undo Log 保证：未提交事务所有修改全部撤销，不会半提交");
    }

    static void scenario5_flushPolicy2Risk() {
        flushPolicy = 2;
        System.out.println("切换 innodb_flush_log_at_trx_commit=2（COMMIT 只 write 到 OS cache，不立即 fsync）");

        long txId = txIdCounter++;
        System.out.println("[BEGIN tx=" + txId + "] Alice 转 200 元给 Bob");
        update(txId, "Alice", 800);
        update(txId, "Bob", 700);
        commit(txId);  // ib_logfile0 中该事务 block 的 isFlushed bit = 0（模拟 OS cache 未 fsync）

        System.out.println("\n[CRASH!] 机器断电（OS 崩溃），OS page cache 丢失！");
        System.out.println("ib_logfile0 中该事务 block isFlushed=false -> Redo Phase 无法重放");
        Map<String, Integer> stale = new LinkedHashMap<>();
        stale.put("Alice", 1000);
        stale.put("Bob", 500);
        flushDataToDisk(stale);
        flushedToDisklsn = 0;
        bufferPool.clear();
        currentLsn = 0;

        crashRecovery();
        System.out.println("恢复后 data.ibd: " + loadDataFromDisk());
        System.out.println("✗ 已提交事务丢失！这是 innodb_flush_log_at_trx_commit=2 的已知风险");
        System.out.println("  适用场景：允许少量丢失，追求极致写性能（如日志流水表、临时统计表）");
        System.out.println("  金融/支付系统：必须使用 innodb_flush_log_at_trx_commit=1");

        flushPolicy = 1;
    }
}

