package ai题;

import java.util.*;


//https://www.doubao.com/thread/w978d8919bb65ecd1
public class 时间戳内统计topK {

    // 全局自增时间戳（模拟系统时钟，单位：秒）
    private long currentTimestamp = 0;
    // 商品ID -> 该商品的所有访问时间戳（双端队列，方便清理过期数据）
    private final Map<Integer, Deque<Long>> productTimeMap;
    // 商品ID -> 该商品在当前窗口内的访问频率
    private final Map<Integer, Integer> productFreqMap;
    // 访问频率 -> 对应频率的商品数量（TreeMap按频率降序排列，方便快速取TopK）
    private final TreeMap<Integer, Integer> freqCountMap;

    public 时间戳内统计topK() {
        productTimeMap = new HashMap<>();
        productFreqMap = new HashMap<>();
        // 降序排列，保证频率高的在前面
        freqCountMap = new TreeMap<>(Collections.reverseOrder());
    }

    /**
     * 记录商品访问行为
     *
     * @param productId 商品ID
     */
    public void record(int productId) {
        // 时间戳自增
        currentTimestamp++;
        // 1. 获取该商品的时间戳队列，不存在则创建
        Deque<Long> timeQueue = productTimeMap.computeIfAbsent(productId, k -> new ArrayDeque<>());
        // 2. 添加当前时间戳到队列
        timeQueue.offer(currentTimestamp);
        // 3. 更新商品的频率统计
        updateProductFrequency(productId);
    }

    /**
     * 统计最近window秒内访问次数第topK高的次数
     *
     * @param window 时间窗口（秒）
     * @param topK   取第topK高的次数
     * @return 第topK高的访问次数
     */
    public int getTopFrequency(int window, int topK) {
        // 边界条件：无效参数直接返回0
        if (window <= 0 || topK <= 0) {
            return 0;
        }

        // 1. 先清理所有商品的过期数据
        cleanExpiredData(window);

        // 2. 若没有有效频率，返回0
        if (freqCountMap.isEmpty()) {
            return 0;
        }

        // 3. 遍历TreeMap，收集前topK的频率
        int count = 0;
        int result = 0;
        for (Map.Entry<Integer, Integer> entry : freqCountMap.entrySet()) {
            int freq = entry.getKey();
            int productNum = entry.getValue();
            // 累计商品数量
            count += productNum;
            // 当累计数量>=topK时，当前频率即为结果
            if (count >= topK) {
                result = freq;
                break;
            }
            // 若累计数量<topK，记录最后一个频率（处理商品数不足topK的情况）
            result = freq;
        }

        return result;
    }

    /**
     * 更新商品的访问频率（核心辅助方法）
     *
     * @param productId 商品ID
     */
    private void updateProductFrequency(int productId) {
        // 1. 获取旧频率
        int oldFreq = productFreqMap.getOrDefault(productId, 0);
        // 2. 旧频率的商品数减1，若为0则从TreeMap中移除
        if (oldFreq > 0) {
            int oldCount = freqCountMap.get(oldFreq);
            if (oldCount == 1) {
                freqCountMap.remove(oldFreq);
            } else {
                freqCountMap.put(oldFreq, oldCount - 1);
            }
        }
        // 3. 更新新频率
        int newFreq = oldFreq + 1;
        productFreqMap.put(productId, newFreq);
        // 4. 新频率的商品数加1
        freqCountMap.put(newFreq, freqCountMap.getOrDefault(newFreq, 0) + 1);
    }

    /**
     * 清理所有商品的过期时间戳（核心辅助方法）
     *
     * @param window 时间窗口（秒）
     */
    private void cleanExpiredData(long window) {
        // 计算过期时间戳：当前时间戳 - window
        long expireTimestamp = currentTimestamp - window;
        // 遍历所有商品的时间戳队列，清理过期数据
        Iterator<Map.Entry<Integer, Deque<Long>>> iterator = productTimeMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Deque<Long>> entry = iterator.next();
            int productId = entry.getKey();
            Deque<Long> timeQueue = entry.getValue();

            // 清理该商品的过期时间戳
            while (!timeQueue.isEmpty() && timeQueue.peekFirst() <= expireTimestamp) {
                timeQueue.pollFirst();
                // 过期一个时间戳，商品频率减1
                decreaseProductFrequency(productId);
            }

            // 若该商品无有效时间戳，移除相关映射（节省空间）
            if (timeQueue.isEmpty()) {
                iterator.remove();
                productFreqMap.remove(productId);
            }
        }
    }

    /**
     * 减少商品的访问频率（辅助清理过期数据）
     *
     * @param productId 商品ID
     */
    private void decreaseProductFrequency(int productId) {
        // 1. 获取当前频率
        int oldFreq = productFreqMap.get(productId);
        // 2. 更新频率-商品数映射
        int oldCount = freqCountMap.get(oldFreq);
        if (oldCount == 1) {
            freqCountMap.remove(oldFreq);
        } else {
            freqCountMap.put(oldFreq, oldCount - 1);
        }
        // 3. 更新商品频率
        int newFreq = oldFreq - 1;
        if (newFreq == 0) {
            productFreqMap.remove(productId);
        } else {
            productFreqMap.put(productId, newFreq);
            freqCountMap.put(newFreq, freqCountMap.getOrDefault(newFreq, 0) + 1);
        }
    }

    // 测试用例
    public static void main(String[] args) {
        时间戳内统计topK counter = new 时间戳内统计topK();

        // 时间戳1：访问商品1
        counter.record(1);
        // 时间戳2：访问商品1
        counter.record(1);
        // 时间戳3：访问商品2
        counter.record(2);
        // 时间戳4：访问商品3
        counter.record(3);
        // 时间戳5：访问商品2
        counter.record(2);

        // 统计最近3秒（时间戳3、4、5）的top2频率
        System.out.println(counter.getTopFrequency(3, 2)); // 输出：1

        // 时间戳6：访问商品2
        counter.record(2);
        // 统计最近5秒（时间戳2-6）的top1频率
        System.out.println(counter.getTopFrequency(5, 1)); // 输出：3
    }
}
