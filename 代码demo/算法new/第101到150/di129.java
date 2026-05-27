package 算法new.第101到150;

public class di129 {

    int sum;

    public int sumNumbers(TreeNode root) {
        if(root == null) {
            return sum;
        }
        dfs(root.val, root);
        return sum;
    }

    void dfs(int cur, TreeNode node) {
        if (node.left == null && node.right == null) {
            sum += cur;
            return;
        }
        if (node.left != null) {
            dfs(cur * 10 + node.left.val, node.left);
        }
        if (node.right != null) {
            dfs(cur * 10 + node.right.val, node.right);
        }
    }

}
