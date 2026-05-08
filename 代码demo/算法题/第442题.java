package 算法题;

import java.util.ArrayList;
import java.util.List;

public class 第442题 {
    public List<Integer> findDuplicates(int[] nums) {
        List<Integer> res = new ArrayList<>();
        int n = nums.length;
        for (int i = 0; i < n; i++) {
            // 取当前元素的绝对值（避免已标记的负数影响）
            int x = Math.abs(nums[i]);
            int idx = x - 1;
            if (nums[idx] > 0) {
                // 第一次出现，标记为负数
                nums[idx] = -nums[idx];
            } else {
                // 第二次出现，加入结果集
                res.add(x);
            }
        }
        return res;
    }
}
