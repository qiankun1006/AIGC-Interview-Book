package 算法题;

import java.util.Stack;

public class 二叉树中第K小的元素230题 {
    private static class TreeNode {
        public int val;
        public TreeNode left;
        public TreeNode right;
        public TreeNode() {}
    }

    // 递归法
    public int kthSmallest(TreeNode root, int k) {
        return 0;
    }

    // 迭代法
    public int kthSmallest2(TreeNode root, int k) {
        Stack<TreeNode> stack = new Stack<>();
        int count = 0;
        while(root != null || stack.size() != 0) {
            if(root != null){
                stack.push(root);
                root = root.left;
            } else {
                TreeNode node = stack.pop();
                count++;
                if(count == k){
                    return node.val;
                }
                root = node.right;
            }
        }
        return 0;
    }

    public static void main(String[] args) {

    }
}



