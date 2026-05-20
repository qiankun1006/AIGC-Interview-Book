package 算法new.第51到100;

public class di72 {

    // dp[i][j]：word1 前 i 个字符 → word2 前 j 个字符的最少操作数
    //todo 这题难度没到困难主要是对称的，不用考虑很多非对称逻辑。
    public int minDistance(String word1, String word2) {
        int len1 = word1.length();
        int len2 = word2.length();
        //todo 考虑空字符串这种情况，所以需要加1
        int[][] dp = new int[len1 + 1][len2 + 1];

        // 边界：空串 → 非空串，只能逐个插入
        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i; // word1 前 i 个 → 空串：删除 i 次
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j; // 空串 → word2 前 j 个：插入 j 次
        }

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                if (word1.charAt(i - 1) == word2.charAt(j - 1)) {
                    // 末尾字符相同，无需操作，直接继承
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    // 三种操作取最小：
                    // dp[i-1][j] + 1   → 删除 word1[i-1]
                    // dp[i][j-1] + 1   → 插入 word2[j-1]
                    // dp[i-1][j-1] + 1 → 替换 word1[i-1] 为 word2[j-1]
                    dp[i][j] = Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]) + 1;
                }
            }
        }
        return dp[len1][len2];
    }
}
