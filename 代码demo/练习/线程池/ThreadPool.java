package 练习.线程池;

import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;

//手写线程池
public class ThreadPool {

    private int queueMaxSize;

    private Deque<Runnable> taskQueue;

    private int coreSize;

    // 线程池本体
    private Work[] works;

    // 线程池是否关闭
    private AtomicBoolean isShutDown = new AtomicBoolean(false);

    public static class Work extends Thread {

        @Override
        public void run() {

        }
    }
}
