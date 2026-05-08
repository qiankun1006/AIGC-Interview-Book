package 算法new;

public class di14 {
    //编写一个函数来查找字符串数组中的最长公共前缀。
    //
    //如果不存在公共前缀，返回空字符串 ""。
    //
    //示例 1：
    //
    //输入：strs = ["flower","flow","flight"]
    //输出："fl"
    //示例 2：
    //
    //输入：strs = ["dog","racecar","car"]
    //输出：""
    //解释：输入不存在公共前缀。

    // ===== 前缀树（Trie）节点 =====
    static class TrieNode {
        TrieNode[] children = new TrieNode[26];
        // 经过该节点的字符串数量（用来判断是否所有字符串都经过这个节点）
        int passCount;
    }

    public static void main(String[] args) {
        di14 sol = new di14();
        System.out.println(sol.longestCommonPrefix(new String[]{"flower", "flow", "flight"})); // "fl"
        System.out.println(sol.longestCommonPrefix(new String[]{"dog", "racecar", "car"}));    // ""
        System.out.println(sol.longestCommonPrefix(new String[]{"ab", "a"}));                  // "a"
        System.out.println(sol.longestCommonPrefix(new String[]{"a"}));                        // "a"
    }

    public String longestCommonPrefix(String[] strs) {
        if (strs == null || strs.length == 0) return "";

        // 1. 把所有字符串插入 Trie，同时记录每个节点被经过的次数
        TrieNode root = new TrieNode();
        for (String s : strs) {
            insert(root, s);
        }

        // 2. 从根节点往下走：
        //    只要当前节点的 passCount == strs.length（所有字符串都经过）
        //    且该节点只有唯一一个孩子（不分叉），就继续往下
        //    遇到分叉 或 某个字符串在此结束（passCount < strs.length）就停止
        StringBuilder sb = new StringBuilder();
        TrieNode cur = root;
        while (true) {
            TrieNode next = null;
            int nextIdx = -1;
            // 找到唯一的孩子
            for (int i = 0; i < 26; i++) {
                if (cur.children[i] != null) {
                    if (next != null) {
                        // 出现分叉，停止
                        return sb.toString();
                    }
                    next = cur.children[i];
                    nextIdx = i;
                }
            }
            // 没有孩子（所有字符串都已到末尾）或下一个节点不是所有字符串共同经过的
            if (next == null || next.passCount < strs.length) {
                return sb.toString();
            }
            sb.append((char) ('a' + nextIdx));
            cur = next;
        }
    }

    private void insert(TrieNode root, String s) {
        TrieNode cur = root;
        for (char c : s.toCharArray()) {
            int idx = c - 'a';
            if (cur.children[idx] == null) {
                cur.children[idx] = new TrieNode();
            }
            cur = cur.children[idx];
            cur.passCount++;
        }
    }

    public String longestCommonPrefixV2(String[] strs) {
        if(strs.length == 0) {
            return "";
        }
        String res = "";
        for(int index = 0; index < strs[0].length(); index++) {
            char c = strs[0].charAt(index);
            boolean flag = true;
            for(int i = 0; i < strs.length; i++) {
                if(strs[i].length() <= index ||  c != strs[i].charAt(index)) {
                    flag = false;
                    break;
                }
            }
            if(flag) {
                res = res + c;
            } else {
                break;
            }
        }
        return res;
    }
}
