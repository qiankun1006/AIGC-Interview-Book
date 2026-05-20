package 算法new.第51到100;

import java.util.Arrays;
import java.util.Stack;

public class di84 {

    // 单调递增栈：栈里存下标，保证栈底到栈顶对应的高度单调递增
    // 当遇到比栈顶矮的柱子时，说明以栈顶高度为"限制高度"的矩形右边界已确定，可以计算面积
    public int largestRectangleArea(int[] heights) {
        int length = heights.length;

        // 末尾补一个高度为 0 的哨兵，目的是让栈里所有元素在遍历结束时都能被弹出结算
        int[] heightsNew = new int[length + 1];
        for (int i = 0; i < length; i++) {
            heightsNew[i] = heights[i];
        }
        heightsNew[length] = 0;

        Stack<Integer> st = new Stack<>();  // 存下标，单调递增栈
        int maxRes = 0;

        for (int i = 0; i < heightsNew.length; i++) {
            System.out.printf("i=%d height=%d  栈=%s%n", i, heightsNew[i], st);

            // 当前柱子比栈顶矮，栈顶柱子的"最大延伸矩形"右边界就是 i，开始结算
            while (!st.isEmpty() && heightsNew[st.peek()] > heightsNew[i]) {
                int top = st.pop();  // 被弹出的柱子，它的高度就是矩形高度
                int height = heightsNew[top];

                // 宽度计算：
                // - 右边界是 i（当前更矮的柱子位置，不含）
                // - 左边界是新栈顶的下一个位置（栈顶是左边第一个比它矮的，不含）
                // - 如果栈空，说明左边没有更矮的柱子，矩形可以延伸到最左边（宽度=i）
                int width = st.isEmpty() ? i : (i - st.peek() - 1);
                int area = height * width;

                System.out.printf("  弹出下标=%d height=%d, 左边界下标=%s, 宽=%d, 面积=%d%n",
                        top, height, st.isEmpty() ? "无(到最左)" : String.valueOf(st.peek()), width, area);

                maxRes = Math.max(maxRes, area);
            }
            st.push(i);
        }
        return maxRes;
    }

    public static void main(String[] args) {
        di84 sol = new di84();

        // 经典用例，答案 10
        // 高度：2 1 5 6 2 3
        //        ↑     ↑↑       最大矩形是第3、4根柱子（高6宽1=6）或（高5宽2=10）
        check(sol, new int[]{2, 1, 5, 6, 2, 3}, 10, "经典用例");

        // 全相等，答案 = 高度 * 数量
        check(sol, new int[]{3, 3, 3}, 9, "全相等");

        // 单调递增，最大矩形在最后
        check(sol, new int[]{1, 2, 3, 4, 5}, 9, "单调递增");

        // 单调递减，最大矩形是第一根
        check(sol, new int[]{5, 4, 3, 2, 1}, 9, "单调递减");

        // 单个元素
        check(sol, new int[]{5}, 5, "单个元素");

        // 中间一根很高，两边很矮
        check(sol, new int[]{1, 100, 1}, 100, "中间高两边矮");
    }

    private static void check(di84 sol, int[] heights, int expected, String desc) {
        System.out.println("\n========== 【" + desc + "】 heights=" + Arrays.toString(heights) + " ==========");
        int result = sol.largestRectangleArea(heights);
        String status = result == expected ? "✓ PASS" : "✗ FAIL";
        System.out.println("结果=" + result + " 期望=" + expected + "  " + status);
    }
}
