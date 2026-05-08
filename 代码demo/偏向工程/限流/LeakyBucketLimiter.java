package 偏向工程.限流;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 漏桶限流完整实现（含main测试方法）
 */
public class LeakyBucketLimiter {
    // 桶的总容量
    private final long bucketCapacity;
    // 漏出速率（单位：请求/秒）
    private final double leakRatePerSecond;
    // 当前桶内的水量（请求数）
    private final AtomicLong currentWater;
    // 上次漏水时间戳（纳秒）
    private final AtomicLong lastLeakTimestamp;

    /**
     * 构造漏桶限流器
     * @param bucketCapacity 桶容量（最大堆积请求数）
     * @param leakRatePerSecond 漏出速率（每秒处理请求数）
     */
    public LeakyBucketLimiter(long bucketCapacity, double leakRatePerSecond) {
        if (bucketCapacity <= 0 || leakRatePerSecond <= 0) {
            throw new IllegalArgumentException("桶容量和漏出速率必须大于0");
        }
        this.bucketCapacity = bucketCapacity;
        this.leakRatePerSecond = leakRatePerSecond;
        this.currentWater = new AtomicLong(0);
        this.lastLeakTimestamp = new AtomicLong(System.nanoTime());
    }

    /**
     * 尝试获取请求许可（非阻塞）
     * @return true：获取成功（请求可处理），false：桶满拒绝
     */
    public boolean tryAcquire() {
        // 1. 先执行漏水：计算从上一次到现在应该漏掉的水量
        long now = System.nanoTime();
        long lastTime = lastLeakTimestamp.get();
        long timeDiffNanos = now - lastTime;

        // 计算漏出的水量 = 时间差(秒) * 漏出速率
        double leakWater = (timeDiffNanos / 1_000_000_000.0) * leakRatePerSecond;
        if (leakWater > 0) {
            // 原子更新：先获取当前水量，再计算漏水后的水量（不能为负）
            currentWater.getAndUpdate(water -> {
                long newWater = Math.max(0, (long) (water - leakWater));
                return newWater;
            });
            // 更新最后漏水时间（仅当前线程成功更新，避免并发覆盖）
            lastLeakTimestamp.compareAndSet(lastTime, now);
        }

        // 2. 尝试加水（请求入桶）
        long current = currentWater.get();
        if (current < bucketCapacity) {
            // 桶未满，请求入桶（原子+1）
            return currentWater.compareAndSet(current, current + 1);
        } else {
            // 桶满，拒绝请求
            return false;
        }
    }

    /**
     * 阻塞获取请求许可（直到获取成功）
     * @throws InterruptedException 线程中断异常
     */
    public void acquire() throws InterruptedException {
        while (!tryAcquire()) {
            // 短暂休眠，避免自旋消耗CPU
            TimeUnit.MILLISECONDS.sleep(1);
        }
    }

    // 辅助方法：获取当前桶内水量
    public long getCurrentWater() {
        return currentWater.get();
    }

    // 测试主方法
    public static void main(String[] args) throws InterruptedException {
        // ========== 第一步：初始化漏桶限流器 ==========
        // 配置：桶容量5（最多堆积5个请求），漏出速率2请求/秒（每秒稳定处理2个）
        LeakyBucketLimiter limiter = new LeakyBucketLimiter(5, 2);

        // ========== 第二步：模拟多线程并发请求 ==========
        int totalRequest = 15; // 总请求数
        int threadCount = 10;  // 并发线程数
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(totalRequest);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        System.out.println("开始模拟 " + totalRequest + " 个请求，并发线程数：" + threadCount);
        for (int i = 0; i < totalRequest; i++) {
            executor.submit(() -> {
                try {
                    if (limiter.tryAcquire()) {
                        // 请求成功
                        int success = successCount.incrementAndGet();
                        System.out.printf("[%s] 请求成功 | 累计成功：%d | 当前桶内水量：%d%n",
                                Thread.currentThread().getName(), success, limiter.getCurrentWater());
                    } else {
                        // 请求失败（桶满）
                        int fail = failCount.incrementAndGet();
                        System.out.printf("[%s] 请求失败（桶满）| 累计失败：%d%n",
                                Thread.currentThread().getName(), fail);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有请求执行完成
        latch.await();
        executor.shutdown();

        // ========== 第三步：输出统计结果 ==========
        System.out.println("\n===== 第一轮请求统计 =====");
        System.out.println("总请求数：" + totalRequest);
        System.out.println("成功数：" + successCount.get());
        System.out.println("失败数：" + failCount.get());
        System.out.println("当前桶内水量：" + limiter.getCurrentWater());

        // ========== 第四步：验证漏水效果（时间流逝后桶内水量减少） ==========
        int sleepSeconds = 3;
        System.out.printf("\n等待 %d 秒，观察漏桶出水效果...%n", sleepSeconds);
        Thread.sleep(sleepSeconds * 1000);

        System.out.println("\n===== 漏水后状态 =====");
        System.out.println(sleepSeconds + "秒后桶内水量：" + limiter.getCurrentWater());

        // ========== 第五步：模拟第二轮请求（验证漏水后可再次处理请求） ==========
        int secondRequestCount = 5;
        CountDownLatch secondLatch = new CountDownLatch(secondRequestCount);
        AtomicInteger secondSuccess = new AtomicInteger(0);
        AtomicInteger secondFail = new AtomicInteger(0);

        System.out.printf("\n开始第二轮 %d 个请求（漏水后）%n", secondRequestCount);
        for (int i = 0; i < secondRequestCount; i++) {
            new Thread(() -> {
                try {
                    if (limiter.tryAcquire()) {
                        secondSuccess.incrementAndGet();
                        System.out.printf("[%s] 第二轮请求成功 | 当前桶内水量：%d%n",
                                Thread.currentThread().getName(), limiter.getCurrentWater());
                    } else {
                        secondFail.incrementAndGet();
                        System.out.printf("[%s] 第二轮请求失败（桶满）%n", Thread.currentThread().getName());
                    }
                } finally {
                    secondLatch.countDown();
                }
            }).start();
        }

        secondLatch.await();
        System.out.println("\n===== 第二轮请求统计 =====");
        System.out.println("第二轮请求数：" + secondRequestCount);
        System.out.println("第二轮成功数：" + secondSuccess.get());
        System.out.println("第二轮失败数：" + secondFail.get());
        System.out.println("最终桶内水量：" + limiter.getCurrentWater());
    }
}