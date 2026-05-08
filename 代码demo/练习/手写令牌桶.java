package 练习;

import java.util.concurrent.atomic.AtomicInteger;

public class 手写令牌桶 {

    // 最大令牌数
    private int maxTokens;

    // 每秒钟生成多少个令牌
    private int tokenRate;

    // 当前令牌数
    private AtomicInteger curTokens;

    private long lastTime;

    public 手写令牌桶(int maxTokens, int tokenRate) {
        this.maxTokens = maxTokens;
        this.tokenRate = tokenRate;
        this.curTokens = new AtomicInteger(maxTokens);
    }

    public boolean tryAcquire(int tokens) {
        if(tokens <= 0 || tokens > maxTokens) {
            throw new RuntimeException("令牌桶传参不合理");
        }
        //拿出当前有多少令牌
        int curTokenCount = curTokens.get();
        while(curTokenCount >= tokens) {
            //保证从判断，到真正减去的时候，这个值是没变的
            //todo 核心：比数和set数是两个动作，不是原子的
            if(curTokens.compareAndSet(curTokenCount, curTokenCount - tokens)) {
                return true;
            }
            //刷新成最新值，再进行判断
            curTokenCount = curTokens.get();
        }
        //没够扣
        return false;
    }

    // 定时任务，每秒生成一个令牌
    // 即使设定了时间，每次还是判断上一次生成令牌的时间，补充令牌
    public void addToken() {

    }
}
