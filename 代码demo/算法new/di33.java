package 算法new;

public class di33 {

    //输入：nums = [4,5,6,7,0,1,2], target = 0 输出：4
    // 核心：大小对比 不再是nums[mid]和target（当然，如果是等于就直接结束）
    // 确定哪边有序之后去明确有序的一边比较target，如果没有就是另一边。
    // 总结：先确定有序边，然后在有序边搜索。
    public int search(int[] nums, int target) {
        if (nums.length == 0) return -1;
        int left = 0;
        int right = nums.length - 1;
        while (left < right) {
            int mid = left + (right - left) / 2;
            if (nums[mid] == target) return mid;
            else if (nums[mid] < nums[right]) {  //右边有序
                if (target > nums[mid] && target <= nums[right]) left = mid + 1;
                else right = mid;
            } else {
                //左边有序
                if (target >= nums[left] && target < nums[mid]) right = mid;
                else left = mid + 1;
            }
        }
        if (nums[left] == target) return left;
        return -1;
    }
}
