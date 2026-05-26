package 算法new.第101到150;

import java.util.*;

public class di113 {

    int target = 0;

    public List<List<Integer>> pathSum(TreeNode root, int sum) {
        List<List<Integer>> res = new ArrayList<>();
        if (root == null)
            return res;
        List<Integer> yiwei = new ArrayList<>();
        yiwei.add(root.val);
        target = sum;
        dfs(res, yiwei, root, root.val);
        return res;
    }

    void dfs(List<List<Integer>> res, List<Integer> yiwei, TreeNode node, int count) {
        if (node.left == null && node.right == null) {
            if (count == target) {
                res.add(new ArrayList(yiwei));
            }
            return;
        }
        if (node.left != null) {
            yiwei.add(node.left.val);
            dfs(res, yiwei, node.left, count + node.left.val);
            yiwei.remove(yiwei.size() - 1);
        }
        if (node.right != null) {
            yiwei.add(node.right.val);
            dfs(res, yiwei, node.right, count + node.right.val);
            yiwei.remove(yiwei.size() - 1);
        }
    }
}
