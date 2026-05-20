package 算法new.前50;

import java.util.Arrays;
//todo 没有一次过，出错了
public class di1 {

    public static void main(String[] args) {

    }

    public int[] twoSum(int[] nums, int target) {
        Arrays.sort(nums);
        int left = 0;
        int right = nums.length;
        while(left < right) {
            int sum = nums[left] + nums[right];
            if(sum == target) {
                return new int[]{left, right};
            } else if(sum > target) {
                right --;
            } else {
                left++;
            }
        }
        return null;
    }
}
