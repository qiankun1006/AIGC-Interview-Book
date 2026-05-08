package 算法题;

public class 第214最短回文串 {

    //示例 1：
    //输入：s = "aacecaaa"
    //输出："aaacecaaa"
    //示例 2：
    //输入：s = "abcd"
    //输出："dcbabcd"

    /**
     * 给定一个字符串 s，通过在字符串前面添加字符将其转换为回文串，返回最短的回文串
     * 核心思路：利用KMP算法的部分匹配表（前缀函数），找到字符串s中最长的前缀回文子串，
     * 只需将剩余后缀反转后添加到原字符串前，即可得到最短回文串
     * @param s 输入的原始字符串
     * @return 转换后的最短回文串
     */
    public String shortestPalindrome(String s) {
        // 1. 获取原始字符串的长度
        int originalLen = s.length();
        // 处理空字符串的边界情况
        if (originalLen == 0) {
            return "";
        }

        // 2. 反转原始字符串，得到反转后的字符串
        StringBuilder reversedStr = new StringBuilder(s);
        reversedStr.reverse();

        // 3. 构造新字符串：原字符串 + 分隔符 + 反转字符串（用#避免原字符串和反转字符串的匹配干扰）
        StringBuilder combinedStr = new StringBuilder(s);
        combinedStr.append("#").append(reversedStr);
        int combinedLen = combinedStr.length();

        // 4. 构建KMP算法的前缀函数数组（部分匹配表），记录每个位置的最长相等前后缀长度
        // prefixArr[i] 表示 combinedStr[0...i] 中最长的相等前缀和后缀的长度
        int[] prefixArr = new int[combinedLen];

        // 遍历新字符串，计算每个位置的前缀函数值
        for (int i = 1; i < combinedLen; i++) {
            // 前一个位置的最长相等前后缀长度
            int prevPrefixLen = prefixArr[i - 1];

            // 如果当前字符不匹配，回退到上一个可能的前缀长度
            while (prevPrefixLen > 0 && combinedStr.charAt(i) != combinedStr.charAt(prevPrefixLen)) {
                prevPrefixLen = prefixArr[prevPrefixLen - 1];
            }

            // 如果当前字符匹配，前缀长度加1
            if (combinedStr.charAt(i) == combinedStr.charAt(prevPrefixLen)) {
                prevPrefixLen++;
            }

            // 记录当前位置的前缀函数值
            prefixArr[i] = prevPrefixLen;
        }

        // 5. 找到原字符串中最长的前缀回文子串的长度（即prefixArr最后一个位置的值）
        int maxPalindromePrefixLen = prefixArr[combinedLen - 1];

        // 6. 提取反转字符串中需要添加到原字符串前的部分：反转字符串的前(原始长度-最长回文前缀长度)个字符
        // 这部分是原字符串中无法构成回文的后缀的反转
        String addPart = reversedStr.substring(0, originalLen - maxPalindromePrefixLen);

        // 7. 拼接添加部分和原字符串，得到最短回文串
        return new StringBuilder(addPart).append(s).toString();
    }
}
