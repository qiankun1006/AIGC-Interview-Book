package 算法new.第51到100;

import java.util.Stack;

public class di99 {

    // Morris 中序遍历：O(n) 时间，O(1) 空间
    // BST 中序遍历结果是有序的，被交换的两个节点会破坏这个有序性：
    //   - 第一个错误节点：第一次出现"前驱 > 当前"时的前驱节点
    //   - 第二个错误节点：最后一次出现"前驱 > 当前"时的当前节点
    //todo 自己写一遍
    public void recoverTree(TreeNode root) {
        Stack<TreeNode> st = new Stack<>();
        //记录不合理的前后节点
        //  12345678 -> 8和3交换
        //  12845673  这里会出现84 和 73 两对不合理的，当然如果3和4交换，就只有一对不合理的。
        TreeNode pre1 = null;
        TreeNode cur1 = null;
        TreeNode pre2 = null;
        TreeNode cur2 = null;
        //记录前面一个
        TreeNode pre = new TreeNode(0);
        int count = 1;

        while (!st.isEmpty() || root != null) {
            if (root != null) {
                st.push(root);
                root = root.left;
            } else {
                //左边拿到底了
                TreeNode cur = st.pop();
                if (count != 1) {
                    //看看前一个是不是比当前小
                    if (pre.val > cur.val) {
                        if (pre1 == null) {
                            pre1 = pre;
                            cur1 = cur;
                        } else {
                            pre2 = pre;
                            cur2 = cur;
                        }
                    }
                } else {
                    count ++;
                }
                pre = cur;
                root = cur.right;
            }
        }
        // 有两对逆序：交换 pre1 和 cur2（非相邻交换的两端）
        // 只有一对逆序：交换 pre1 和 cur1（相邻交换）
        TreeNode second = (pre2 != null) ? cur2 : cur1;
        int tmp = pre1.val;
        pre1.val = second.val;
        second.val = tmp;
    }
}
