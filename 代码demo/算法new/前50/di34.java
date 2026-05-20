package 算法new.前50;

public class di34 {

    // 两次二分查找，分开查找第一个和最后一个
    // 时间复杂度 O(log n), 空间复杂度 O(1)
    // [1,2,3,3,3,3,4,5,9]
    public int[] searchRange(int[] nums, int target) {
        int left = 0;
        int right = nums.length;
        int first = -1;
        int last = -1;
        // 找第一个等于target的位置
        while (left < right) {
            int middle = (left + right) / 2;
            if (nums[middle] == target) {
                //todo 命中了也不能停，继续往右边找，每次有新的等于都要记录
                first = middle;
                right = middle; //重点
            } else if (nums[middle] > target) {
                right = middle;
            } else {
                left = middle + 1;
            }
        }

        // 最后一个等于target的位置
        left = 0;
        right = nums.length;
        while (left < right) {
            int middle = (left + right) / 2;
            if (nums[middle] == target) {
                last = middle;
                left = middle + 1; //重点
            } else if (nums[middle] > target) {
                right = middle;
            } else {
                left = middle + 1;
            }
        }

        return new int[]{first, last};
    }

}
