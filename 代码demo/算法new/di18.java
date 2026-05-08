package 算法new;

import java.util.*;

public class di18 {

    public List<List<Integer>> fourSum(int[] nums, int target) {
        Arrays.sort(nums);
        if (nums.length < 4) {
            return new ArrayList<>();
        }
        List<List<Integer>> res = new ArrayList<>();
        for (int i = 0; i < nums.length - 3; i++) {
            if (i > 0 && nums[i] == nums[i - 1])
                continue;
            for (int j = i + 1; j < nums.length - 2; j++) {
                if (j > i + 1 && nums[j] == nums[j - 1])
                    continue;
                int start = j + 1;
                int end = nums.length - 1;
                while (start < end) {
                    long sum = ((long) nums[i] + (long) nums[j] + (long) nums[start] + (long) nums[end]);
                    if (sum < (long) target) {
                        start++;
                    } else if (sum > (long) target) {
                        end--;
                    } else {
                        List<Integer> cache = new ArrayList();
                        cache.add(nums[i]);
                        cache.add(nums[j]);
                        cache.add(nums[start]);
                        cache.add(nums[end]);
                        res.add(cache);
                        while (start < end && nums[start] == nums[start + 1]) {
                            start++;
                        }
                        while (start < end && nums[end] == nums[end - 1]) {
                            end--;
                        }
                        start++;
                        end--;
                    }
                }
            }
        }
        return res;
    }
}
