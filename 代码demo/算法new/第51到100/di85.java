package 算法new.第51到100;

import java.util.*;

public class di85 {

    public int maximalRectangle(char[][] matrix) {
        if (matrix.length == 0) return 0;
        int m = matrix.length, n = matrix[0].length;

        // 三个关键 DP 数组
        int[] height = new int[n]; // 高度
        int[] left = new int[n];   // 左边界
        int[] right = new int[n];  // 右边界

        // 初始化右边界为最右边
        Arrays.fill(right, n);

        int maxArea = 0;

        for (int i = 0; i < m; i++) {
            int curLeft = 0, curRight = n;

            // 1. 更新 height 和 left
            for (int j = 0; j < n; j++) {
                if (matrix[i][j] == '1') {
                    height[j]++;
                    // left[j] 取最大值：要么是上一行的左边界，要么是这一行遇到的第一个1的位置
                    left[j] = Math.max(left[j], curLeft);
                } else {
                    height[j] = 0;
                    left[j] = 0; // 重置
                    curLeft = j + 1; // 更新当前行下一个可能的左边界
                }
            }

            // 2. 更新 right
            for (int j = n - 1; j >= 0; j--) {
                if (matrix[i][j] == '1') {
                    // right[j] 取最小值
                    right[j] = Math.min(right[j], curRight);
                } else {
                    right[j] = n; // 重置
                    curRight = j; // 更新当前行下一个可能的右边界
                }
            }

            // 3. 计算面积
            for (int j = 0; j < n; j++) {
                if (height[j] > 0) {
                    maxArea = Math.max(maxArea, (right[j] - left[j]) * height[j]);
                }
            }
        }
        return maxArea;
    }
}
