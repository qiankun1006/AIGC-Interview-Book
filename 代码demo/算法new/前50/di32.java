package 算法new.前50;

public class di32 {

    /**
     * LeetCode 32 - 最长有效括号
     *
     * 核心结论：最长有效子串一定以 '(' 开头或以 ')' 结尾（反证法）
     *   反证：假设最长有效串既不靠左端也不靠右端，则它左边还有字符X，右边还有字符Y。
     *         有效串开头必是 '('，结尾必是 ')'，所以X或Y可以继续向外扩展，矛盾。
     *
     * 因此只需正向 + 反向各扫描一遍即可覆盖所有情况。
     *
     * -----------------------------------------------------------------------
     * 正向扫描的盲区（只正向扫描不够的反例）：
     *   例如 "(()":  left 始终 > right，永远触发不了 left==right，正向结果为0（漏掉了！）
     *   需要反向扫描来补救：从右往左，')' 作为"基准"，left>right 时重置。
     *   反向结果：遇到 ')' right=1，遇到 '(' left=1，left==right → max=2 ✓
     *
     * -----------------------------------------------------------------------
     * 为什么不能用 "right < left 时也记录 2*right" 来一遍完成？
     *   反例 "()(()":
     *     正确答案是 2，但 i=4 时 right=2 < left=3，记录 2*2=4，**错误！**
     *   原因：right < left 时括号还没配对完，这 right 个右括号对应的左括号不一定连续，
     *         贸然记录会把"碎片"当成完整有效串。
     *
     * -----------------------------------------------------------------------
     * 想一遍遍历完成，正确方案是用【栈】：
     *   栈里存下标，栈底放哨兵 -1 表示有效串起点的前一位。
     *   遇到 '(' 压入下标；遇到 ')' 弹出栈顶：
     *     - 栈空 → 当前 ')' 作为新哨兵压入
     *     - 栈非空 → i - stack.peek() 即为当前有效串长度
     *   见 longestValidParenthesesStack() 方法。
     *
     * -----------------------------------------------------------------------
     * 典型用例验证：
     *   ")()((": 正向扫描，开头 ')' 触发 right>left 重置，之后找到 "()" → max=2 ✓
     *   "(()":   正向漏掉，反向补到 2 ✓
     *   "(()((": 正向 right<left 始终不触发，反向从右找到 "()" → max=2 ✓
     */
    public int longestValidParentheses(String s) {
        int left = 0, right = 0, maxlength = 0;
        // 正向扫描：能找到"右括号多余"导致重置的情况，盲区是"左括号多余"
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '(') {
                left++;
            } else {
                right++;
            }
            if (left == right) {
                maxlength = Math.max(maxlength, 2 * right);
            } else if (right >= left) {
                // 右括号多了，当前窗口不可能继续合法，重置
                left = right = 0;
            }
        }
        left = right = 0;
        // 反向扫描：对称处理，专门补救"左括号多余"的盲区（如 "(()","(()(()"）
        for (int i = s.length() - 1; i >= 0; i--) {
            if (s.charAt(i) == '(') {
                left++;
            } else {
                right++;
            }
            if (left == right) {
                maxlength = Math.max(maxlength, 2 * left);
            } else if (left >= right) {
                // 左括号多了，重置
                left = right = 0;
            }
        }
        return maxlength;
    }

    // 用栈一遍遍历的正确方案，O(n) 时间 O(n) 空间
    public int longestValidParenthesesStack(String s) {
        int max = 0;
        java.util.Deque<Integer> stack = new java.util.ArrayDeque<>();
        stack.push(-1); // 哨兵，记录有效串起点的前一位下标
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '(') {
                stack.push(i); // 压入左括号的下标
            } else {
                stack.pop(); // 弹出匹配的左括号（或哨兵）
                if (stack.isEmpty()) {
                    stack.push(i); // 无法匹配，当前 ')' 作为新哨兵
                } else {
                    max = Math.max(max, i - stack.peek()); // 当前位置 - 栈顶 = 有效串长度
                }
            }
        }
        return max;
    }
}
