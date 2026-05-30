package 算法new.第101到150;

import java.util.ArrayList;
import java.util.List;

public class di131 {

    //给你一个字符串 s，请你将 s 分割成一些 子串，使每个子串都是 回文串 。返回 s 所有可能的分割方案。
    //示例 1：
    //输入：s = "aab"
    //输出：[["a","a","b"],["aa","b"]]
    //
    //示例 2：
    //输入：s = "a"
    //输出：[["a"]]
    public List<List<String>> partition(String s) {
        int n = s.length();
        // isPalin[i][j]：s[i..j] 是否是回文串
        // 预处理比在回溯中每次调用 isPalindrome() 要高效，避免重复计算
        boolean[][] isPalin = new boolean[n][n];
        //预制好 dp，这个是关键
        // 输入：abcba
        //        j=0  j=1  j=2  j=3  j=4
        // i=4     -    -    -    -    T     "a"
        // i=3     -    -    -    T    F     "b","ba"
        // i=2     -    -    T    T    F     "c","cb","cba"
        // i=1     -    T    F    T    F     "b","bc","bcb","bcba"
        // i=0     T    F    F    F    T     "a","ab","abc","abcb","abcba"

        //todo  isPalin[i][j] 依赖什么？ 依赖 isPalin[i + 1][j - 1]，有了这个，两层for循环的就知道i和j的顺序了。
        // 可以确定i从大到小，j从小到大。然后还有两个点要确认，i在外面还是j在外面？各自的起点和终点？
        for (int i = n - 1; i >= 0; i--) {      // 从右向左枚举左端点
            for (int j = i; j < n; j++) {         // 从左向右枚举右端点
                // 单字符必然是回文；两端字符相等且内部也是回文 → 整体是回文
                isPalin[i][j] = s.charAt(i) == s.charAt(j) && (j - i <= 2 || isPalin[i + 1][j - 1]);
            }
        }

        List<List<String>> res = new ArrayList<>();
        backtrack(s, 0, isPalin, new ArrayList<>(), res);
        return res;
    }

    // 回溯：从 start 位置开始，枚举所有以 start 为起点的回文子串作为下一段
    private void backtrack(String s, int start, boolean[][] isPalin,
                           List<String> path, List<List<String>> res) {
        // 所有字符都已分配完，找到一种合法方案
        if (start == s.length()) {
            res.add(new ArrayList<>(path));
            return;
        }
        for (int end = start; end < s.length(); end++) {
            // 剪枝：只有 s[start..end] 是回文时才继续往后递归
            if (isPalin[start][end]) {
                path.add(s.substring(start, end + 1));
                backtrack(s, end + 1, isPalin, path, res);
                path.remove(path.size() - 1); // 回溯，撤销选择
            }
        }
    }
}
