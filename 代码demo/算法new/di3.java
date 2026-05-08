package 算法new;

import java.util.*;

//给定一个字符串 s ，请你找出其中不含有重复字符的 最长 子串 的长度。
//示例 1:
//输入: s = "abcabcbb"
//输出: 3
//解释: 因为无重复字符的最长子串是 "abc"，所以其长度为 3。注意 "bca" 和 "cab" 也是正确答案。
public class di3 {

    public static void main(String[] args) {
        di3 di3 = new di3();
        di3.lengthOfLongestSubstring("bpfbhmipx");
    }

    public int lengthOfLongestSubstring(String s) {
        Map<Character, Integer> charIntMap = new HashMap<>();
        Integer res = Integer.MIN_VALUE;
        for (int i = 0; i < s.length(); i++) {
            if(!charIntMap.containsKey(s.charAt(i))) {
                charIntMap.put(s.charAt(i), i);
                //记录最大
                res = Math.max(res, charIntMap.size());
            } else {
                //将重复的位置，前面的都出栈
                int start = charIntMap.get(s.charAt(i));
                while(start >=0 && charIntMap.containsKey(s.charAt(start))) {
                    charIntMap.remove(s.charAt(start));
                    start --;
                }
                charIntMap.put(s.charAt(i), i);
                //记录最大
                res = Math.max(res, charIntMap.size());
            }
        }
        return res;
    }
}
