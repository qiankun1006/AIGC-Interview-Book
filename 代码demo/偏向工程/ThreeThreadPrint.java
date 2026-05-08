package 偏向工程;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ThreeThreadPrint {

    //只新建一个lock锁
    private ReentrantLock lock = new ReentrantLock();

    // 新建三个condition
    private List<Condition> conditions;
    private int num = 1;

    private int count;

    ThreeThreadPrint(int count) {
        this.count = count;
        conditions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            conditions.add(lock.newCondition());
        }
    }

    public void print(int index, int remain) {
        //先加锁，后面判断如果现在不应该打印147会await释放
        lock.lock();
        try {
            //会进入循环一直打印，直到打完
            while(true) {
                //如果没轮到这个方法，就睡眠
                while(num % count != remain) {
                    conditions.get(index).await();
                }
                //超过10，就不用打印了，但是num还得++，不然其他线程及时被唤醒了，会因为num没满足继续循环沉睡
                if(num >= 100) {
                    num++;
                    break;
                }
                System.out.println(Thread.currentThread().getName() + " " + num);
                num++;
                //唤醒下一个，轮到下一个打印了
                conditions.get((index + 1) % count).signal();
            }
            //其余的全唤醒，防止死锁
            for(Condition condition : conditions) {
                if(condition != conditions.get(index)) {
                    condition.signal();
                }
            }
        } catch (Exception e) {
        } finally {
            //都打完了，lock也可以释放了
            lock.unlock();
        }
    }

    public static void main(String[] args) {
        int count = 7;
        ThreeThreadPrint threeThreadPrint = new ThreeThreadPrint(count);
        for(int i = 0; i < count; i++) {
            // 定义临时变量，final 修饰（或不加final，只要值不变就是effectively final）
            final int tempIndex = i;
            // Lambda 引用 tempIndex（值固定，满足规则）
            new Thread(() -> threeThreadPrint.print(tempIndex, (tempIndex + 1) % count), "线程" + (char)('A' + tempIndex)).start();
        }
    }

}
