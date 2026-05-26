package 算法new.第101到150;

import java.util.*;

public class di126 {

    //按字典 wordList 完成从单词 beginWord 到单词 endWord 转化，一个表示此过程的 转换序列 是形式上像
    // beginWord -> s1 -> s2 -> ... -> sk 这样的单词序列，并满足：
    //每对相邻的单词之间仅有单个字母不同。
    //转换过程中的每个单词 si（1 <= i <= k）必须是字典 wordList 中的单词。注意，beginWord 不必是字典 wordList 中的单词。
    //sk == endWord
    //给你两个单词 beginWord 和 endWord ，以及一个字典 wordList 。请你找出并返回所有从 beginWord 到 endWord 的 最短转换序列 ，如果不存在这样的转换序列，
    // 返回一个空列表。每个序列都应该以单词列表 [beginWord, s1, s2, ..., sk] 的形式返回。
    //示例 1：
    //输入：beginWord = "hit", endWord = "cog", wordList = ["hot","dot","dog","lot","log","cog"]
    //输出：[["hit","hot","dot","dog","cog"],["hit","hot","lot","log","cog"]]
    //解释：存在 2 种最短的转换序列：
    //"hit" -> "hot" -> "dot" -> "dog" -> "cog"
    //"hit" -> "hot" -> "lot" -> "log" -> "cog"
    //示例 2：
    //输入：beginWord = "hit", endWord = "cog", wordList = ["hot","dot","dog","lot","log"]
    //输出：[]
    //解释：endWord "cog" 不在字典 wordList 中，所以不存在符合要求的转换序列。
    public static void main(String[] args) {
        di126 solution = new di126();
        List<String> wordList = Arrays.asList("hot", "dot", "dog", "lot", "log", "cog");
        List<List<String>> result = solution.findLadders("hit", "cog", wordList);
        System.out.println("最终结果：" + result);
    }

    public List<List<String>> findLadders(String beginWord, String endWord, List<String> wordList) {
        List<List<String>> res = new ArrayList<>();
        Set<String> dictSet = new HashSet<>(wordList);
        if (!dictSet.contains(endWord)) return res;

        // visited：已出现过的单词不再进入下一层（保证最短路径）
        Set<String> visited = new HashSet<>();
        Queue<List<String>> queue = new LinkedList<>();
        queue.add(new ArrayList<>(Arrays.asList(beginWord)));
        visited.add(beginWord);

        boolean found = false;
        int level = 0;
        while (!queue.isEmpty() && !found) {
            level++;
            int size = queue.size();
            // subVisited：本层新访问的单词，处理完整层后才加入 visited
            // 原因：同一层多条路径可能走到同一个单词，必须都允许通过
            Set<String> subVisited = new HashSet<>();
            System.out.println("\n===== 第 " + level + " 层，当前队列中有 " + size + " 条路径 =====");
            for (int i = 0; i < size; i++) {
                List<String> path = queue.poll();
                String lastWord = path.get(path.size() - 1);
                List<String> neighbors = getNeighbors(lastWord, dictSet, visited);
                System.out.println("  扩展路径：" + path + "  →  邻居：" + neighbors);
                for (String neighbor : neighbors) {
                    List<String> newPath = new ArrayList<>(path);
                    newPath.add(neighbor);
                    if (neighbor.equals(endWord)) {
                        found = true;
                        res.add(newPath);
                        System.out.println("  ✓ 找到目标路径：" + newPath);
                    }
                    queue.add(newPath);
                    subVisited.add(neighbor);
                }
            }
            System.out.println("  本层新增单词：" + subVisited + "  加入 visited 后：" + visited);
            visited.addAll(subVisited);
        }
        return res;
    }

    // 枚举 word 在字典中所有合法的"一步可达"邻居（改变一个字母，且未被访问过）
    private List<String> getNeighbors(String word, Set<String> dictSet, Set<String> visited) {
        List<String> neighbors = new ArrayList<>();
        char[] chars = word.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char origin = chars[i];
            for (char ch = 'a'; ch <= 'z'; ch++) {
                if (ch == origin) continue;
                chars[i] = ch;
                String candidate = new String(chars);
                if (dictSet.contains(candidate) && !visited.contains(candidate)) {
                    neighbors.add(candidate);
                }
            }
            chars[i] = origin; // 还原
        }
        return neighbors;
    }

}
