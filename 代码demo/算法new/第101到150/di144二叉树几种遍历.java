package 算法new.第101到150;

import java.util.*;

public class di144二叉树几种遍历 {


    /*  统一按这个样子思考就行了
                   3
                 2
               3   6
             4
               5
     */

    //后序
    public List<Integer> postorderTraversal(TreeNode root) {
        TreeNode p = root, r = null;
        List<Integer> ans = new ArrayList();
        Stack<TreeNode> s = new Stack();
        while (!s.isEmpty() || p != null) {
            if (p != null) {
                s.push(p);
                p = p.left;
            } else {
                p = s.peek();
                if (p.right == null || p.right == r) {  //能来到这一步
                    //要么是没有左孩子了，要么是左孩子被访问过了，然后被置为null了（p被置null）
                    ans.add(p.val);
                    r = p;
                    s.pop();
                    p = null;  //被访问的节点置为null
                } else
                    p = p.right;
            }
        }
        return ans;
    }

    //前序
    public List<Integer> preorderTraversal(TreeNode root) {
        Stack<TreeNode> st = new Stack<>();
        List<Integer> res = new ArrayList<>();
        while (root != null || st.size() != 0) {
            if (root != null) {
                res.add(root.val);
                st.push(root);
                root = root.left;
            } else {
                root = st.pop();
                root = root.right;
            }
        }
        return res;
    }


    //中序
    public List<Integer> inorderTraversal(TreeNode root) {
        Stack<TreeNode> st = new Stack<>();
        Queue<TreeNode> queue = new LinkedList<>();
        List<Integer> res = new ArrayList<>();
        while (!st.isEmpty() || root != null) {
            if (root != null) {
                st.push(root);
                root = root.left;
            } else {
                //左边拿到底了
                TreeNode cur = st.pop();
                res.add(cur.val);
                root = cur.right;
            }
        }
        return res;
    }

    /*  统一按这个样子思考就行了
               3
             2
           3   6
         4
           5
 */
    public List<Integer> postorderTraversalV2(TreeNode root) {
        Stack<TreeNode> st = new Stack<>();
        List<Integer> res = new ArrayList<>();
        //记录上一次访问的节点
        TreeNode last = null;
        while (root != null || !st.isEmpty()) {
            if(root != null) {
                st.push(root);
                root = root.left;
            } else {
                root = st.peek(); // 注意不出栈
                if(root.right == null || root.right == last) {
                    res.add(root.val);
                    last = root;
                    st.pop();
                    root = null;  //todo 最关键的点，被访问的节点置为null，因为下一轮还要从栈里面拿
                } else {
                    st.push(root.right);
                }
            }
        }
        return res;
    }


}
