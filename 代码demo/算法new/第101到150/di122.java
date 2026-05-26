package 算法new.第101到150;



public class di122 {

    //ToDo 贪心算法，有涨就买，难度是中等，实现上是简单，能确定方案最关键
    public int maxProfit(int[] prices) {
        int profit = 0;
        for (int i = 1; i < prices.length; i++) {
            int tmp = prices[i] - prices[i - 1];
            if (tmp > 0) profit += tmp;
        }
        return profit;
    }

}
