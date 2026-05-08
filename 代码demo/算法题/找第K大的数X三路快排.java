package 算法题;

import java.util.*;


public class 找第K大的数X三路快排 {

    // 随机数生成器
    private static final Random random = new Random();

    /**
     * 对外暴露的方法：找数组中第K大的数
     *
     * @param nums 输入数组
     * @param k    第K大（k从1开始）
     * @return 第K大的数
     */
    public static int findKthLargest(int[] nums, int k) {
        if (nums == null || nums.length == 0 || k < 1 || k > nums.length) {
            throw new IllegalArgumentException("输入参数不合法");
        }
        // 调用三路快排的核心方法，范围是整个数组
        return quickSelect(nums, 0, nums.length - 1, k);
    }

    /**
     * 三路快排的核心选择方法
     *
     * @param nums  数组
     * @param left  左边界
     * @param right 右边界
     * @param k     第K大
     * @return 第K大的数
     */
    private static int quickSelect(int[] nums, int left, int right, int k) {
        // 递归终止条件：只有一个元素时，直接返回
        if (left == right) {
            return nums[left];
        }

        // 随机选择基准值的索引，避免有序数组的最坏情况
        int pivotIndex = left + random.nextInt(right - left + 1);
        int pivot = nums[pivotIndex];

        // 三路划分：[left, lt) 大于pivot，[lt, i) 等于pivot，(gt, right] 小于pivot
        int lt = left;    // 大于区域的右边界（不包含）
        int i = left;     // 当前遍历的位置
        int gt = right;   // 小于区域的左边界（不包含）

        while (i <= gt) {
            if (nums[i] > pivot) {
                // 当前元素大于基准值，交换到大于区域，lt和i都右移
                swap(nums, i++, lt++);
            } else if (nums[i] < pivot) {
                // 当前元素小于基准值，交换到小于区域，gt左移（i不移动，因为交换过来的元素还没判断）
                swap(nums, i, gt--);
            } else {
                // 当前元素等于基准值，直接i右移
                i++;
            }
        }

        // 计算各区域的长度
        int greaterLen = lt - left;          // 大于区域的长度
        int equalLen = gt - lt + 1;          // 等于区域的长度

        // 判断第K大的数在哪个区域
        if (k <= greaterLen) {
            // 大于区域的长度≥k，递归处理大于区域
            return quickSelect(nums, left, lt - 1, k);
        } else if (k <= greaterLen + equalLen) {
            // 落在等于区域，直接返回基准值
            return pivot;
        } else {
            // 落在小于区域，递归处理小于区域，k需要减去前两个区域的长度
            return quickSelect(nums, gt + 1, right, k - greaterLen - equalLen);
        }
    }

    /**
     * 交换数组中两个位置的元素
     *
     * @param nums 数组
     * @param i    索引1
     * @param j    索引2
     */
    private static void swap(int[] nums, int i, int j) {
        int temp = nums[i];
        nums[i] = nums[j];
        nums[j] = temp;
    }

    // 测试用例
    public static void main(String[] args) {
        int[] nums1 = {3, 2, 1, 5, 6, 4};
        int k1 = 2;
        System.out.println("第" + k1 + "大的数：" + findKthLargest(nums1, k1)); // 预期输出5

        int[] nums2 = {3, 2, 3, 1, 2, 4, 5, 5, 6};
        int k2 = 4;
        System.out.println("第" + k2 + "大的数：" + findKthLargest(nums2, k2)); // 预期输出4

        int[] nums3 = {1};
        int k3 = 1;
        System.out.println("第" + k3 + "大的数：" + findKthLargest(nums3, k3)); // 预期输出1
    }
}
