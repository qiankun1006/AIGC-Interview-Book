package 算法new;

public class di25 {

    public ListNode reverseKGroup(ListNode head, int k) {
        ListNode dummy = new ListNode(0, head);
        ListNode pre = dummy;
        while (pre.next != null) {
            ListNode post = rotate(pre, k);
            if (post == null) {
                rotate(pre, k);
                break;
            } else {
                pre = post;
            }
        }
        return dummy.next;
    }

    //先写206题，就简单了
    public ListNode rotate(ListNode pre, int k) {
        ListNode cur = pre.next;
        int count = 1;
        //头插法
        while (count < k && cur != null && cur.next != null) {
            ListNode next = cur.next;
            cur.next = next.next;
            next.next = pre.next;
            pre.next = next;
            count ++;
        }
        return count == k ? cur : null;
    }
}
