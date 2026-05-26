package 算法new.第101到150;

import java.util.List;

public class di120 {

    //示例 1：
    //输入：triangle = [[2],[3,4],[6,5,7],[4,1,8,3]]
    //输出：11
    //解释：如下面简图所示：
    //   2
    //  3 4
    // 6 5 7
    //4 1 8 3
    //自顶向下的最小路径和为 11（即，2 + 3 + 5 + 1 = 11）。
    //示例 2：
    //
    //输入：triangle = [[-10]]
    //输出：-10
    //todo 关键点：1、从下往上。2、复用一维数组
    public int minimumTotal(List<List<Integer>> triangle) {
        if (triangle.size() == 0) return 0;

        // dp[j]：从第 i 层第 j 个位置出发，走到最底层的最小路径和
        // 初始长度多开 1 位，方便最后一层边界计算（dp[lastRow+1] = 0）
        int[] dp = new int[triangle.size() + 1];

        // 自底向上：从最后一行往上推，避免自顶向下时需要记录完整路径
        for (int i = triangle.size() - 1; i >= 0; i--) {
            List<Integer> row = triangle.get(i);
            for (int j = 0; j < row.size(); j++) {
                // 当前位置 (i,j) 可以走到下一层的 (i+1,j) 或 (i+1,j+1)
                // 取两者中较小的路径和，再加上当前节点的值
                dp[j] = Math.min(dp[j], dp[j + 1]) + row.get(j);
            }
        }

        // dp[0] 即从顶点出发到底层的最小路径和
        return dp[0];
    }
}
