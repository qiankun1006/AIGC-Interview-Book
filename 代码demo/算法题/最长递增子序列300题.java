package 算法题;


/**
 * 优化版：贪心 + 二分查找求解 LIS（时间 O(n log n)，空间 O(n)）
 */
public class 最长递增子序列300题 {
    public int lengthOfLIS(int[] nums) {
        if (nums == null || nums.length == 0) {
            return 0;
        }
        int n = nums.length;
        // tails：长度为i+1的LIS的最小末尾元素
        int[] tails = new int[n];
        int len = 0; // 记录tails的有效长度（即当前LIS长度）

        for (int num : nums) {
            // 二分查找：找tails中第一个 >= num 的位置
            int left = 0, right = len;
            while (left < right) {
                int mid = left + (right - left) / 2;
                if (tails[mid] < num) {
                    // 目标在右区间
                    left = mid + 1;
                } else {
                    // 目标在左区间（包含mid）
                    right = mid;
                }
            }
            // 替换/追加元素
            tails[left] = num;
            // 若left == len，说明num是新的最大值，追加到末尾，长度+1
            if (left == len) {
                len++;
            }
        }
        return len;
    }

    // 测试
    public static void main(String[] args) {
        最长递增子序列300题 lis = new 最长递增子序列300题();
        int[] nums1 = {10,9,2,5,3,7,101,18};
        System.out.println(lis.lengthOfLIS(nums1)); // 输出：4

        int[] nums2 = {0,1,0,3,2,3};
        System.out.println(lis.lengthOfLIS(nums2)); // 输出：4（子序列 [0,1,2,3]）

        int[] nums3 = {3,5,6,2,5,4,19,5,6,7,12};
        System.out.println(lis.lengthOfLIS(nums3)); // 输出：6（子序列 [2,4,5,6,7,12]）
    }
}
