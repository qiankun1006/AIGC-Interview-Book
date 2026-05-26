package 算法new.第51到100;

public class di91 {


    public int numDecodings(String s) {
        int n = s.length();
        int[] f = new int[n];    // f[i] = 以 s[i] 结尾的解码方案数

        for (int i = 0; i < n; i++) {
            // 情况1：单独解码 s[i]，只要它不是 '0' 就合法
            int oneDigit = s.charAt(i) - '0';
            if (oneDigit != 0) {
                f[i] += (i == 0) ? 1 : f[i - 1];
            }

            // 情况2：将 s[i-1] 和 s[i] 组合解码，合法范围 10~26
            if (i >= 1) {
                int twoDigit = (s.charAt(i - 1) - '0') * 10 + oneDigit;
                if (twoDigit >= 10 && twoDigit <= 26) {  //todo 比如 06就不行，0不能单独用，不能当前缀，只能当后缀
                    f[i] += (i == 1) ? 1 : f[i - 2];
                }
            }
        }
        return f[n - 1];
    }

    //示例 1：
    //
    //输入：s = "12"
    //输出：2
    //解释：它可以解码为 "AB"（1 2）或者 "L"（12）。
    //示例 2：
    //
    //输入：s = "226"
    //输出：3
    //解释：它可以解码为 "BZ" (2 26), "VF" (22 6), 或者 "BBF" (2 2 6) 。
    //示例 3：
    //
    //输入：s = "06"
    //输出：0
    //解释："06" 无法映射到 "F" ，因为存在前导零（"6" 和 "06" 并不等价）。
    public int numDecodingsV2(String s) {
        int[] dp = new int[s.length()];
        for (int i = 0; i < s.length(); i++) {
            int cur = s.charAt(i) - '0';
            //只要不等于0，当前元素都可以拆出来解码，这样就是dp[n-1]中
            if (cur != 0) {
                dp[i] += (i == 0) ? 1 : dp[i - 1]; //当前就是第一个，那就是一种
            }
            //考虑i-1，按两个元素来拆的情况
            if (i >= 1) {

            }
        }
        return 0;
    }


    public static void main(String[] args) {
        di91 d = new di91();
        System.out.println(d.numDecodings("12"));   // 期望 2
        System.out.println(d.numDecodings("226"));  // 期望 3
        System.out.println(d.numDecodings("06"));   // 期望 0
    }
}
