package ai题;

import java.util.*;


// https://www.doubao.com/thread/w1748c232940da125
// 就是桶，可以练习一下桶排序
public class 订单分区 {

    /**
     * 直白版O(n)解法：遍历每个订单，直接计算所属区间并计数
     *
     * @param orders 订单金额数组
     * @param k      区间数量
     * @return 各区间的订单数
     */
    public static int[] countOrdersByInterval(int[] orders, int k) {
        // 边界条件1：k为0时返回空数组
        if (k == 0) {
            return new int[0];
        }
        // 边界条件2：订单为空时返回长度为k的全0数组
        if (orders == null || orders.length == 0) {
            return new int[k];
        }

        // 步骤1：计算订单金额的最大值，确定总范围
        int maxVal = Arrays.stream(orders).max().getAsInt();
        long totalLen = (long) maxVal + 1; // 总范围[0, maxVal+1)，用long避免溢出

        // 步骤2：计算区间的基础长度和需要加长的区间数
        long baseLen = totalLen / k;
        long remain = totalLen % k; // 前remain个区间长度为baseLen+1，其余为baseLen

        // 步骤3：初始化结果数组（k个桶）
        int[] result = new int[k];

        // 步骤4：遍历每个订单，计算所属区间并计数（核心O(n)逻辑）
        for (int amount : orders) {
            // 计算当前金额属于第几个区间
            int bucketIndex = findBucketIndex(amount, baseLen, remain, k);
            result[bucketIndex]++;
        }

        return result;
    }

    /**
     * 核心工具方法：计算单个金额所属的区间索引（O(1)时间）
     */
    private static int findBucketIndex(long amount, long baseLen, long remain, int k) {
        // 先计算前remain个区间的总长度：remain*(baseLen+1)
        long firstPartTotal = remain * (baseLen + 1);

        if (amount < firstPartTotal) {
            // 属于前remain个区间：直接用amount/(baseLen+1)得到索引
            return (int) (amount / (baseLen + 1));
        } else {
            // 属于后面的区间：先减去前remain个区间的总长度，再除以baseLen，最后加上remain
            long restAmount = amount - firstPartTotal;
            return (int) (remain + restAmount / baseLen);
        }
    }

    // 测试用例
    public static void main(String[] args) {
        // 测试用例1：常规场景
        int[] orders1 = {1, 3, 5, 7, 9, 11, 13, 15};
        int k1 = 3;
        System.out.println(Arrays.toString(countOrdersByInterval(orders1, k1))); // 输出：[3, 2, 3]

        // 测试用例2：全0订单
        int[] orders2 = {0, 0, 0, 5, 5, 5};
        int k2 = 2;
        System.out.println(Arrays.toString(countOrdersByInterval(orders2, k2))); // 输出：[3, 3]

        // 测试用例3：max_val=14，k=3
        int[] orders3 = {2, 5, 7, 10, 14, 4, 9, 13};
        int k3 = 3;
        System.out.println(Arrays.toString(countOrdersByInterval(orders3, k3))); // 输出：[2, 3, 3]
    }
}