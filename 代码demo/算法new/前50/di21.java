package 算法new.前50;

public class di21 {

    public ListNode mergeTwoLists(ListNode list1, ListNode list2) {
        ListNode res = new ListNode();
        ListNode cur = res;
        while(list1 != null || list2 != null) {
            if(list1 == null || list1.val >= list2.val) {
                cur.next = list2;
                cur = cur.next;
                list2 = list2.next;
                continue;
            }
            if(list2 == null || list1.val < list2.val) {
                cur.next = list1;
                cur = cur.next;
                list1 = list1.next;
            }
        }
        return res.next;
    }
}
