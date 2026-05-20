package 算法new.前50;

import java.util.*;

public class di15 {
    public List<List<Integer>> threeSum(int[] nums) {
        Arrays.sort(nums);
        List<List<Integer>> res = new ArrayList<>();
        for (int i = 0; i < nums.length - 2; i++) {
            if (nums[i] > 0) {
                break;
            }
            //防止重复，第一个防止重
            if(i > 0 && nums[i] == nums[i-1]) {
                continue;
            }
            int start = i + 1;
            int end = nums.length - 1;
            while(start < end) {
                int sum = nums[i] + nums[start] + nums[end];
                if(sum == 0) {
                    List<Integer> cur = Arrays.asList(nums[i], nums[start], nums[end]);
                    res.add(cur);
                    //第二第三个防止重复
                    while(start < end -1 && nums[start] == nums[start + 1]) {
                        start ++;
                    }
                    while(start < end -1 && nums[end] == nums[end - 1]) {
                        end --;
                    }
                    start++;
                    end--;
                } else if(sum > 0) {
                    end --;
                } else {
                    start ++;
                }
            }
        }
        return res;
    }
}
