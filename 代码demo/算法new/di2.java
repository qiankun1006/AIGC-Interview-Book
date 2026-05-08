package 算法new;

class ListNode {
    int val;
    ListNode next;
    ListNode() {}
    ListNode(int val) { this.val = val; }
    ListNode(int val, ListNode next) { this.val = val; this.next = next; }
}

public class di2 {

    public ListNode addTwoNumbers(ListNode l1, ListNode l2) {
        ListNode res = new ListNode();
        ListNode cur = res;
        int jinwei = 0;
        while(l1 != null || l2 != null || jinwei != 0) {
            int sum = jinwei;
            if(l1 != null) {
                sum = sum + l1.val;
            }
            if(l2 != null) {
                sum = sum + l2.val;
            }
            cur.val = sum % 10;
            jinwei = sum / 10;
            ListNode node = new ListNode();
            cur.next = node;
            cur = node;
        }
        return res;
    }
}
