package 算法题;

import java.util.ArrayList;
import java.util.List;

public class 多数元素229题 {
    public List<Integer> majorityElement(int[] nums) {
        List<Integer> ret = new ArrayList<>();
        if(nums.length < 1) return ret;  //一个都没有
        int count1 = 0, count2 = 0;
        int major1 = nums[0], major2 = nums[0];
        for(int i=0;i<nums.length;i++) {  //找出两个有可能的结果
            if(nums[i] == major1)
                count1++;
            else if(nums[i] == major2)
                count2++;
            else if(count1 == 0) {  //因为抵消导致0或者一开始就是0（major2有这个可能），总是将最新出现的优先分配给major1
                count1 = 1;
                major1 = nums[i];
            }
            else if(count2 == 0) {  //因为抵消导致0或者一开始就是0（major2有这个可能）
                count2 = 1;
                major2 = nums[i];
            }
            else {    //出现了major1，major2都不等于的数，导致抵消删除的操作，两个major都删去一个，以抵消
                count1--;
                count2--;
            }
        }
        count1 = 0;
        count2 = 0;
        for(int i=0;i<nums.length;i++) {
            if(nums[i] == major1)
                count1++;
            else if(nums[i] == major2)
                count2++;
        }
        if(count1 > nums.length/3)
            ret.add(major1);
        if(major1 != major2 && count2 > nums.length/3)  //数组全为相同的数，major1==major2
            ret.add(major2);
        return ret;
    }
}
