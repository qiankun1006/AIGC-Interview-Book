package 算法题;

import java.util.ArrayList;
import java.util.List;

public class 汇总区间228题 {
    public List<String> summaryRanges(int[] nums) {
        int start = 0;
        List<String> res = new ArrayList<>();
        while(start < nums.length) {
            int end = start;
            while(end < nums.length - 1 && nums[end] + 1 == nums[end + 1]) {
                end++;
            }
            if(start == end) {
                res.add(nums[start] + "");
            }else{
                res.add(nums[start] + "->" + nums[end]);
            }
            start = end + 1;
        }

        return res;
    }
}
