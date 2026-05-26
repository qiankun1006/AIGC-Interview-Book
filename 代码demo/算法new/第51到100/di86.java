package 算法new.第51到100;

public class di86 {

    public ListNode partition(ListNode head, int x) {
        //用两个链表保存？
        ListNode res = new ListNode();
        ListNode small = res;
        ListNode res2 = new ListNode();
        ListNode big = res2;
        while (head != null) {  //不是特殊情况，循环条件一般都是head!=null
            ListNode cache = head;
            head = head.next;
            if (cache.val < x) {
                small.next = cache;
                cache.next = null;
                small = small.next;
            } else {
                big.next = cache;
                cache.next = null;
                big = big.next;
            }
        }
        small.next = res2.next;
        return res.next;
    }
}
