package 算法new.第101到150;

public class di115 {

    //给你两个字符串 s 和 t ，统计并返回在 s 的 子序列 中 t 出现的个数。
    //测试用例保证结果在 32 位有符号整数范围内。
    //
    //示例 1：
    //输入：s = "rabbbit", t = "rabbit"
    //输出：3
    //解释：
    //如下所示, 有 3 种可以从 s 中得到 "rabbit" 的方案。
    //示例 2：
    //输入：s = "babgbag", t = "bag"
    //输出：5
    //解释：
    //如下所示, 有 5 种可以从 s 中得到 "bag" 的方案。
    public int numDistinct(String s, String t) {
        // dp[i][j]：s 的前 i 个字符中，t 的前 j 个字符作为子序列出现的次数
        int[][] dp = new int[s.length() + 1][t.length() + 1];

        // 初始化：t 为空串时，s 的任意前缀都有且只有 1 种方案（什么都不选）
        for (int i = 0; i <= s.length(); i++) {
            dp[i][0] = 1;
        }

        for (int i = 1; i <= s.length(); i++) {
            for (int j = 1; j <= t.length(); j++) {
                if (s.charAt(i - 1) == t.charAt(j - 1)) {
                    // 当前字符相等，有两种决策：
                    // 1. 不用 s[i-1] 来匹配：方案数 = dp[i-1][j]（跳过 s[i-1]）
                    // 2. 用 s[i-1] 来匹配 t[j-1]：方案数 = dp[i-1][j-1]（两者同时消耗）
                    dp[i][j] = dp[i - 1][j] + dp[i - 1][j - 1];
                } else {
                    // 当前字符不等，s[i-1] 一定用不上，只能跳过它
                    dp[i][j] = dp[i - 1][j];
                }
            }
        }
        return dp[s.length()][t.length()];
    }
}
