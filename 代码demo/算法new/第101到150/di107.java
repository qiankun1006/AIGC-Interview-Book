package 算法new.第101到150;

import java.util.*;

public class di107 {


    public List<List<Integer>> list = new ArrayList<>();

    public List<List<Integer>> levelOrderBottom(TreeNode root) {
        bfs(root, 0);
        Collections.reverse(list);
        return list;
    }

    public void bfs(TreeNode root, int step) {
        if (root == null) return;
        if (list.size() <= step) list.add(new ArrayList<>());
        bfs(root.left, step + 1);
        bfs(root.right, step + 1);
        list.get(step).add(root.val);
    }
}

class ListNode {
    int val;
    ListNode next;

    ListNode() {
    }

    ListNode(int val) {
        this.val = val;
    }

    ListNode(int val, ListNode next) {
        this.val = val;
        this.next = next;
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