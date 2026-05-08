package 练习12月13号;

public class K个一组翻转链表 {

    private class ListNode {
        int val;
        ListNode next;

        ListNode(int x, ListNode next) {
            this.val = x;
            this.next = next;
        }
    }

    public ListNode reverseKGroup(ListNode head, int k) {
        ListNode store = new ListNode(0, head);
        ListNode pre = store;
        while(pre.next != null) {
            ListNode post = convert(pre, k);
            if(post == null) {
                convert(pre, k);
                break;
            } else {
                pre = post;
            }
        }
        return store.next;
    }

    // 1-> 4 -> 5 -> 6  count = 0
    // 1-> 5 -> 4 -> 6  count = 1
    // 1-> 6 -> 5 -> 4  count = 2
    private ListNode convert(ListNode pre, int k) {
        ListNode head = pre.next;
        ListNode behind = head.next;
        int count = 0;
        while(count < k - 1 && behind != null) {
            head.next = behind.next;
            behind.next = head;
            pre.next = behind;
            behind = head.next;
            count++;
        }
        if(count == k - 1) {
            //head刚好就是下一轮翻转的pre
            return head;
        } else { //说明节点已经不足k个
            return null;
        }
    }

}
