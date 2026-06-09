package 算法new.第101到150;

import java.util.*;

public class di140 {

    //给定一个字符串 s 和一个字符串字典 wordDict ，在字符串 s 中增加空格来构建一个句子，使得句子中所有的单词都在词典中。以任意顺序 返回所有这些可能的句子。
    //注意：词典中的同一个单词可能在分段中被重复使用多次。
    //
    //示例 1：
    //输入:s = "catsanddog", wordDict = ["cat","cats","and","sand","dog"]
    //输出:["cats and dog","cat sand dog"]
    //示例 2：
    //输入:s = "pineapplepenapple", wordDict = ["apple","pen","applepen","pine","pineapple"]
    //输出:["pine apple pen apple","pineapple pen apple","pine applepen apple"]
    //解释: 注意你可以重复使用字典中的单词。
    //示例 3：
    //输入:s = "catsandog", wordDict = ["cats","dog","sand","and","cat"]
    //输出:[]

    //todo 记忆化 DFS：O(n^2 * 结果数) 时间，O(n * 结果数) 空间
    // memo.get(i) 存储从下标 i 开始能拼出的所有后缀句子列表
    private Map<Integer, List<String>> memo = new HashMap<>();

    public List<String> wordBreak(String s, List<String> wordDict) {
        Set<String> wordSet = new HashSet<>(wordDict);
        return dfs(s, 0, wordSet);
    }

    private List<String> dfs(String s, int start, Set<String> wordSet) {
        if (memo.containsKey(start)) return memo.get(start);

        List<String> result = new ArrayList<>();

        // 到达末尾：返回一个空字符串作为"句子起始"，供上层拼接
        if (start == s.length()) {
            result.add("");
            memo.put(start, result);
            return result;
        }

        for (int end = start + 1; end <= s.length(); end++) {
            String word = s.substring(start, end);
            if (wordSet.contains(word)) {
                // 当前词匹配，递归处理剩余部分
                List<String> suffixes = dfs(s, end, wordSet);
                for (String suffix : suffixes) {
                    // suffix 为空说明已到末尾，直接用 word；否则拼上空格
                    result.add(suffix.isEmpty() ? word : word + " " + suffix);
                }
            }
        }

        memo.put(start, result);
        return result;
    }

    public static void main(String[] args) {
        // 示例1：期望 ["cats and dog","cat sand dog"]
        di140 sol1 = new di140();
        System.out.println("示例1: " + sol1.wordBreak("catsanddog",
                Arrays.asList("cat", "cats", "and", "sand", "dog")));

        // 示例2：期望 ["pine apple pen apple","pineapple pen apple","pine applepen apple"]
        di140 sol2 = new di140();
        System.out.println("示例2: " + sol2.wordBreak("pineapplepenapple",
                Arrays.asList("apple", "pen", "applepen", "pine", "pineapple")));

        // 示例3：期望 []
        di140 sol3 = new di140();
        System.out.println("示例3: " + sol3.wordBreak("catsandog",
                Arrays.asList("cats", "dog", "sand", "and", "cat")));
    }
}
