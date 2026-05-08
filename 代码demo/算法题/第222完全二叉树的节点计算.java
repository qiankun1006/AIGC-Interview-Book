package 算法题;

public class 第222完全二叉树的节点计算 {
    public int countNodes(TreeNode root) {
        // 边界条件：根节点为空，返回0
        if (root == null) {
            return 0;
        }

        // 计算左子树高度（沿左子节点遍历）
        int leftHeight = getLeftHeight(root);
        // 计算右子树高度（沿右子节点遍历）
        int rightHeight = getRightHeight(root);

        // 左右高度相等，说明是满二叉树，节点数=2^h - 1
        if (leftHeight == rightHeight) {
            return (1 << leftHeight) - 1; // 位运算替代Math.pow(2, h)，效率更高
        } else {
            // 否则递归计算左右子树节点数 + 当前节点
            return 1 + countNodes(root.left) + countNodes(root.right);
        }
    }

    // 计算左子树高度（从当前节点开始，沿左子节点一直走）
    private int getLeftHeight(TreeNode node) {
        int height = 0;
        while (node != null) {
            height++;
            node = node.left;
        }
        return height;
    }

    // 计算右子树高度（从当前节点开始，沿右子节点一直走）
    private int getRightHeight(TreeNode node) {
        int height = 0;
        while (node != null) {
            height++;
            node = node.right;
        }
        return height;
    }
}

