package 算法题.第K大的数;

import java.util.Random;

public class QuickSelectWithDuplicates {
    private static final Random random = new Random();

    public static int findKthLargest(int[] nums, int k) {
        // 第K大对应升序数组的 nums.length - k 索引
        return quickSelect(nums, 0, nums.length - 1, nums.length - k);
    }

    private static int quickSelect(int[] nums, int left, int right, int targetIdx) {
        if (left == right) return nums[left]; // 递归终止

        // 随机选择基准，避免有序数组的最坏情况
        int pivotIdx = left + random.nextInt(right - left + 1);
        int pivotVal = nums[pivotIdx];

        // 三路分区：[left, lt) < pivot, [lt, gt] = pivot, (gt, right] > pivot
        int lt = left;   // 小于区的右边界（不包含）
        int gt = right;  // 大于区的左边界（不包含）
        int i = left;    // 当前遍历指针

        // 分区过程
        while (i <= gt) {
            if (nums[i] < pivotVal) {
                // 当前元素 < 基准：交换到小于区，lt和i都右移
                swap(nums, lt++, i++);
            } else if (nums[i] > pivotVal) {
                // 当前元素 > 基准：交换到大于区，gt左移（i不移动，因为新交换来的元素还没判断）
                swap(nums, i, gt--);
            } else {
                // 当前元素 == 基准：直接跳过，i右移
                i++;
            }
        }

        // 判断目标位置落在哪个区域
        if (targetIdx < lt) {
            // 目标在小于区：递归左半部分
            return quickSelect(nums, left, lt - 1, targetIdx);
        } else if (targetIdx > gt) {
            // 目标在大于区：递归右半部分
            return quickSelect(nums, gt + 1, right, targetIdx);
        } else {
            // 目标在等于区：直接返回（所有等于区的元素都是基准值）
            return pivotVal;
        }
    }

    private static void swap(int[] nums, int i, int j) {
        int temp = nums[i];
        nums[i] = nums[j];
        nums[j] = temp;
    }

    // 测试：大量重复元素场景
    public static void main(String[] args) {
        // 包含大量重复元素的数组
        int[] nums = {5, 2, 5, 3, 5, 1, 5, 4, 5, 6, 5, 5};
        int k = 4; // 找第4大的数（预期结果：5）
        System.out.println("第" + k + "大的数：" + findKthLargest(nums.clone(), k));
    }
}