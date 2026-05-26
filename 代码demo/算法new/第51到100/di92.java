package 算法new.第51到100;

public class di92 {

    //给你单链表的头指针 head 和两个整数 left 和 right ，其中 left <= right 。请你反转从位置 left 到位置 right 的链表节点，返回 反转后的链表 。
    //
    //示例 1：
    //输入：head = [1,2,3,4,5], left = 2, right = 4
    //输出：[1,4,3,2,5]
    //示例 2：
    //输入：head = [5], left = 1, right = 1
    //输出：[5]
    public ListNode reverseBetween(ListNode head, int left, int right) {
        ListNode leftNode = new ListNode();
        leftNode.next = head;
        ListNode res = leftNode;
        for (int i = 0; i < left - 1; i++) {
            leftNode = leftNode.next;
        }
        ListNode mid = leftNode.next;
        
        for (int i = 0; i < right - left; i++) {
            ListNode next = mid.next;
            mid.next = next.next;   // mid 往后跳过 next
            next.next = leftNode.next; // next 插到 leftNode 后面（头插）
            leftNode.next = next;
        }
        return res.next;
    }
}
