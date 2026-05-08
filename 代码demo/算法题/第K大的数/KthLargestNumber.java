package 算法题.第K大的数;

import java.util.Random;

public class KthLargestNumber {
    private static final Random random = new Random();

    // 方法2：快速选择
    public static int findKthLargestByQuickSelect(int[] nums, int k) {
        // 第K大的数，对应「升序数组中索引为 nums.length - k」的数
        return quickSelect(nums, 0, nums.length - 1, nums.length - k);
    }

    private static int quickSelect(int[] nums, int left, int right, int targetIdx) {
        // 随机选基准，避免最坏情况
        int pivotIdx = left + random.nextInt(right - left + 1);
        // 分区：将基准放到正确位置，返回基准最终索引
        int partitionIdx = partition(nums, left, right, pivotIdx);

        if (partitionIdx == targetIdx) {
            return nums[partitionIdx]; // 找到目标
        } else if (partitionIdx < targetIdx) {
            return quickSelect(nums, partitionIdx + 1, right, targetIdx); // 递归右半部分
        } else {
            return quickSelect(nums, left, partitionIdx - 1, targetIdx); // 递归左半部分
        }
    }

    // 分区函数：将小于基准的放左边，大于的放右边，返回基准最终位置
    private static int partition(int[] nums, int left, int right, int pivotIdx) {
        int pivotVal = nums[pivotIdx];
        // 先把基准交换到最右侧
        swap(nums, pivotIdx, right);
        int storeIdx = left; // 记录小于基准的元素的存储位置

        for (int i = left; i < right; i++) {
            if (nums[i] < pivotVal) {
                swap(nums, storeIdx, i);
                storeIdx++;
            }
        }
        // 把基准放到正确位置
        swap(nums, storeIdx, right);
        return storeIdx;
    }

    private static void swap(int[] nums, int i, int j) {
        int temp = nums[i];
        nums[i] = nums[j];
        nums[j] = temp;
    }

    public static void main(String[] args) {
        int[] nums = {3, 2, 1, 5, 5, 5, 5, 4, 4, 4, 4, 6, 4};
        int k = 1;
        System.out.println("快速选择-第" + k + "大的数：" + findKthLargestByQuickSelect(nums.clone(), k));
    }
}