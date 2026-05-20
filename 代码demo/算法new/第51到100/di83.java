package 算法new.第51到100;

public class di83 {
    public ListNode deleteDuplicates(ListNode head) {
        ListNode cur = head;
        while (cur != null && cur.next != null) {
            if (cur.next.val == cur.val) {
                cur.next = cur.next.next;  // 跳过重复节点
            } else {
                cur = cur.next;
            }
        }
        return head;  // 头节点不会被删，直接返回
    }
}
