package 算法new.第51到100;

public class di97 {

    // ========== 解法一：二维 DP ==========
    // dp[i][j] 表示 s1 前 i 个字符和 s2 前 j 个字符能否交错组成 s3 前 i+j 个字符
    // 转移：dp[i][j] = (dp[i-1][j] && s1[i-1]==s3[i+j-1])
    //              || (dp[i][j-1] && s2[j-1]==s3[i+j-1])
    //todo 二维动态规划，只要是对称的都是【中等难度】
    public boolean isInterleave(String s1, String s2, String s3) {
        int m = s1.length(), n = s2.length();
        if (m + n != s3.length()) return false;

        boolean[][] dp = new boolean[m + 1][n + 1];
        dp[0][0] = true;

        // 只用 s1 的前 i 个字符匹配 s3 前 i 个字符
        for (int i = 1; i <= m; i++) {
            dp[i][0] = dp[i - 1][0] && s1.charAt(i - 1) == s3.charAt(i - 1);
        }
        // 只用 s2 的前 j 个字符匹配 s3 前 j 个字符
        for (int j = 1; j <= n; j++) {
            dp[0][j] = dp[0][j - 1] && s2.charAt(j - 1) == s3.charAt(j - 1);
        }

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                char c = s3.charAt(i + j - 1);
                dp[i][j] = (dp[i - 1][j] && s1.charAt(i - 1) == c)
                        || (dp[i][j - 1] && s2.charAt(j - 1) == c);
            }
        }
        return dp[m][n];
    }

}
