package 算法new.第51到100;

import java.util.ArrayList;
import java.util.List;

public class di95 {

    public List<TreeNode> generateTrees(int n) {
        if (n < 1) return new ArrayList<TreeNode>();
        return rescur(1, n);
    }

    public List<TreeNode> rescur(int min, int max) {
        List<TreeNode> res = new ArrayList<>();
        // 空区间：返回含 null 的列表，表示"空子树"占位
        if (min > max) {
            res.add(null);
            return res;
        }
        for (int i = min; i <= max; i++) {
            List<TreeNode> leftList = rescur(min, i - 1);
            List<TreeNode> rightList = rescur(i + 1, max);
            //叉乘关系
            for (TreeNode left : leftList) {
                for (TreeNode right : rightList) {
                    TreeNode root = new TreeNode(i);
                    root.left = left;
                    root.right = right;
                    res.add(root);
                }
            }
        }
        return res;
    }
}

class TreeNode {

    int val;

    TreeNode left;

    TreeNode right;

    TreeNode(int x) {
        val = x;
    }
}