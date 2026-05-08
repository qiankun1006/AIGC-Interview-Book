package 算法new;

public class di10 {

    //给你一个字符串 s 和一个字符规律 p，请你来实现一个支持 '.' 和 '*' 的正则表达式匹配。
    //'.' 匹配任意单个字符
    //'*' 匹配零个或多个前面的那一个元素
    //返回一个布尔值，表示匹配是否覆盖整个输入字符串（而非部分）。
    //
    //示例 1：
    //
    //输入：s = "aa", p = "a"
    //输出：false
    //解释："a" 无法匹配 "aa" 整个字符串。
    //示例 2:
    //
    //输入：s = "aa", p = "a*"
    //输出：true
    //解释：因为 '*' 代表可以匹配零个或多个前面的那一个元素, 在这里前面的元素就是 'a'。因此，字符串 "aa" 可被视为 'a' 重复了一次。
    //示例 3：
    //
    //输入：s = "ab", p = ".*"
    //输出：true
    //解释：".*" 表示可匹配零个或多个（'*'）任意字符（'.'）。

    public static void main(String[] args) {
        // 复杂输入：s="aab", p="c*a*b"
        // c* 可匹配0个c，a* 可匹配多个a，最终匹配 "aab" → 预期 true
        String s = "aab";
        String p = "c*a*b";
        System.out.println("=== 正则匹配 DP 可视化 ===");
        System.out.println("s = \"" + s + "\"");
        System.out.println("p = \"" + p + "\"");
        System.out.println();

        boolean result = new di10().isMatch(s, p);
        System.out.println("最终结果: " + result);
    }

    public boolean isMatch(String s, String p) {
        // 在 s 和 p 前各加一个占位符 ' '
        // 这样 s[i] 就直接对应 dp 第 i 行，p[j] 直接对应 dp 第 j 列
        // 不再需要 charAt(i-1) 这种 -1 偏移，下标完全对齐
        s = " " + s;
        p = " " + p;
        // s.length() == m，p.length() == n，dp[i][j] 即 s前i字符 vs p前j字符
        int m = s.length(), n = p.length();
        boolean[][] dp = new boolean[m][n];

        // dp[0][0]: 空串 vs 空串，匹配
        dp[0][0] = true;

        // 初始化第0行：空串 s 能被 p 的前 j 个字符匹配的情况
        // 只有 p[j]=='*' 时，p[1..j] 才可能匹配空串（每次消掉一对 x*）
        // j 从 2 开始，步长 2（每次看一对 x*）
        for (int j = 2; j < n; j += 2)
            dp[0][j] = dp[0][j - 2] && p.charAt(j) == '*';

        for (int i = 1; i < m; i++) {
            for (int j = 1; j < n; j++) {
                if (p.charAt(j) == '*') {
                    // p[j]=='*'，有两种选择：
                    // 1. 让 p[j-1]* 匹配 0 次 → dp[i][j-2]
                    // 2. 让 p[j-1]* 再多匹配一个 s[i] → dp[i-1][j] && (s[i]==p[j-1] || p[j-1]=='.')
                    dp[i][j] = dp[i][j - 2]
                            || (dp[i - 1][j] && (s.charAt(i) == p.charAt(j - 1) || p.charAt(j - 1) == '.'));
                } else {
                    // p[j] 是普通字符或 '.'，直接看 s[i] 和 p[j] 是否匹配
                    dp[i][j] = dp[i - 1][j - 1]
                            && (p.charAt(j) == '.' || s.charAt(i) == p.charAt(j));
                }
            }
        }

        // ===== 打印 DP 表格 =====
        // 表头第一行：列标题，j=0 为空串占位列，j>=1 对应 p[j]
        System.out.printf("%-10s|", "dp[i][j]");
        System.out.printf("  %-8s", "j=0(\"\")");
        for (int j = 1; j < n; j++) {
            System.out.printf("  j=%d(%c)  ", j, p.charAt(j));
        }
        System.out.println();

        // 分隔线
        System.out.print("----------|");
        for (int j = 0; j < n; j++) System.out.print("----------");
        System.out.println();

        // 每行：i=0 为空串行，i>=1 对应 s[i]
        for (int i = 0; i < m; i++) {
            if (i == 0) {
                System.out.printf("%-10s|", "i=0(\"\")");
            } else {
                System.out.printf("i=%d(s=%c)  |", i, s.charAt(i));
            }
            for (int j = 0; j < n; j++) {
                System.out.printf("  %-8s", dp[i][j] ? "T" : "F");
            }
            System.out.println();
        }

        return dp[m - 1][n - 1];
    }
}
