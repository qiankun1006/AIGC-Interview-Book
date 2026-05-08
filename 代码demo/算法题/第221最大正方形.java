package 算法题;

public class 第221最大正方形 {

    public int maximalSquare(char[][] matrix) {
        int x_len = matrix.length;
        if (x_len == 0) return 0;
        int y_len = matrix[0].length;
        int[][] dp = new int[x_len][y_len];
        int count = 0;
        for (int i = 0; i < x_len; i++) {
            if (matrix[i][0] == '1') {
                count = 1;
                dp[i][0] = 1;
            }
        }
        for (int i = 0; i < y_len; i++) {
            if (matrix[0][i] == '1') {
                count = 1;
                dp[0][i] = 1;
            }
        }
        for (int i = 1; i < x_len; i++) {
            for (int j = 1; j < y_len; j++) {
                if (matrix[i][j] == '1') {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j], Math.min(dp[i - 1][j - 1], dp[i][j - 1]));
                    count = Math.max(count, dp[i][j]);
                }
            }
        }
        return count * count;
    }
}
