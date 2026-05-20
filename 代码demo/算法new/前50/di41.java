package 算法new.前50;

public class di41 {

    //输入：nums = [1,2,0]
    //输出：3
    //解释：范围 [1,2] 中的数字都在数组中。

    //输入：nums = [3,4,-1,1]
    //输出：2
    //解释：1 在数组中，但 2 没有。

    //输入：nums = [7,8,9,11,12]
    //输出：1
    //解释：最小的正数 1 没有出现。
    public int firstMissingPositive(int[] nums) {
        int n = nums.length;

        // 1. 第一遍遍历：交换法，将每个数字放到它应该在的位置
        //todo 用交换法做一遍
        for (int i = 0; i < n; i++) {
            // 只要当前数字在 [1, n] 范围内，且它不在正确的位置上，
            // 就一直交换，直到当前位置放对了数，或者遇到重复数（防止死循环）
            while (nums[i] >= 1 && nums[i] <= n && nums[nums[i] - 1] != nums[i]) {
                // 交换 nums[i] 和 nums[nums[i] - 1]
                int temp = nums[nums[i] - 1];
                nums[nums[i] - 1] = nums[i];
                nums[i] = temp;
            }
        }

        // 2. 第二遍遍历：找第一个不在正确位置上的数
        for (int i = 0; i < n; i++) {
            // 如果 nums[i] 不等于 i + 1，说明 i + 1 缺失了
            if (nums[i] != i + 1) {
                return i + 1;
            }
        }

        // 如果 1 到 n 都在，那缺失的就是 n + 1
        return n + 1;
    }
}
