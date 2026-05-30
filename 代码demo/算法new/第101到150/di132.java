package 算法new.第101到150;

public class di132 {

    //给你一个字符串 s，请你将 s 分割成一些子串，使每个子串都是回文串。
    //返回符合要求的 最少分割次数 。
    //示例 1：输入：s = "aab"  输出：1  ["aa","b"]
    //示例 2：输入：s = "a"   输出：0
    //示例 3：输入：s = "ab"  输出：1
    //
    // 132 和 131 的区别：131 要枚举所有方案（必须回溯），132 只要最少次数。
    // 回溯是指数级复杂度，必然 TLE，应改用一维 DP。
    public int minCut(String s) {
        int n = s.length();

        // 第一步：预处理回文表（复用 131 的思路）
        // isPalin[i][j]：s[i..j] 是否是回文
        boolean[][] isPalin = new boolean[n][n];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = i; j < n; j++) {
                isPalin[i][j] = s.charAt(i) == s.charAt(j)
                        && (j - i <= 2 || isPalin[i + 1][j - 1]);
            }
        }

        // 第二步：一维 DP 求最少切割数
        // dp[i]：s[0..i] 的最少切割次数
        // 初始化为最坏情况：每个字符单独切，需要 i 刀
        int[] dp = new int[n];
        for (int i = 0; i < n; i++) dp[i] = i;

        for (int i = 1; i < n; i++) {
            // 如果 s[0..i] 整体就是回文，0 刀不需要切
            if (isPalin[0][i]) {
                dp[i] = 0;
                continue;
            }
            // 枚举最后一段 s[j..i] 是回文的所有切法
            // dp[i] = min(dp[j-1] + 1)，其中 s[j..i] 是回文
            for (int j = 1; j <= i; j++) {
                if (isPalin[j][i]) {
                    dp[i] = Math.min(dp[i], dp[j - 1] + 1);
                }
            }
        }

        return dp[n - 1];
    }
}
