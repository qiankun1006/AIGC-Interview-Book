package 算法new;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class di40 {
    private List<List<Integer>> res = new ArrayList<>();

    public List<List<Integer>> combinationSum2(int[] candidates, int target) {
        // 1. 关键步骤：先对数组排序
        // 排序是为了方便后面“跳过重复的数字”，从而去重
        Arrays.sort(candidates);

        // 2. 调用回溯函数
        // 初始 deep 为 0，表示从左往右开始搜
        dfs(candidates, 0, target, new ArrayList<>(), 0);
        return res;
    }

    private void dfs(int[] candidates, int sum, int target, List<Integer> yiwei, int deep) {
        // 剪枝：如果当前和已经超过目标值，直接返回
        if (sum > target) {
            return;
        }

        // 找到符合条件的组合
        if (sum == target) {
            res.add(new ArrayList<>(yiwei));
            return;
        }

        // 从 deep 开始遍历（保证每个数只用一次，所以下一层是 i + 1）
        for (int i = deep; i < candidates.length; i++) {

            // 【去重关键】：如果当前数字和前一个数字相同，且不是第一层递归（i > deep），
            // 则跳过，防止生成重复组合。
            // 例如：输入 [1,1,2]，如果不加这个判断，会搜出两个 [1,2]
            // todo 和15题是一样的
            if (i > deep && candidates[i] == candidates[i - 1]) {
                continue;
            }

            // 做选择
            yiwei.add(candidates[i]);

            // 递归：注意这里传的是 i + 1，因为每个数字只能用一次
            dfs(candidates, sum + candidates[i], target, yiwei, i + 1);

            // 撤销选择
            yiwei.remove(yiwei.size() - 1);
        }
    }
}
