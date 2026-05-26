import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * ============================================================
 * DiskStore —— 磁盘 IO 层（模拟 InnoDB 的文件读写）
 * ============================================================
 *
 * 这个类封装了所有"写盘/读盘"操作，上层的 TransactionEngine
 * 只管调接口，不直接碰文件，实现了职责分离。
 *
 * ★ 面试怎么说（InnoDB 有哪些磁盘文件）：
 *   "InnoDB 主要有这几类文件：
 *    · data.ibd   → 数据文件，存放真正的数据页（16KB/页），每张表一个；
 *    · undo_001.ibu → Undo 表空间，存放 Undo Log（MySQL 8.0 起独立文件）；
 *    · ib_logfile0  → Redo Log 文件，环形覆盖写，存放 WAL 日志；
 *    · binlog       → Server 层的 Binlog 文件，追加写，用于主从复制和恢复。"
 *
 * 本 Demo 的磁盘文件布局：
 * <pre>
 *  disk/
 *   ├── data.ibd          — 数据文件（key=value 文本，模拟 16KB 数据页）
 *   ├── undo_001.ibu      — Undo 文件（每行一条 Undo Record）
 *   ├── ib_logfile0       — Redo Log（定长 64B 二进制块，环形写，模拟真实 512B block）
 *   ├── ib_logfile_header — 日志文件头（checkpoint_lsn + write_head，模拟真实 4096B 文件头）
 *   └── binlog            — Binlog 文件（每行一条事件，模拟 mysql-bin.000001）
 * </pre>
 */
public class DiskStore extends MysqlDemoBase {

    // ==================== 磁盘文件路径常量 ====================

    /**
     * disk/ 目录绝对路径
     * 真实 MySQL：由启动参数 --datadir 指定
     */
    public static final String DISK_DIR = "/Users/qiankun96/Desktop/面试/AIGC-Interview-Book/面试学习/mysql/demo/disk";

    /**
     * 数据文件，对应真实的 .ibd 文件（InnoDB 数据表空间）
     *
     * ★ 面试知识点：
     *   真实 .ibd 每页 16KB，有页头（38B）、行格式（Compact/Dynamic）、页目录等复杂结构。
     *   本 Demo 用"key=value 文本"简化，每次写盘全量覆盖（真实是增量随机 IO）。
     */
    public static final String DATA_FILE = DISK_DIR + "/data.ibd";

    /**
     * Undo 表空间文件，对应真实的 undo_001.ibu（MySQL 8.0 独立 Undo 文件）
     *
     * ★ 面试知识点：
     *   Undo Log 的落盘路径：
     *   先写到 Buffer Pool 里的 Undo Page（内存脏页）→ 同时写 Redo for Undo 到 Log Buffer →
     *   提交时 Redo fsync → Page Cleaner 异步把 Undo Page 刷入 .ibu 文件。
     *   即使 .ibu 文件没来得及落盘就崩溃，重启时 Redo Phase 会先重放 Redo for Undo，
     *   把 Undo Page 重建出来，Undo Phase 再用它回滚。
     */
    public static final String UNDO_FILE = DISK_DIR + "/undo_001.ibu";

    /**
     * Redo Log 文件，对应真实的 ib_logfile0（环形循环写）
     *
     * ★ 面试知识点（Redo Log 环形写，面试常考）：
     *   "ib_logfile0 是固定大小的循环文件（默认 2×48MB，MySQL 8.0 改为单文件可扩展）。
     *    写满后从头覆盖，但覆盖的前提是：被覆盖位置对应的数据页已经安全落盘（checkpoint 推进到这里）。
     *    如果脏页写盘太慢，checkpoint 推进不够快，Redo Log 写满就会阻塞写入，
     *    报'checkpoint age'告警，这是 InnoDB 性能瓶颈之一。"
     *
     * 本 Demo 二进制块布局（每块 REDO_BLOCK_SIZE = 64B）：
     * <pre>
     *  offset  0 ( 8B): LSN
     *  offset  8 ( 8B): txId
     *  offset 16 ( 4B): flags（bit0=isCommit, bit1=isFlushed, bit2=isRedoForUndo, bit3=isPrepare）
     *  普通数据日志（isRedoForUndo=false）:
     *    offset 20 (32B): key, offset 52 (4B): newValue, offset 56 (8B): padding
     *  Redo for Undo（isRedoForUndo=true）:
     *    offset 20 (4B): undoNo, offset 24 (24B): undoKey,
     *    offset 48 (4B): oldValue, offset 52 (4B): rollPtr 低32位, offset 56 (8B): padding
     * </pre>
     */
    public static final String REDO_FILE         = DISK_DIR + "/ib_logfile0";
    public static final int    REDO_BLOCK_SIZE   = 64;   // Demo 简化；真实 512B（一个磁盘扇区）
    public static final int    REDO_FILE_CAPACITY = 128; // 最多 128 个 block；真实 2×48MB

    /**
     * Redo Log 文件头，记录 checkpoint_lsn 和环形写头 write_head
     *
     * ★ 面试知识点：
     *   "checkpoint_lsn 是'安全线'：这之前的 Redo Log 对应的数据页已经写盘，
     *    对应的 Redo Log 空间可以被新日志覆盖复用。
     *    MySQL 重启崩溃恢复时，第一步就是读文件头的 checkpoint_lsn，
     *    从这里开始扫描 Redo Log，只重放 checkpoint_lsn 之后的记录。"
     *
     * 真实文件头 4096B，有 LOG_CHECKPOINT_1/2 两个 checkpoint 页交替写（带 CRC32 校验），
     * 防止写文件头时崩溃导致文件头损坏。
     */
    public static final String REDO_HEADER_FILE = DISK_DIR + "/ib_logfile_header";

    /**
     * Binlog 文件，对应真实的 mysql-bin.000001（MySQL Server 层）
     *
     * ★ 面试知识点：
     *   "Binlog 追加写，不循环，文件写满后 rotate 产生新文件（mysql-bin.000002 ...）。
     *    从库的 IO Thread 按 file:offset 游标拉取 Binlog，SQL Thread 回放执行。
     *    sync_binlog=1 表示每次提交都 fsync，保证 OS 崩溃也不丢 Binlog。"
     */
    public static final String BINLOG_FILE = DISK_DIR + "/binlog";

    // ==================== 目录初始化 ====================

    /**
     * 初始化 disk/ 目录（不存在则创建）
     * 真实 MySQL 启动时也会做类似的文件初始化检查（my.cnf 的 datadir）
     */
    public static void initDiskDir() {
        try {
            Files.createDirectories(Paths.get(DISK_DIR));
        } catch (IOException e) {
            throw new RuntimeException("无法创建 disk 目录", e);
        }
    }

    // ==================== data.ibd 读写 ====================

    /**
     * 把内存中的数据全量写入 data.ibd（覆盖写，模拟脏页落盘）
     *
     * ★ 面试知识点（为什么 COMMIT 不等这一步）：
     *   "数据落盘是随机 IO（磁盘要寻道），很慢。
     *    Redo Log 是顺序追加写，非常快。
     *    所以 InnoDB 的设计是：COMMIT 时只 fsync Redo Log 就够了，
     *    脏页由后台 Page Cleaner 线程慢慢异步刷盘，不阻塞客户端。
     *    即使脏页还没落盘就崩溃，重启后重放 Redo Log 也能还原，这就是 WAL 的精髓。"
     *
     * 真实：Page Cleaner 落盘前会检查 page.newest_modification ≤ flushed_to_disk_lsn，
     * 确保对应 Redo 已 fsync，才允许写盘（WAL 约束）。
     */
    public static void flushDataToDisk(Map<String, Integer> data) {
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
     * 从 data.ibd 文件读取数据到内存 Map（模拟 Buffer Pool 缺页时从磁盘加载）
     *
     * 真实：只读需要的那一页（16KB），不会全量读取。
     * 读到内存后放入 Buffer Pool，下次读同一页就直接从内存取（零 IO）。
     */
    public static Map<String, Integer> loadDataFromDisk() {
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

    // ==================== undo_001.ibu 读写 ====================

    /**
     * 追加写一条 Undo Record 到 undo_001.ibu 文件
     *
     * 注意：这是"绕过 Buffer Pool 直接写盘"的辅助方法，
     * 正常业务路径是通过 TransactionEngine.writeUndoToUndoPage() 先写到内存 Undo Page，
     * 再由 Page Cleaner 异步刷盘。
     */
    public static void appendUndoRecord(UndoLogRecord r) {
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
     *
     * 真实：从 Rollback Segment 按 roll_pointer 链遍历 Undo Page（Buffer Pool 中），
     * 不一定要读文件（Page 可能还在内存）。
     */
    public static List<UndoLogRecord> loadUndoFromDisk() {
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

    // ==================== ib_logfile0（Redo Log 文件）读写 ====================

    /**
     * 将一批 Redo Log Record 写入 ib_logfile0（环形二进制文件）
     *
     * ★ 面试知识点（write vs fsync 的区别）：
     *   "write（pwrite）是把数据从进程内存写到 OS 的 page cache，速度很快。
     *    fsync（fdatasync）是把 OS page cache 强制刷到物理磁盘，速度慢，
     *    但只有 fsync 之后数据才真正'安全'，机器断电也不会丢。
     *    innodb_flush_log_at_trx_commit=1 就是每次 COMMIT 都 write+fsync，最安全。"
     *
     * 环形写的覆盖条件：
     *   被覆盖位置的 LSN 必须 ≤ checkpoint_lsn（那个位置的数据页已安全落盘）。
     *   否则写入会阻塞，等 checkpoint 推进。本 Demo 简化：不做覆盖检查。
     */
    public static void writeRedoBlocksToDisk(List<RedoLogRecord> records, int startHead) {
        try (RandomAccessFile raf = new RandomAccessFile(REDO_FILE, "rw")) {
            // 预分配文件空间（环形写需要固定大小）
            long totalSize = (long) REDO_FILE_CAPACITY * REDO_BLOCK_SIZE;
            if (raf.length() < totalSize) raf.setLength(totalSize);

            int head = startHead;
            for (RedoLogRecord r : records) {
                int  slot   = head % REDO_FILE_CAPACITY;
                long offset = (long) slot * REDO_BLOCK_SIZE;
                raf.seek(offset);

                ByteBuffer buf = ByteBuffer.allocate(REDO_BLOCK_SIZE);
                buf.putLong(r.lsn);   // offset  0: lsn (8B)
                buf.putLong(r.txId);  // offset  8: txId (8B)
                int flags = (r.isCommit      ? 1 : 0)
                          | (r.isFlushed     ? 2 : 0)
                          | (r.isRedoForUndo ? 4 : 0)
                          | (r.isPrepare     ? 8 : 0);
                buf.putInt(flags);    // offset 16: flags (4B)

                if (r.isRedoForUndo && r.undoRecord != null) {
                    // Redo for Undo 布局（MLOG_UNDO_INSERT）
                    buf.putInt(r.undoRecord.undoNo);                           // offset 20: undoNo (4B)
                    byte[] ukb = new byte[24];
                    if (r.undoRecord.key != null) {
                        byte[] kb = r.undoRecord.key.getBytes(StandardCharsets.UTF_8);
                        System.arraycopy(kb, 0, ukb, 0, Math.min(kb.length, 24));
                    }
                    buf.put(ukb);                                              // offset 24: undoKey (24B)
                    buf.putInt(r.undoRecord.oldValue);                         // offset 48: oldValue (4B)
                    buf.putInt((int) r.undoRecord.rollPointer);                // offset 52: rollPtr 低32位 (4B)
                    buf.putLong(0L);                                           // offset 56: padding (8B)
                } else {
                    // 普通数据日志布局
                    byte[] keyBytes = new byte[32];
                    if (r.key != null) {
                        byte[] kb = r.key.getBytes(StandardCharsets.UTF_8);
                        System.arraycopy(kb, 0, keyBytes, 0, Math.min(kb.length, 32));
                    }
                    buf.put(keyBytes);   // offset 20: key (32B)
                    buf.putInt(r.newValue);  // offset 52: newValue (4B)
                    buf.putLong(0L);         // offset 56: padding (8B)
                }
                raf.write(buf.array());
                head++;
            }
            // fdatasync：只同步数据不同步 inode 元数据，比 fsync 略快，是 OLTP 性能瓶颈
            raf.getFD().sync();
        } catch (IOException e) {
            throw new RuntimeException("写 ib_logfile0 失败", e);
        }
    }

    /**
     * 从 ib_logfile0 读取全部有效 Redo Block 到内存（崩溃恢复时调用）
     *
     * 真实：从 checkpoint_lsn 位置开始扫描，按 LSN 顺序逐条重放；
     * 每块都做 CRC32 校验，校验失败认为日志文件在此处截断。
     * 本 Demo 简化：全量扫描所有 slot，跳过空块（lsn=txId=0 的块是未写区域）。
     */
    public static List<RedoLogRecord> readRedoBlocksFromDisk() {
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
                long lsn   = bb.getLong();  // offset  0
                long txId  = bb.getLong();  // offset  8
                int  flags = bb.getInt();   // offset 16

                if (lsn == 0 && txId == 0) continue; // 空 slot，跳过

                boolean isCommit      = (flags & 1) != 0;
                boolean isFlushed     = (flags & 2) != 0;
                boolean isRedoForUndo = (flags & 4) != 0;
                boolean isPrepare     = (flags & 8) != 0;

                RedoLogRecord r;
                if (isRedoForUndo) {
                    int    undoNo = bb.getInt();         // offset 20: undoNo (4B)
                    byte[] ukRaw  = new byte[24];
                    bb.get(ukRaw);                       // offset 24: undoKey (24B)
                    int    oldVal = bb.getInt();         // offset 48: oldValue (4B)
                    int    rollLo = bb.getInt();         // offset 52: rollPtr 低32位
                    int ukLen = 0;
                    while (ukLen < 24 && ukRaw[ukLen] != 0) ukLen++;
                    String undoKey = new String(ukRaw, 0, ukLen, StandardCharsets.UTF_8);
                    UndoLogRecord undoRec = new UndoLogRecord(txId, undoNo, undoKey, oldVal,
                            UndoLogRecord.UndoType.UPDATE, (long) rollLo);
                    r = RedoLogRecord.redoForUndo(lsn, txId, undoRec);
                } else {
                    byte[] keyBytes = new byte[32];
                    bb.get(keyBytes);            // offset 20: key (32B)
                    int    newVal   = bb.getInt(); // offset 52: newValue (4B)
                    String key = null;
                    if (!isCommit) {
                        int len = 0;
                        while (len < 32 && keyBytes[len] != 0) len++;
                        key = new String(keyBytes, 0, len, StandardCharsets.UTF_8);
                    }
                    r           = new RedoLogRecord(lsn, txId, key, newVal);
                    r.isCommit  = isCommit;
                    r.isPrepare = isPrepare;  // 必须还原 isPrepare 标志（崩溃恢复 2PC Check 依赖此字段）
                }
                r.isFlushed = isFlushed;
                list.add(r);
            }
        } catch (IOException e) {
            throw new RuntimeException("读 ib_logfile0 失败", e);
        }
        return list;
    }

    /**
     * 把 ib_logfile0 中属于 txId 的 block 的 isFlushed 位清零
     *
     * 用于模拟 innodb_flush_log_at_trx_commit=2 时
     * "只 write 到 OS cache，没有 fsync"的状态。
     * OS 崩溃后 page cache 丢失，相当于这些 block 从未落过盘。
     *
     * ★ 面试知识点：
     *   "innodb_flush_log_at_trx_commit=2 时，COMMIT 只把日志写到 OS 的 page cache，
     *    不立即 fsync。MySQL 进程崩溃（killed）不丢数据，因为 page cache 还在；
     *    但机器断电/OS 崩溃会丢失最近约 1 秒内的已提交事务。
     *    金融场景必须用 =1，普通日志类业务可以用 =2 换取更高写入性能。"
     */
    public static void markRedoBlocksUnflushed(long txId) {
        try (RandomAccessFile raf = new RandomAccessFile(REDO_FILE, "rw")) {
            for (int i = 0; i < REDO_FILE_CAPACITY; i++) {
                long offset = (long) i * REDO_BLOCK_SIZE;
                if (offset + REDO_BLOCK_SIZE > raf.length()) break;
                raf.seek(offset);
                byte[] buf = new byte[REDO_BLOCK_SIZE];
                raf.readFully(buf);
                ByteBuffer bb  = ByteBuffer.wrap(buf);
                bb.getLong(); // lsn
                long tid = bb.getLong();
                if (tid == txId) {
                    int flags = bb.getInt() & ~2; // 清 bit1（isFlushed）
                    ByteBuffer out = ByteBuffer.wrap(buf);
                    out.getLong();
                    out.getLong();
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

    // ==================== ib_logfile_header 读写 ====================

    /**
     * 持久化 checkpoint_lsn 和 write_head 到 ib_logfile_header 文件
     *
     * ★ 面试知识点（checkpoint 是什么）：
     *   "checkpoint_lsn 代表'我保证这个 LSN 之前的数据页都已经写盘了'。
     *    checkpoint 推进后，ib_logfile0 里 ≤ checkpoint_lsn 的那部分日志就没用了，
     *    可以被新日志覆盖，这样 Redo Log 的循环文件才能持续使用。
     *    Page Cleaner 每次刷完一批脏页，就推进一次 checkpoint。"
     *
     * 真实：写在 ib_logfile 文件头的 LOG_CHECKPOINT_1/2 两个页（512B），
     * 交替写，防止写文件头时崩溃导致两个 checkpoint 都损坏。
     */
    public static void saveRedoHeader(long checkpointLsn, int redoWriteHead) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(REDO_HEADER_FILE, false))) {
            pw.println("checkpoint_lsn=" + checkpointLsn);
            pw.println("write_head="     + redoWriteHead);
            pw.flush();
        } catch (IOException e) {
            throw new RuntimeException("写 ib_logfile_header 失败", e);
        }
    }

    /**
     * 从 ib_logfile_header 读取 checkpoint_lsn 和 write_head
     * 返回 long[]{checkpointLsn, writeHead}
     *
     * 崩溃恢复的第一步就是读这个文件，确定从哪个 LSN 开始重放 Redo Log。
     * 真实：选 LOG_CHECKPOINT_1/2 中 checkpoint_no 更大（更新）且 CRC 校验通过的那个。
     */
    public static long[] loadRedoHeader() {
        long[] result = {0L, 0L};
        Path p = Paths.get(REDO_HEADER_FILE);
        if (!Files.exists(p)) return result;
        try {
            for (String line : Files.readAllLines(p)) {
                if (line.startsWith("checkpoint_lsn="))
                    result[0] = Long.parseLong(line.split("=")[1]);
                if (line.startsWith("write_head="))
                    result[1] = Long.parseLong(line.split("=")[1]);
            }
        } catch (IOException e) {
            throw new RuntimeException("读 ib_logfile_header 失败", e);
        }
        return result;
    }

    // ==================== binlog 读写 ====================

    /**
     * 将 Binlog 事件列表追加写入 binlog 文件，按 doFsync 决定是否立即 fsync
     *
     * ★ 面试知识点（Binlog 写入时序）：
     *   "事务执行期间，行变更先写到线程私有的 binlog_cache（内存）；
     *    COMMIT 时，才把整个 Cache（BEGIN + ROW_CHANGE×N + XID_COMMIT）
     *    一次性 write 到 binlog 文件，sync_binlog=1 时再 fsync。
     *    这样保证了 Binlog 里的事务是完整的，不会有'写一半'的情况。"
     *
     * @param entries 要写入的事件列表（通常是一个完整事务：BEGIN + ROW×N + XID_COMMIT）
     * @param doFsync true = sync_binlog=1（立即 fsync）；false = 只写 OS cache
     */
    public static void writeBinlogEntries(List<BinlogEntry> entries, boolean doFsync) {
        try (FileWriter fw = new FileWriter(BINLOG_FILE, true)) {
            for (BinlogEntry e : entries) {
                e.isFlushed = doFsync;
                fw.write(e.serialize() + "\n");
                System.out.println("  [binlog]     写入: " + e);
            }
            fw.flush();
            if (doFsync) {
                System.out.println("  [binlog]     fsync 完成 (sync_binlog=1) ← 事务提交的真正分界线");
            } else {
                System.out.println("  [binlog]     仅写 OS cache，未 fsync (sync_binlog=0)，OS 崩溃可能丢 XID");
            }
        } catch (IOException e) {
            throw new RuntimeException("写 binlog 失败", e);
        }
    }

    /**
     * 从 binlog 文件全量读取所有事件
     *
     * 真实：从库通过 IO thread 按 file:offset 游标增量拉取，不是全量读；
     * 崩溃恢复时从文件末尾向前扫描，找最后一个完整的 XID_COMMIT 事件。
     */
    public static List<BinlogEntry> loadBinlogFromDisk() {
        List<BinlogEntry> list = new ArrayList<>();
        Path p = Paths.get(BINLOG_FILE);
        if (!Files.exists(p)) return list;
        try {
            for (String line : Files.readAllLines(p)) {
                if (!line.trim().isEmpty()) list.add(BinlogEntry.deserialize(line.trim()));
            }
        } catch (IOException e) {
            throw new RuntimeException("读 binlog 失败", e);
        }
        return list;
    }

    /**
     * 从 Binlog 文件中提取所有已落盘 XID_COMMIT 的 txId 集合（崩溃恢复时使用）
     *
     * ★ 面试知识点（崩溃恢复时 Binlog 怎么用）：
     *   "崩溃恢复时，InnoDB 把所有处于 PREPARE 状态的事务 XID 告诉 Server 层，
     *    Server 层扫描 Binlog 文件，返回其中有 XID_COMMIT 事件的 txId 集合。
     *    InnoDB 对照：有 XID → 补提交；没有 XID → 回滚。
     *    这样就保证了，无论在两阶段提交的哪个步骤崩溃，主从数据都一致。"
     */
    public static Set<Long> loadCommittedXidsFromBinlog() {
        Set<Long> xids = new HashSet<>();
        for (BinlogEntry e : loadBinlogFromDisk()) {
            if (e.type == BinlogEntry.Type.XID_COMMIT && e.isFlushed) {
                xids.add(e.txId);
            }
        }
        return xids;
    }
}

