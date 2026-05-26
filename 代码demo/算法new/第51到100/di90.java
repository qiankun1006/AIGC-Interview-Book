package 算法new.第51到100;

import java.util.*;

public class di90 {
    //给你一个整数数组 nums ，其中可能包含重复元素，请你返回该数组所有可能的 子集（幂集）。
    //解集 不能 包含重复的子集。返回的解集中，子集可以按 任意顺序 排列。
    //
    //示例 1：
    //
    //输入：nums = [1,2,2]
    //输出：[[],[1],[1,2],[1,2,2],[2],[2,2]]
    //示例 2：
    //
    //输入：nums = [0]
    //输出：[[],[0]]
    public List<List<Integer>> subsetsWithDup(int[] nums) {
        Arrays.sort(nums);
        List<List<Integer>> res = new ArrayList<>();
        List<Integer> yiwei = new ArrayList();
        res.add(new ArrayList(yiwei));
        int length = nums.length;
        dfs(res, yiwei, 0, nums);
        return res;
    }

    void dfs(List<List<Integer>> res, List<Integer> yiwei, int index, int[] nums) {
        if (index == nums.length) {
            return;
        }
        for (int i = index; i < nums.length; i++) {
            //相同元素，要拿就只能拿第一个，因为前面是后面的超集
            //todo 47 也是重复元素，但是是排列，看起来写法类似，但是理解起来并不一样
            if (i != 0 && nums[i] == nums[i - 1] && i != index) {
                continue;
            }
            yiwei.add(nums[i]);
            res.add(new ArrayList(yiwei));
            dfs(res, yiwei, i + 1, nums);
            yiwei.remove(yiwei.size() - 1);
        }
    }

}
