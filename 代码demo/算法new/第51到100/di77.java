package 算法new.第51到100;

import java.util.*;

public class di77 {

    int N;
    int K;

    public List<List<Integer>> combine(int n, int k) {
        if (k == 0 || n == 0) {
            return new ArrayList<>();
        }
        N = n;
        K = k;
        List<List<Integer>> res = new ArrayList<>();
        dfs(new ArrayList<>(), res, 1);
        return res;
    }

    private void dfs(List<Integer> yiwei, List<List<Integer>> erwei, int cursor) {
        if (yiwei.size() == K) {
            erwei.add(new ArrayList<>(yiwei));
        }
        for (int i = cursor; i <= N; i++) {
            yiwei.add(i);
            dfs(yiwei, erwei, i + 1);
            yiwei.remove(yiwei.size() - 1);
        }
    }
}
