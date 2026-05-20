package 算法new.第51到100;

import java.util.*;

public class di76 {

    //给定两个字符串 s 和 t，长度分别是 m 和 n，返回 s 中的 最短窗口 子串，使得该子串包含 t 中的每一个字符（包括重复字符）。如果没有这样的子串，返回空字符串 ""。
    //测试用例保证答案唯一。
    //示例 1：
    //输入：s = "ADOBECODEBANC", t = "ABC"
    //输出："BANC"
    //解释：最小覆盖子串 "BANC" 包含来自字符串 t 的 'A'、'B' 和 'C'。

    //示例 2：
    //输入：s = "a", t = "a"
    //输出："a"
    //解释：整个字符串 s 是最小覆盖子串。

    //示例 3:
    //输入: s = "a", t = "aa"
    //输出: ""
    //解释: t 中两个字符 'a' 均应包含在 s 的子串中，
    //因此没有符合条件的子字符串，返回空字符串。
    public String minWindow(String s, String t) {
        int[] need = new int[128];  // t 中每个字符的需求量
        for (char c : t.toCharArray()) need[c]++;

        int left = 0, right = 0;
        int missing = t.length();  // 还缺多少个字符
        int start = 0, minLen = Integer.MAX_VALUE;

        while (right < s.length()) {
            // 右指针扩窗：need > 0 说明是 t 真正需要的字符，missing--
            char rc = s.charAt(right++);
            if (need[rc] > 0) {
                missing--;
            }
            need[rc]--;

            // 窗口已覆盖 t，尝试收缩左指针
            while (missing == 0) {
                if (right - left < minLen) {
                    start = left;
                    minLen = right - left;
                }
                // 左指针收缩：把字符还回去，need 变回 > 0 说明又缺了，missing++
                char lc = s.charAt(left++);
                need[lc]++;
                if (need[lc] > 0) missing++;
            }
        }
        return minLen == Integer.MAX_VALUE ? "" : s.substring(start, start + minLen);
    }


    public String minWindowV2(String s, String t) {
        Map<Character, Integer> charNeedCount = new HashMap<>();
        for (char c : t.toCharArray()) {
            charNeedCount.put(c, charNeedCount.getOrDefault(c, 0) + 1);
        }
        int left = 0;
        int right = 0;
        int start = 0;
        int min = Integer.MAX_VALUE;
        //全部匹配需要的字符
        int needCount = t.length();
        while(right < s.length()) {
            //来了一个字符，判断need里有没有
            if(charNeedCount.getOrDefault(s.charAt(right), 0) > 0) {
                needCount --;
            }
            // -1 必做
            charNeedCount.put(s.charAt(right), charNeedCount.getOrDefault(s.charAt(right), 0) - 1);
            // 下标滑动，必做
            right ++;
            //虽然是while，本质也是if
            //左边滑动窗口
            while(needCount == 0) { //todo 能走到这里 charNeedCount里的value值要么是0，要么是负数
                if(right - left < min) {
                    start = left;
                    min = right - left;
                }
                charNeedCount.put(s.charAt(left), charNeedCount.getOrDefault(s.charAt(left), 0) + 1);
                left ++;
                if(charNeedCount.getOrDefault(s.charAt(left), 0) > 0) {
                    //如果本次加了之后变得大于0了，说明本来肯定是0
                    needCount ++;
                }
            }
        }
        return min == Integer.MAX_VALUE ? "": s.substring(start, start + min);
    }
}
