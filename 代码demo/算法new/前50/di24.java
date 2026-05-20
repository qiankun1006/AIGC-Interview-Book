package 算法new.前50;

public class di24 {

    // pre -> mid -> next -> node -> node ...
    public ListNode swapPairs(ListNode head) {
        ListNode pre = new ListNode();
        ListNode res = pre;
        pre.next = head;

        while(pre.next != null && pre.next.next != null) {
            //拿到后面要交换的两个节点
            ListNode mid = pre.next;
            ListNode next = mid.next;

            //进行交换
            mid.next = next.next;
            next.next = mid;
            pre.next = next;

            pre = mid;
        }
        return res.next;
    }
}
