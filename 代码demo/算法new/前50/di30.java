package 算法new.前50;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class di30 {

    public List<Integer> findSubstring(String s, String[] words) {
        List<Integer> res = new ArrayList<>();
        if (s == null || s.length() == 0 || words == null || words.length == 0) {
            return res;
        }

        // 1. 统计 words 数组中每个单词的频率
        Map<String, Integer> wordCount = new HashMap<>();
        for (String word : words) {
            wordCount.put(word, wordCount.getOrDefault(word, 0) + 1);
        }

        int numWords = words.length;
        int wordLen = words[0].length();
        int totalLen = numWords * wordLen;

        // 2. 遍历 s 的所有可能起始位置
        // 只需要遍历 0 到 wordLen - 1 的位置即可，因为后面的位置可以通过循环移位覆盖
        for (int i = 0; i < wordLen; i++) {
            int left = i; // 滑动窗口左边界
            int right = i; // 滑动窗口右边界
            Map<String, Integer> currentCount = new HashMap<>(); // 当前窗口内的单词频率
            int count = 0; // 当前窗口内匹配上的单词数量

            // 开始滑动窗口
            while (right + wordLen <= s.length()) {
                // 截取一个单词
                String currWord = s.substring(right, right + wordLen);
                right += wordLen; // 右边界移动一个单词长度

                // 如果这个单词不在 words 中，重置窗口，以下一个wordLen作为起点
                if (!wordCount.containsKey(currWord)) {
                    left = right;
                    currentCount.clear();
                    count = 0;
                    continue;
                }

                // 更新当前窗口的单词计数
                currentCount.put(currWord, currentCount.getOrDefault(currWord, 0) + 1);
                count++;

                // 如果这个单词的出现次数超了，需要收缩左边界，一直丢左边的东西，丢到没超
                while (currentCount.get(currWord) > wordCount.get(currWord)) {
                    String leftWord = s.substring(left, left + wordLen);
                    currentCount.put(leftWord, currentCount.get(leftWord) - 1);
                    left += wordLen;
                    count--;
                }

                // 如果匹配上了所有单词，记录结果。全匹配上了也不能结束这轮，丢掉左边一个wordLen块，继续遍历。
                if (count == numWords) {
                    res.add(left);
                    // 收缩左边界，继续寻找下一个可能的匹配
                    String leftWord = s.substring(left, left + wordLen);
                    currentCount.put(leftWord, currentCount.get(leftWord) - 1);
                    left += wordLen;
                    count--;
                }
            }
        }

        return res;
    }

    public static void main(String[] args) {
        di30 solution = new di30();

        // 测试用例1: 基础用例
        // s = "barfoothefoobarman", words = ["foo","bar"]
        // 期望结果: [0, 9]  （"barfoo" 起始0，"foobar" 起始9）
        String s1 = "barfoothefoobarman";
        String[] words1 = {"foo", "bar"};
        System.out.println("测试1: " + solution.findSubstring(s1, words1)); // [0, 9]

        // 测试用例2: 无匹配
        // s = "wordgoodgoodgoodbestword", words = ["word","good","best","word"]
        // 期望结果: []
        String s2 = "wordgoodgoodgoodbestword";
        String[] words2 = {"word", "good", "best", "word"};
        System.out.println("测试2: " + solution.findSubstring(s2, words2)); // []

        // 测试用例3: 重复单词
        // s = "barfoofoobarthefoobarman", words = ["bar","foo","the"]
        // 期望结果: [6, 9, 12]
        String s3 = "barfoofoobarthefoobarman";
        String[] words3 = {"bar", "foo", "the"};
        System.out.println("测试3: " + solution.findSubstring(s3, words3)); // [6, 9, 12]

        // 测试用例4: words 中有重复单词
        // s = "wordgoodgoodgoodbestword", words = ["word","good","best","good"]
        // 期望结果: [8]
        String s4 = "wordgoodgoodgoodbestword";
        String[] words4 = {"word", "good", "best", "good"};
        System.out.println("测试4: " + solution.findSubstring(s4, words4)); // [8]

        // 测试用例5: 单个单词
        // s = "aaaaaaaa", words = ["aa","aa","aa"]
        // 期望结果: [0, 2]
        String s5 = "aaaaaaaa";
        String[] words5 = {"aa", "aa"};
        System.out.println("测试5: " + solution.findSubstring(s5, words5)); // [0, 1, 2, 3, 4]
    }
}
