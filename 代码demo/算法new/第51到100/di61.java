package 算法new.第51到100;


public class di61 {

    public ListNode rotateRight(ListNode head, int k) {
        if (k == 0 || head == null) {
            return head;
        }
        //k的数量可能比链表节点大。
        ListNode left = new ListNode();
        ListNode right = new ListNode();
        left.next = head;
        right.next = head;
        //取余
        k = k % count(head);
        for (int i = 0; i < k; i++) {
            right = right.next;
        }
        while (right.next != null) {
            left = left.next;
            right = right.next;
        }
        right.next = head;
        ListNode res = left.next;
        left.next = null;
        return res;
    }

    int count(ListNode head) {
        ListNode countNode = new ListNode();
        countNode = head;
        int count = 0;
        while (countNode != null) {
            count++;
            countNode = countNode.next;
        }
        return count;
    }
}
