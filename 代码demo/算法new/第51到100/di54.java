package 算法new.第51到100;

import java.util.*;

public class di54 {

    public List<Integer> spiralOrder(int[][] matrix) {
        int up = 0;
        int down = matrix.length - 1;
        int left = 0;
        int right = matrix[0].length;
        List<Integer> res = new ArrayList<>();
        while (up <= down && left <= right) {
            //todo 上来就写错了，写成if(left <= right)
            if (up <= down)
                for (int i = left; i <= right; i++)
                    res.add(matrix[up][i]);
            up++;
            if (left <= right)
                for (int i = up; i <= down; i++)
                    res.add(matrix[i][right]);
            right--;
            if (up <= down)
                for (int i = right; i >= left; i--)
                    res.add(matrix[down][i]);
            down--;
            if (left <= right)
                for (int i = down; i >= up; i--)
                    res.add(matrix[i][left]);
            left++;
        }
        return res;
    }
}

class ListNode {
    int val;
    ListNode next;
    ListNode() {}
    ListNode(int val) { this.val = val; }
    ListNode(int val, ListNode next) { this.val = val; this.next = next; }
}
