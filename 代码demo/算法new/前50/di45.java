package 算法new.前50;

public class di45 {

    public int jump(int[] nums) {
        int jumps = 0;
        int currentEnd = 0;
        int farthest = 0;

        System.out.println("=== 开始跳跃，数组长度=" + nums.length + " ===");
        for (int i = 0; i < nums.length - 1; i++) {
            farthest = Math.max(farthest, i + nums[i]);
            System.out.println("  i=" + i + " nums[i]=" + nums[i]
                    + " 最远能到=" + farthest
                    + " currentEnd=" + currentEnd);

            if (i == currentEnd) {
                jumps++;
                currentEnd = farthest;
                System.out.println("  *** 到达当前覆盖边界，执行第 " + jumps + " 次跳跃，新边界=" + currentEnd + " ***");
            }
        }
        System.out.println("=== 总跳跃次数=" + jumps + " ===");
        return jumps;
    }

    public static void main(String[] args) {
        di45 sol = new di45();

        // 用例1：[2,3,1,1,4]  期望输出 2
        // 下标0跳到1，下标1跳到4（终点）
        int[] nums1 = {2, 3, 1, 1, 4};
        System.out.println("输入: [2,3,1,1,4]");
        System.out.println("结果: " + sol.jump(nums1));
        System.out.println();

        // 用例2：[2,3,0,1,4]  期望输出 2
        int[] nums2 = {2, 3, 0, 1, 4};
        System.out.println("输入: [2,3,0,1,4]");
        System.out.println("结果: " + sol.jump(nums2));
        System.out.println();

        // 用例3：[1,1,1,1]  期望输出 3（每次只能跳1步）
        int[] nums3 = {1, 1, 1, 1};
        System.out.println("输入: [1,1,1,1]");
        System.out.println("结果: " + sol.jump(nums3));
    }
}
