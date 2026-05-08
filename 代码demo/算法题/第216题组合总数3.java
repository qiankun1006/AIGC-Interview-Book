package 算法题;

import java.util.*;

public class 第216题组合总数3 {

    //相加之和为n的k个数组合，只使用数字1到9，每个数只能用一次
    public List<List<Integer>> combinationSum3(int k, int n) {
        List<List<Integer>> res = new ArrayList<>();
        List<Integer> path = new ArrayList<>();
        dfs(k, n, 0, res, path);
        return res;
    }

    public void dfs(int k, int n, int sum, List<List<Integer>> res, List<Integer> path){
        if(path.size() == k && sum == n) {
            res.add(new ArrayList<>(path));
            return;
        }
        if(path.size() >= k || sum > n) {
            return;
        }
        int start = path.size() == 0 ? 1 : path.get(path.size() - 1) + 1;
        for(int i = start; i <= 9; i++){
            path.add(i);
            dfs(k, n, sum + i, res, path);
            path.remove(path.size() - 1);
        }
    }
}
