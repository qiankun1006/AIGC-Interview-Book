package 算法new.第101到150;

public class di124 {

    // 全局记录遍历过程中遇到的最大路径和（路径可以不经过根节点）
    int maxSum = Integer.MIN_VALUE;

    public int maxPathSum(TreeNode root) {
        maxSolo(root);
        return maxSum;
    }

    private int maxSolo(TreeNode root) {
        if (root == null) {
            return 0;
        }

        // 如果左/右子树贡献为负，不如不选（取 0 相当于截断这条路径）
        int leftSum  = Math.max(maxSolo(root.left),  0);
        int rightSum = Math.max(maxSolo(root.right), 0);

        // 以 root 为"拱顶"的路径和（左 + root + 右），尝试更新全局最大值
        maxSum = Math.max(maxSum, leftSum + rightSum + root.val);

        //todo 向父节点只能提供单侧延伸的最大值
        return root.val + Math.max(leftSum, rightSum);
    }

}
