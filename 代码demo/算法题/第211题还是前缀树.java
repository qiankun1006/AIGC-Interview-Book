package 算法题;

//208也是前缀树
public class 第211题还是前缀树 {
    // 字典树节点类
    private static class TrieNode {
        TrieNode[] children; // 子节点数组，对应a-z
        boolean isEnd;       // 标记是否为单词结尾

        public TrieNode() {
            children = new TrieNode[26]; // 26个小写英文字母
            isEnd = false;
        }
    }

    private TrieNode root; // 字典树根节点

    /**
     * 初始化字典树
     */
    public 第211题还是前缀树() {
        root = new TrieNode();
    }

    /**
     * 添加单词到字典树中
     */
    public void addWord(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            int index = c - 'a'; // 计算字符对应的索引（0-25）
            // 若当前字符的子节点不存在，创建新节点
            if (node.children[index] == null) {
                node.children[index] = new TrieNode();
            }
            // 移动到子节点
            node = node.children[index];
        }
        // 标记单词结尾
        node.isEnd = true;
    }

    /**
     * 查找单词（支持.通配符）
     */
    public boolean search(String word) {
        // 从根节点开始，递归匹配整个单词
        return dfs(word, 0, root);
    }

    /**
     * 深度优先搜索辅助函数
     *
     * @param word  要匹配的单词
     * @param index 当前匹配的字符索引
     * @param node  当前遍历的字典树节点
     * @return 是否匹配成功
     */
    private boolean dfs(String word, int index, TrieNode node) {
        // 递归终止条件：已遍历完所有字符
        if (index == word.length()) {
            return node.isEnd; // 检查当前节点是否为单词结尾
        }

        char c = word.charAt(index);
        if (c == '.') {
            // 通配符：遍历所有存在的子节点，继续递归匹配
            for (TrieNode child : node.children) {
                if (child != null && dfs(word, index + 1, child)) {
                    return true;
                }
            }
            // 所有子节点都不匹配
            return false;
        } else {
            // 普通字符：匹配对应的子节点
            int childIndex = c - 'a';
            TrieNode child = node.children[childIndex];
            // 子节点不存在则匹配失败，否则继续递归
            return child != null && dfs(word, index + 1, child);
        }
    }
}

/**
 * Your WordDictionary object will be instantiated and called as such:
 * WordDictionary obj = new WordDictionary();
 * obj.addWord(word);
 * boolean param_2 = obj.search(word);
 */
