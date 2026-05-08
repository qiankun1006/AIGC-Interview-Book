package 偏向工程.线程池;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 手写极简线程池（面试专用）
 */
public class MyThreadPool {
    // 1. 核心参数
    private final int corePoolSize; // 核心线程数
    private final Deque<Runnable> taskQueue; // 任务队列
    private final int queueCapacity; // 队列容量
    private final Worker[] workers; // 工作线程数组
    private final AtomicBoolean isShutdown = new AtomicBoolean(false); // 线程池是否关闭

    // 2. 拒绝策略（简化版：队列满抛异常）
    @FunctionalInterface
    public interface RejectedExecutionHandler {
        void reject(Runnable task);
    }
    private final RejectedExecutionHandler rejectHandler;

    // 3. 工作线程类（线程复用核心）
    private class Worker extends Thread {
        public Worker(String name) {
            super(name);
        }

        @Override
        public void run() {
            // 循环取任务执行（线程复用）：未关闭 或 队列还有任务
            while (!isShutdown.get() || !taskQueue.isEmpty()) {
                Runnable task = null;
                // 加锁取任务（保证队列线程安全）
                synchronized (taskQueue) {
                    try {
                        // 队列空且未关闭：等待任务
                        while (taskQueue.isEmpty() && !isShutdown.get()) {
                            taskQueue.wait();
                        }
                        // 队列空且已关闭：退出线程
                        if (taskQueue.isEmpty() && isShutdown.get()) {
                            break;
                        }
                        // 取出任务
                        task = taskQueue.pollFirst();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                // 执行任务（避免空指针）
                if (task != null) {
                    try {
                        task.run();
                    } catch (Exception e) {
                        System.err.println("任务执行异常：" + e.getMessage());
                    }
                }
            }
        }
    }

    // 4. 构造方法（初始化线程池）
    public MyThreadPool(int corePoolSize, int queueCapacity) {
        this.corePoolSize = corePoolSize;
        this.queueCapacity = queueCapacity;
        this.taskQueue = new ArrayDeque<>(queueCapacity);
        // 默认拒绝策略：抛异常
        this.rejectHandler = task -> {
            throw new RuntimeException("任务队列已满，拒绝执行任务：" + task);
        };
        // 初始化工作线程
        this.workers = new Worker[corePoolSize];
        for (int i = 0; i < corePoolSize; i++) {
            workers[i] = new Worker("工作线程-" + (i + 1));
            workers[i].start(); // 启动工作线程
        }
    }

    // 5. 提交任务（核心方法）
    public void execute(Runnable task) {
        // 校验线程池状态
        if (isShutdown.get()) {
            throw new RuntimeException("线程池已关闭，无法提交任务");
        }
        // 加锁提交任务
        synchronized (taskQueue) {
            // 队列未满：添加任务并唤醒工作线程
            if (taskQueue.size() < queueCapacity) {
                taskQueue.offerLast(task);
                taskQueue.notify(); // 唤醒等待的工作线程
            } else {
                // 队列满：执行拒绝策略
                rejectHandler.reject(task);
            }
        }
    }

    // 6. 优雅关闭线程池
    public void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            // 唤醒所有等待的工作线程
            synchronized (taskQueue) {
                taskQueue.notifyAll();
            }
            // 等待所有工作线程执行完成
            for (Worker worker : workers) {
                try {
                    worker.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            System.out.println("线程池已优雅关闭");
        }
    }

    // 7. 测试方法
    public static void main(String[] args) throws InterruptedException {
        // 初始化线程池：2个核心线程，队列容量3
        MyThreadPool threadPool = new MyThreadPool(2, 3);

        // 提交5个任务（2个直接执行，3个入队列，第6个触发拒绝策略）
        for (int i = 1; i <= 6; i++) {
            int taskId = i;
            try {
                threadPool.execute(() -> {
                    System.out.println(Thread.currentThread().getName() + " 执行任务" + taskId);
                    try {
                        TimeUnit.MILLISECONDS.sleep(500); // 模拟任务耗时
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            } catch (Exception e) {
                System.err.println("提交任务" + taskId + "失败：" + e.getMessage());
            }
        }

        // 等待任务执行
        TimeUnit.SECONDS.sleep(3);
        // 关闭线程池
        threadPool.shutdown();
    }
}