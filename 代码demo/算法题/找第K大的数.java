package 算法题;

import java.util.Random;

/**
 * 优化版：快速选择找第 K 大的数（工业级）
 * 优化点：随机基准 + 三数取中 + 小数组插入排序 + 尾递归优化
 */
public class 找第K大的数 {
    private static final Random RANDOM = new Random();
    // 小数组阈值：小于该值时用插入排序
    private static final int INSERTION_SORT_THRESHOLD = 10;

    // 对外入口
    public static int findKthLargest(int[] nums, int k) {
        // 增强边界校验
        if (nums == null) {
            throw new NullPointerException("数组不能为空");
        }
        int len = nums.length;
        if (k <= 0 || k > len) {
            throw new IllegalArgumentException("K 必须在 1~" + len + " 范围内");
        }
        int[] arr = nums.clone();
        // 尾递归优化：用循环替代递归，减少栈深度
        return quickSelectOptimized(arr, 0, len - 1, k);
    }

    // 优化版快速选择（尾递归优化）
    private static int quickSelectOptimized(int[] arr, int left, int right, int k) {
        while (true) {
            // 优化1：小数组用插入排序
            if (right - left + 1 <= INSERTION_SORT_THRESHOLD) {
                insertionSort(arr, left, right);
                return arr[left + k - 1];
            }

            // 优化2：三数取中 + 随机基准，避免有序数组退化
            int pivotIdx = medianOfThree(arr, left, right);
            swap(arr, left, pivotIdx); // 基准值移到左边界

            // 分区
            int partitionIdx = partition(arr, left, right);

            // 递归转循环，减少栈开销
            if (partitionIdx == k - 1) {
                return arr[partitionIdx];
            } else if (partitionIdx < k - 1) {
                // 目标在右区间，更新左边界
                left = partitionIdx + 1;
                // 调整 K：右区间的第 (k - partitionIdx - 1) 大
                k = k - partitionIdx - 1;
            } else {
                // 目标在左区间，更新右边界
                right = partitionIdx - 1;
            }
        }
    }

    // 优化2：三数取中（左、中、右的中位数）
    private static int medianOfThree(int[] arr, int left, int right) {
        int mid = left + (right - left) / 2;
        // 调整顺序：让 arr[left] <= arr[mid] <= arr[right]
        if (arr[left] < arr[mid]) {
            swap(arr, left, mid);
        }
        if (arr[left] < arr[right]) {
            swap(arr, left, right);
        }
        if (arr[mid] < arr[right]) {
            swap(arr, mid, right);
        }
        // 随机偏移：避免固定三数取中的极端情况
        int randomOffset = RANDOM.nextInt(right - left + 1);
        swap(arr, mid, left + randomOffset);
        return left + randomOffset;
    }

    // 优化3：插入排序（小数组专用，降序）
    private static void insertionSort(int[] arr, int left, int right) {
        for (int i = left + 1; i <= right; i++) {
            int temp = arr[i];
            int j = i - 1;
            // 降序插入：把比 temp 小的元素后移
            while (j >= left && arr[j] < temp) {
                arr[j + 1] = arr[j];
                j--;
            }
            arr[j + 1] = temp;
        }
    }

    // 分区方法（同基础版，降序）
    private static int partition(int[] arr, int left, int right) {
        int pivot = arr[left];
        int i = left, j = right;
        while (i < j) {
            while (i < j && arr[j] <= pivot) {
                j--;
            }
            while (i < j && arr[i] >= pivot) {
                i++;
            }
            if (i < j) {
                swap(arr, i, j);
            }
        }
        swap(arr, left, i);
        return i;
    }

    private static void swap(int[] arr, int i, int j) {
        int temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;
    }

    // 测试
    public static void main(String[] args) {
        // 测试有序数组（优化版抗退化）
        int[] sortedNums = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        int k1 = 4;
        System.out.println("有序数组第 " + k1 + " 大的数：" + findKthLargest(sortedNums, k1)); // 6

        // 测试乱序数组
        int[] randomNums = {3, 1, 5, 2, 4, 6, 7, 9, 8};
        int k2 = 1;
        System.out.println("乱序数组第 " + k2 + " 大的数：" + findKthLargest(randomNums, k2)); // 9

        // 测试小数组
        int[] smallNums = {5, 3, 1};
        int k3 = 2;
        System.out.println("小数组第 " + k3 + " 大的数：" + findKthLargest(smallNums, k3)); // 3
    }
}