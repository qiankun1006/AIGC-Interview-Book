package 算法new.第101到150;

public class di109 {

    //todo 要注意的点，用cur当做当前节点，也就是右边第一个，因为本来快慢节点看起来是平分，奇数情况会让右边多一个，可以用3个节点验证一下
    public TreeNode sortedListToBST(ListNode head) {
        if (head == null) return null;
        if (head.next == null) return new TreeNode(head.val);
        ListNode pre = new ListNode(-1);
        pre.next = head;
        ListNode low = pre;
        ListNode fast = pre;
        //todo 这题如果直接用数组保存，面试可能过不了？但是其实快慢指针时间复杂度比较高
        while (fast.next != null && fast.next.next != null) {
            low = low.next;
            fast = fast.next.next;
        }
        ListNode cur = low.next;
        //todo 用cur当做当前节点，也就是右边第一个，因为本来快慢节点看起来是平分，奇数情况会让右边多一个，可以用3个节点验证一下
        TreeNode root = new TreeNode(cur.val);
        low.next = null;
        root.left = sortedListToBST(head);
        root.right = sortedListToBST(cur.next);
        return root;
    }
}
