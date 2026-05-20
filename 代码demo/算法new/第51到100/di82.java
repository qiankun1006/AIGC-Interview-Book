package 算法new.第51到100;

public class di82 {

    public ListNode deleteDuplicates(ListNode head) {  //链表两大解法：头插法，递归法。
        //todo 83题是原地修改，只需要一个 cur 在原链表上走，改 next 指针就行。
        // 82题是重新构建，相当于从原链表里"捡"合格的节点接到新链表上，这就需要两个角色：
        // 如果只有一个指针，接上新节点之后就找不到"尾巴"在哪了，没法继续往后接。
        ListNode res = new ListNode();
        ListNode cache = res;
        while (head != null) {   //第一次在链表题上面用到贪心
            ListNode start = head;
            int count = 1;
            while (head.next != null && head.next.val == head.val) {
                head = head.next;
                count++;
            }
            head = head.next;
            if (count == 1) {
                cache.next = start;
                start.next = null;
                cache = start;
            }
        }
        return res.next;
    }
}
