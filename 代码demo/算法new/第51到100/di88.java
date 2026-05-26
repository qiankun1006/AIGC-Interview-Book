package 算法new.第51到100;

public class di88 {

    public void merge(int[] nums1, int m, int[] nums2, int n) {
        int l1 = m - 1;
        int l2 = n - 1;  //两个指针存储
        int index = m + n - 1;
        while (index >= 0) {
            if (l1 < 0) {
                nums1[index] = nums2[l2];
                l2--;
                index--;
                continue;
            }
            if (l2 < 0) {
                nums1[index] = nums1[l1];
                l1--;
                index--;
                continue;
            }
            if (nums1[l1] < nums2[l2]) {
                nums1[index] = nums2[l2];
                l2--;
            } else {
                nums1[index] = nums1[l1];
                l1--;
            }
            index--;
        }
    }
}
