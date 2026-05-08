package 偏向工程;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class 交替打印 {

    private ReentrantLock lock = new ReentrantLock();

    private List<Condition> conditionList;

    private char[] chars;

    private int threadNum;

    //只打印两轮
    private int num = 0;

    private int targetNum = 8;

    //定义好，几个线程，总共几个字符串，要打印多少个字符
    public 交替打印(int threadNum, int charCount, int targetNum) {
        this.threadNum = threadNum;
        this.targetNum = targetNum;
        // 给每个线程创建相应的condition
        conditionList = new ArrayList<>();
        for(int i = 0; i < threadNum; i++) {
            conditionList.add(lock.newCondition());
        }
        chars = new char[charCount];
        for(int i = 0; i < charCount; i++) {
            chars[i] = (char)('A' + i);
        }
    }

    public void print(int threadIndex) {
        lock.lock();
        try {
            while(true) {
                while(num % threadNum == threadIndex) {
                    conditionList.get(threadIndex).await();
                }
                System.out.println("线程" + threadIndex + "打印" + chars[num % chars.length]);
                num++;
                if(num > targetNum) {
                    break;
                }
                //唤醒下一个
                conditionList.get((threadIndex + 1) % threadNum).signal();
            }
            //唤醒其余所有线程
            for(int i = 0; i < threadNum; i++) {
                if(i != threadIndex) {
                    conditionList.get(i).signal();
                }
            }
        } catch (Exception e) {

        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) {
        int threadNum = 3;
        int charCount = 5;
        int targetNum = 40;
        交替打印 print = new 交替打印(threadNum, charCount, targetNum);
        for(int i = 0; i < threadNum; i++) {
            final int index = i;
            new Thread(() -> {
                print.print(index);
            }).start();
        }
    }
}
