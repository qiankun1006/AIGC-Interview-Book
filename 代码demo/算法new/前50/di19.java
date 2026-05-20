package 算法new.前50;

public class di19 {
    // 1-> 3 -> 1 -> 6 -> 3 -> 2 -> 5 -> 7 -> 2
    //
    public ListNode removeNthFromEnd(ListNode head, int n) {
        ListNode pre = new ListNode();
        ListNode preRight = pre;
        pre.next = head;
        for (int i = 0; i < n; i++) {
            if(preRight == null) {
                return null;
            }
            preRight = preRight.next;
        }
        while(preRight.next != null) {
            pre = pre.next;
            preRight = preRight.next;
        }
        pre.next = pre.next.next;
        return head;
    }
}
