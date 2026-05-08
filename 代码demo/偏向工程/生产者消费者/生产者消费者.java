package 偏向工程.生产者消费者;

/**
 * 实现一个「多生产者 - 多消费者」的商品交易系统，包含以下约束：
 * 仓库有容量上限（如 20），且商品分 3 种类型（A、B、C），每种类型有独立的库存上限（如各 8 个）；
 * 生产者按规则生产：优先补充库存最少的商品类型，若类型库存均达上限则阻塞；生产时需记录生产批次、时间戳；
 * 消费者按规则消费：随机选择一种商品类型，若该类型库存为 0 则阻塞；消费时需校验商品批次有效性（生产超过 10 秒的商品视为过期，禁止消费）；
 * 系统需支持动态调整仓库总容量（运行时可修改），调整时需保证线程安全；
 * 需打印关键日志（生产 / 消费操作、阻塞 / 唤醒、库存变化、过期校验）；
 * 生产者 / 消费者线程需优雅停止（支持外部触发终止）。
 */
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 商品实体类：包含类型、批次、生产时间
 */
class Goods {
    // 商品类型
    public enum Type { A, B, C }
    private final Type type;
    // 生产批次（格式：类型-时间戳-序号）
    private final String batchNo;
    // 生产时间（毫秒）
    private final long produceTime;

    public Goods(Type type, String batchNo) {
        this.type = type;
        this.batchNo = batchNo;
        this.produceTime = System.currentTimeMillis();
    }

    public Type getType() { return type; }
    public String getBatchNo() { return batchNo; }
    public long getProduceTime() { return produceTime; }

    // 判断商品是否过期（生产超过10秒）
    public boolean isExpired() {
        return System.currentTimeMillis() - produceTime > 10000;
    }
}

/**
 * 仓库类：核心逻辑实现（库存管理、生产/消费规则、容量调整）
 */
class Warehouse {
    // 仓库总容量（支持动态调整）
    private volatile int totalCapacity;
    // 每种商品的库存上限
    private static final int TYPE_MAX_CAPACITY = 8;
    // 库存：key=商品类型，value=商品列表
    private final Map<Goods.Type, Deque<Goods>> inventory = new HashMap<>();
    // 锁（保证线程安全）
    private final Lock lock = new ReentrantLock();
    // 生产条件：仓库有剩余容量
    private final Condition produceCondition = lock.newCondition();
    // 消费条件：有可消费的商品
    private final Condition consumeCondition = lock.newCondition();
    // 统计信息
    private int produceTotal = 0;    // 总生产量
    private int consumeTotal = 0;    // 总消费量
    private int expiredTotal = 0;    // 过期商品数
    // 批次序号生成器（按类型）
    private final Map<Goods.Type, Integer> batchSeq = new HashMap<>();

    public Warehouse(int initialTotalCapacity) {
        this.totalCapacity = initialTotalCapacity;
        // 初始化库存和批次序号
        for (Goods.Type type : Goods.Type.values()) {
            inventory.put(type, new ArrayDeque<>());
            batchSeq.put(type, 1);
        }
    }

    /**
     * 生产商品：优先补充库存最少的类型
     */
    public Goods produce() throws InterruptedException {
        lock.lock();
        try {
            // 1. 检查是否可生产：总库存未满 且 存在类型库存未达上限
            while (getCurrentTotalInventory() >= totalCapacity || isAllTypeFull()) {
                log("生产者阻塞：仓库总容量已满(" + getCurrentTotalInventory() + "/" + totalCapacity + ") 或 所有类型库存达上限");
                produceCondition.await(); // 阻塞生产者
            }

            // 2. 选择库存最少的商品类型
            Goods.Type targetType = selectMinInventoryType();
            Deque<Goods> typeInventory = inventory.get(targetType);

            // 3. 生成商品（批次号：类型-时间戳-序号）
            String batchNo = targetType + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + batchSeq.get(targetType);
            batchSeq.put(targetType, batchSeq.get(targetType) + 1);
            Goods goods = new Goods(targetType, batchNo);

            // 4. 加入库存
            typeInventory.offer(goods);
            produceTotal++;

            // 5. 打印日志
            log("生产商品：类型=" + targetType + "，批次=" + batchNo + "，当前类型库存=" + typeInventory.size() + "，总库存=" + getCurrentTotalInventory());

            // 6. 唤醒消费者（有新商品）
            consumeCondition.signalAll();
            return goods;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 消费商品：随机选择类型，校验过期
     */
    public Goods consume() throws InterruptedException {
        lock.lock();
        try {
            // 1. 检查是否可消费：存在非空且未过期的商品
            while (!hasAvailableGoods()) {
                log("消费者阻塞：无可用商品（所有类型库存为空或全过期）");
                consumeCondition.await(); // 阻塞消费者
            }

            // 2. 随机选择商品类型（仅选有库存的）
            List<Goods.Type> availableTypes = getAvailableTypes();
            Goods.Type targetType = availableTypes.get(new Random().nextInt(availableTypes.size()));
            Deque<Goods> typeInventory = inventory.get(targetType);

            // 3. 校验并消费商品（先进先出，避免过期）
            Goods goods = null;
            while (goods == null && !typeInventory.isEmpty()) {
                Goods temp = typeInventory.peekFirst();
                if (temp.isExpired()) {
                    // 过期商品：移除并统计
                    typeInventory.pollFirst();
                    expiredTotal++;
                    log("消费校验：商品类型=" + targetType + "，批次=" + temp.getBatchNo() + " 已过期，移除");
                } else {
                    // 有效商品：消费
                    goods = typeInventory.pollFirst();
                    consumeTotal++;
                    log("消费商品：类型=" + targetType + "，批次=" + goods.getBatchNo() + "，当前类型库存=" + typeInventory.size() + "，总库存=" + getCurrentTotalInventory());
                }
            }

            // 4. 唤醒生产者（库存有空位）
            produceCondition.signalAll();
            return goods;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 动态调整仓库总容量
     */
    public void adjustTotalCapacity(int newCapacity) {
        lock.lock();
        try {
            if (newCapacity < 0) {
                throw new IllegalArgumentException("容量不能为负数");
            }
            this.totalCapacity = newCapacity;
            log("仓库总容量调整为：" + newCapacity);
            // 调整后唤醒可能阻塞的生产者/消费者
            produceCondition.signalAll();
            consumeCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 停止所有线程（唤醒所有阻塞的线程）
     */
    public void stop() {
        lock.lock();
        try {
            produceCondition.signalAll();
            consumeCondition.signalAll();
            log("仓库停止：唤醒所有阻塞的生产者/消费者");
        } finally {
            lock.unlock();
        }
    }

    /**
     * 打印统计信息
     */
    public void printStats() {
        lock.lock();
        try {
            System.out.println("\n===== 系统统计 =====");
            System.out.println("总生产量：" + produceTotal);
            System.out.println("总消费量：" + consumeTotal);
            System.out.println("过期商品数：" + expiredTotal);
            System.out.println("剩余库存：");
            for (Goods.Type type : Goods.Type.values()) {
                System.out.println("  " + type + "：" + inventory.get(type).size() + " 个");
            }
        } finally {
            lock.unlock();
        }
    }

    // ---------------- 私有辅助方法 ----------------
    // 获取当前总库存
    private int getCurrentTotalInventory() {
        return inventory.values().stream().mapToInt(Deque::size).sum();
    }

    // 判断所有类型库存是否达上限
    private boolean isAllTypeFull() {
        return inventory.values().stream().allMatch(list -> list.size() >= TYPE_MAX_CAPACITY);
    }

    // 选择库存最少的商品类型（优先选未达上限的）
    private Goods.Type selectMinInventoryType() {
        return inventory.entrySet().stream()
                .filter(entry -> entry.getValue().size() < TYPE_MAX_CAPACITY)
                .min(Comparator.comparingInt(entry -> entry.getValue().size()))
                .map(Map.Entry::getKey)
                .orElse(Goods.Type.A); // 兜底（理论上不会走到）
    }

    // 判断是否有可消费的商品（非空且存在未过期）
    private boolean hasAvailableGoods() {
        for (Deque<Goods> list : inventory.values()) {
            if (!list.isEmpty() && list.stream().anyMatch(g -> !g.isExpired())) {
                return true;
            }
        }
        return false;
    }

    // 获取有可消费商品的类型列表
    private List<Goods.Type> getAvailableTypes() {
        List<Goods.Type> types = new ArrayList<>();
        for (Goods.Type type : Goods.Type.values()) {
            Deque<Goods> list = inventory.get(type);
            if (!list.isEmpty() && list.stream().anyMatch(g -> !g.isExpired())) {
                types.add(type);
            }
        }
        return types;
    }

    // 日志打印（带时间戳和线程名）
    private void log(String msg) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
        System.out.printf("[%s] [%s] %s%n", time, Thread.currentThread().getName(), msg);
    }
}

/**
 * 生产者线程
 */
class Producer extends Thread {
    private final Warehouse warehouse;
    private volatile boolean running = true;

    public Producer(Warehouse warehouse, String name) {
        super(name);
        this.warehouse = warehouse;
    }

    @Override
    public void run() {
        while (running) {
            try {
                warehouse.produce();
                // 模拟生产耗时（100-500ms）
                Thread.sleep(new Random().nextInt(400) + 100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("被中断，准备退出");
                running = false;
            }
        }
        log("已退出");
    }

    public void stopProduce() {
        running = false;
        this.interrupt(); // 唤醒阻塞的线程
    }

    private void log(String msg) {
        System.out.printf("[%s] [%s] %s%n", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")), getName(), msg);
    }
}

/**
 * 消费者线程
 */
class Consumer extends Thread {
    private final Warehouse warehouse;
    private volatile boolean running = true;

    public Consumer(Warehouse warehouse, String name) {
        super(name);
        this.warehouse = warehouse;
    }

    @Override
    public void run() {
        while (running) {
            try {
                warehouse.consume();
                // 模拟消费耗时（200-600ms）
                Thread.sleep(new Random().nextInt(400) + 200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("被中断，准备退出");
                running = false;
            }
        }
        log("已退出");
    }

    public void stopConsume() {
        running = false;
        this.interrupt(); // 唤醒阻塞的线程
    }

    private void log(String msg) {
        System.out.printf("[%s] [%s] %s%n", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")), getName(), msg);
    }
}

/**
 * 主程序：测试多生产者-多消费者场景
 */
public class 生产者消费者 {
    public static void main(String[] args) throws InterruptedException {
        // 1. 初始化仓库（初始总容量20）
        Warehouse warehouse = new Warehouse(20);

        // 2. 创建生产者线程（3个）
        Producer p1 = new Producer(warehouse, "生产者-1");
        Producer p2 = new Producer(warehouse, "生产者-2");
        Producer p3 = new Producer(warehouse, "生产者-3");

        // 3. 创建消费者线程（4个）
        Consumer c1 = new Consumer(warehouse, "消费者-1");
        Consumer c2 = new Consumer(warehouse, "消费者-2");
        Consumer c3 = new Consumer(warehouse, "消费者-3");
        Consumer c4 = new Consumer(warehouse, "消费者-4");

        // 4. 启动线程
        p1.start();
        p2.start();
        p3.start();
        c1.start();
        c2.start();
        c3.start();
        c4.start();

        // 5. 运行5秒后，动态调整仓库容量为15
        Thread.sleep(5000);
        log("动态调整仓库总容量为15");
        warehouse.adjustTotalCapacity(15);

        // 6. 运行10秒后，停止所有生产者
        Thread.sleep(10000);
        log("停止所有生产者");
        p1.stopProduce();
        p2.stopProduce();
        p3.stopProduce();

        // 7. 等待消费者消费完剩余商品（5秒）
        Thread.sleep(5000);
        log("停止所有消费者");
        c1.stopConsume();
        c2.stopConsume();
        c3.stopConsume();
        c4.stopConsume();

        // 8. 等待所有线程退出
        p1.join();
        p2.join();
        p3.join();
        c1.join();
        c2.join();
        c3.join();
        c4.join();

        // 9. 打印统计信息
        warehouse.printStats();
    }

    private static void log(String msg) {
        System.out.printf("[%s] [主线程] %s%n", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")), msg);
    }
}
