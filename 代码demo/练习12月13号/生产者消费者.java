package 练习12月13号;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 实现一个「多生产者 - 多消费者」的商品交易系统，包含以下约束：
 * 仓库有容量上限（如 20），且商品分 3 种类型（A、B、C），每种类型有独立的库存上限（如各 8 个）；
 * 生产者按规则生产：优先补充库存最少的商品类型，若类型库存均达上限则阻塞；生产时需记录生产批次、时间戳；
 * 消费者按规则消费：随机选择一种商品类型，若该类型库存为 0 则阻塞；消费时需校验商品批次有效性（生产超过 10 秒的商品视为过期，禁止消费）；
 * 系统需支持动态调整仓库总容量（运行时可修改），调整时需保证线程安全；
 * 需打印关键日志（生产 / 消费操作、阻塞 / 唤醒、库存变化、过期校验）；
 * 生产者 / 消费者线程需优雅停止（支持外部触发终止）。
 */
public class 生产者消费者 {

    private ReentrantLock lock = new ReentrantLock();

    private Condition produceCondition = lock.newCondition();

    private Condition consumerCondition = lock.newCondition();

    //上限
    private Map<String, Integer> capacityMap;
    //当前详细信息
    private Map<String, LinkedList<Goods>> inventory;

    private AtomicBoolean shutDown;

    public 生产者消费者(Map<String, Integer> capacityMap) {
        capacityMap = capacityMap;
        inventory = new HashMap<>();
        shutDown = new AtomicBoolean(false);
    }

    public static class Goods {
        private String type;
        private long timestamp;

        public Goods(String type, long timestamp) {
            this.type = type;
            this.timestamp = timestamp;
        }
    }

    private void producer() {
        //先锁上，后面看怎么优化
        lock.lock();
        //判断哪种类型的商品最少
        try {
            while (true) {
                String type = getMinType();
                while(type == null) {
                    //释放锁
                    produceCondition.await();
                    type = getMinType();
                }
                if(shutDown.get()) {
                    break;
                }
                LinkedList<Goods> goodsList = inventory.get(type);
                goodsList.add(new Goods(type, System.currentTimeMillis()));
                //通知所有生产者消费者，可以来抢了
                consumerCondition.signalAll();
            }
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    private void consumer(String threadName) {
        //先锁上，后面看怎么优化
        lock.lock();
        try {
            while (true) {
                String type = getRandomType();
                while(type == null) {
                    //释放锁
                    consumerCondition.await();
                    type = getRandomType();
                }
                if(shutDown.get()) {
                    break;
                }
                LinkedList<Goods> goodsList = inventory.get(type);
                for(Goods goods : goodsList) {
                    if(goods.timestamp + 10000 < System.currentTimeMillis()) {
                        goodsList.remove(goods);
                    }
                }
                if(!goodsList.isEmpty()) {
                    Goods goods = goodsList.poll();
                    System.out.println(threadName + "消费了一个" + goods.type);
                    produceCondition.signalAll();
                    continue;
                }
                //通知所有生产者消费者，可以来抢了
                produceCondition.signalAll();
            }
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    private String getMinType() {
        int min = Integer.MAX_VALUE;
        String type = null;
        for (String key : capacityMap.keySet()) {
            //比最小的小，且没达到上限
            if (inventory.get(key).size() < min && inventory.get(key).size() < capacityMap.get(key)) {
                min = inventory.get(key).size();
                type = key;
            }
        }
        return type;
    }

    private String getRandomType() {
        //先判断是不是都满了，从没满的里面随机取一个
        List<String> type = new ArrayList<>();
        for (String key : capacityMap.keySet()) {
            //比最小的小，且没达到上限
            if (inventory.get(key).size() < capacityMap.get(key)) {
                type.add(key);
            }
        }
        if(type.isEmpty()) {
            return null;
        }
        return type.get(new Random().nextInt(type.size()));
    }

    public static void main(String[] args) {

    }


}
