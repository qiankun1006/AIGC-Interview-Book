package 偏向工程;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 令牌桶限流工具
 * 核心：固定速率生成令牌，支持突发流量，线程安全无锁实现
 */
public class 令牌桶限流 {

    // 桶的最大令牌数（突发流量上限）
    private long maxTokens;

    // 令牌生成速率（令牌/秒）
    private double tokenRate;

    // 最后一次令牌补充时间（毫秒）
    private AtomicLong lastRefillTime;

    // 当前桶内令牌数
    private AtomicLong currentTokens;

    /**
     * 构造令牌桶
     * @param maxTokens 桶最大容量（支持的最大突发请求数）
     * @param tokenRate 令牌生成速率（令牌/秒）
     */
    public 令牌桶限流(long maxTokens, double tokenRate) {
        this.maxTokens = maxTokens;
        this.tokenRate = tokenRate;
        this.lastRefillTime = new AtomicLong(System.currentTimeMillis());
        this.currentTokens = new AtomicLong(maxTokens); // 初始桶满
    }

    /**
     * 尝试获取令牌（非阻塞）
     * @param tokens 需要的令牌数（通常1个）
     * @return true=获取成功，false=无令牌
     */
    public boolean tryAcquire(long tokens) {
        if (tokens <= 0 || tokens > maxTokens) {
            throw new IllegalArgumentException("令牌数需在(0, " + maxTokens + "]范围内");
        }

        // 1. 先补充令牌（按时间差计算应生成的令牌数）
        refillTokens();

        // 2. 尝试扣减令牌（CAS保证线程安全）
        long current = currentTokens.get();
        while (current >= tokens) {
            if (currentTokens.compareAndSet(current, current - tokens)) {
                return true;
            }
            current = currentTokens.get(); // CAS失败，重新读取
        }
        return false;
    }


    /**
     * 补充令牌（核心逻辑：按时间差计算应生成的令牌数）
     */
    //@Scheduled(fixedRate = 1000)
    private void refillTokens() {
        long now = System.currentTimeMillis();
        long last = lastRefillTime.get();
        // 时间差（秒）
        double timeDiff = (now - last) / 1000.0;
        // 应生成的令牌数
        long tokensToAdd = (long) (timeDiff * tokenRate);

        // 无令牌可加，直接返回
        if (tokensToAdd <= 0) {
            return;
        }

        // CAS更新最后补充时间（避免多线程重复补充）
        if (lastRefillTime.compareAndSet(last, now)) {
            // 补充令牌（不超过桶上限）
            currentTokens.updateAndGet(current ->
                    Math.min(current + tokensToAdd, maxTokens)
            );
        }
    }

    // 测试方法
    public static void main(String[] args) throws InterruptedException {
        // 配置：桶最大10个令牌，每秒生成2个令牌
        令牌桶限流 limiter = new 令牌桶限流(10, 2);

        // 测试突发流量（前10次直接通过）
        for (int i = 1; i <= 12; i++) {
            boolean success = limiter.tryAcquire(1);
            System.out.println("第" + i + "次请求：" + (success ? "通过" : "限流"));
        }

        // 等待2秒，补充令牌后再试
        TimeUnit.SECONDS.sleep(2);
        System.out.println("等待2秒后：" + limiter.tryAcquire(1)); // 通过
    }
}