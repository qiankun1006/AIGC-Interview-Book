package 算法new.第101到150;

public class di123 {

    // 最多完成两笔交易，求最大利润
    // 状态机 DP：整个过程可以分为 5 个阶段（状态）
    //   状态0：还没有任何操作
    //   状态1：持有第一支股票（第一次买入后）
    //   状态2：卖出第一支股票（第一次卖出后，手上没有股票）
    //   状态3：持有第二支股票（第二次买入后）
    //   状态4：卖出第二支股票（第二次卖出后，手上没有股票）
    //
    // 每个状态记录"到达该状态时手头的最大现金"
    // 买入时现金减少（花钱），卖出时现金增加（收钱）
    public int maxProfit(int[] prices) {
        // 初始现金为 0，买入后现金变为负数，用 MIN_VALUE 表示还未发生
        int hold1  = Integer.MIN_VALUE; // 状态1：第一次持股，手头现金 = 0 - 买入价
        int cash1  = 0;                 // 状态2：第一次卖出，手头现金 = hold1 + 卖出价
        int hold2  = Integer.MIN_VALUE; // 状态3：第二次持股，手头现金 = cash1 - 买入价
        int cash2  = 0;                 // 状态4：第二次卖出，手头现金 = hold2 + 卖出价

        for (int price : prices) {
            // 状态转移（每一天都尝试在当天做操作，取历史最优）
            hold1 = Math.max(hold1, -price);             // 今天第一次买 vs 之前已买
            cash1 = Math.max(cash1, hold1 + price);      // 今天第一次卖 vs 之前已卖
            hold2 = Math.max(hold2, cash1 - price);      // 今天第二次买 vs 之前已买
            cash2 = Math.max(cash2, hold2 + price);      // 今天第二次卖 vs 之前已卖
        }

        // 最终答案是第二次卖出后的最大现金（最多两笔，也可能只做一笔甚至不做）
        // 因为 cash1 <= cash2，所以直接返回 cash2 即可
        return cash2;
    }
}
