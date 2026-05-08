package 算法题;

public class 寻找重复数 {

    //[1,3,4,2,2]
    // 0 -> 1 -> 3 -> 2 -> 4 -> 2
    public int findDuplicate(int[] nums) {
        if(nums == null) {
            return -1;
        }
        int low = 0;
        int fast = 0;
        while(true) {
            low = nums[low];
            fast = nums[nums[fast]];
            if(low == fast) {
                low = 0;
                while(nums[low] != nums[fast]){
                    low = nums[low];
                    fast = nums[fast];
                }
                return nums[low];
            }
        }
    }
}
