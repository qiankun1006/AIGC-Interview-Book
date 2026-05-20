package 算法new.特殊;

public class di81 {

    //todo 不止一次没写出来了
    public boolean search(int[] nums, int target) {
        if (nums.length == 0) return false;
        int l = 0, r = nums.length - 1;
        while (l <= r) {
            // 去重：跳过左边连续重复的元素，防止无法判断哪半边有序
            while (l < r && nums[l] == nums[l + 1]) ++l;
            while (l < r && nums[r] == nums[r - 1]) --r;

            int mid = l + (r - l) / 2;
            System.out.printf("  l=%d(val=%d) mid=%d(val=%d) r=%d(val=%d)%n",
                    l, nums[l], mid, nums[mid], r, nums[r]);

            if (nums[mid] == target) return true;

            if (nums[mid] < nums[r]) {
                // mid~r 是有序的右半段
                System.out.println("  → 右半段有序 [" + nums[mid] + "..." + nums[r] + "]");
                if (target > nums[mid] && target <= nums[r]) {
                    System.out.println("  → target 在右半段，l = mid+1");
                    l = mid + 1;
                } else {
                    System.out.println("  → target 不在右半段，r = mid-1");
                    r = mid - 1;
                }
            } else {
                // l~mid 是有序的左半段
                System.out.println("  → 左半段有序 [" + nums[l] + "..." + nums[mid] + "]");
                if (target < nums[mid] && target >= nums[l]) {
                    System.out.println("  → target 在左半段，r = mid-1");
                    r = mid - 1;
                } else {
                    System.out.println("  → target 不在左半段，l = mid+1");
                    l = mid + 1;
                }
            }
        }
        return false;
    }

    public static void main(String[] args) {
        di81 sol = new di81();

        // 普通旋转数组，target 存在
        // 旋转点在中间，target 在右半段有序区
        check(sol, new int[]{4, 5, 6, 7, 0, 1, 2}, 0, true,  "普通旋转，target在右半段");

        // 普通旋转数组，target 在左半段有序区
        check(sol, new int[]{4, 5, 6, 7, 0, 1, 2}, 5, true,  "普通旋转，target在左半段");

        // 普通旋转数组，target 不存在
        check(sol, new int[]{4, 5, 6, 7, 0, 1, 2}, 3, false, "普通旋转，target不存在");

        // 含重复元素，重复在左边
        // 去重逻辑会跳过左边的 1 1，然后正常二分
        check(sol, new int[]{1, 1, 3, 1}, 3, true,  "含重复，target存在");

        // 含重复元素，target 不存在
        check(sol, new int[]{2, 2, 2, 0, 2, 2}, 0, true,  "大量重复，target存在（旋转点被重复覆盖）");
        check(sol, new int[]{2, 2, 2, 0, 2, 2}, 3, false, "大量重复，target不存在");

        // 没有旋转（正常升序）
        check(sol, new int[]{1, 2, 3, 4, 5}, 3, true,  "无旋转，target存在");
        check(sol, new int[]{1, 2, 3, 4, 5}, 6, false, "无旋转，target不存在");

        // 单个元素
        check(sol, new int[]{1}, 1, true,  "单元素，命中");
        check(sol, new int[]{1}, 0, false, "单元素，未命中");
    }

    private static void check(di81 sol, int[] nums, int target, boolean expected, String desc) {
        System.out.println("\n【" + desc + "】 nums=" + java.util.Arrays.toString(nums) + " target=" + target);
        boolean result = sol.search(nums, target);
        String status = result == expected ? "✓ PASS" : "✗ FAIL";
        System.out.println("  结果=" + result + " 期望=" + expected + "  " + status);
    }


    //todo 33题
    // 输入：nums = [4,5,6,7,0,1,2], target = 0 输出：4
    // 核心：大小对比 不再是nums[mid]和target（当然，如果是等于就直接结束）
    // 确定哪边有序之后去明确有序的一边比较target，如果没有就是另一边。
    // 总结：先确定有序边，然后在有序边搜索。
    public int search33(int[] nums, int target) {
        if (nums.length == 0) return -1;
        int left = 0;
        int right = nums.length - 1;
        while (left < right) {
            int mid = left + (right - left) / 2;
            if (nums[mid] == target) return mid;
            else if (nums[mid] < nums[right]) {  //右边有序
                if (target > nums[mid] && target <= nums[right]) left = mid + 1;
                else right = mid;
            } else {
                //左边有序
                if (target >= nums[left] && target < nums[mid]) right = mid;
                else left = mid + 1;
            }
        }
        if (nums[left] == target) return left;
        return -1;
    }

    //todo 34题
    // 两次二分查找，分开查找第一个和最后一个
    // 时间复杂度 O(log n), 空间复杂度 O(1)
    // [1,2,3,3,3,3,4,5,9]
    public int[] searchRange(int[] nums, int target) {
        int left = 0;
        int right = nums.length;
        int first = -1;
        int last = -1;
        // 找第一个等于target的位置
        while (left < right) {
            int middle = (left + right) / 2;
            if (nums[middle] == target) {
                //todo 命中了也不能停，继续往右边找，每次有新的等于都要记录
                first = middle;
                right = middle; //重点
            } else if (nums[middle] > target) {
                right = middle;
            } else {
                left = middle + 1;
            }
        }

        // 最后一个等于target的位置
        left = 0;
        right = nums.length;
        while (left < right) {
            int middle = (left + right) / 2;
            if (nums[middle] == target) {
                last = middle;
                left = middle + 1; //重点
            } else if (nums[middle] > target) {
                right = middle;
            } else {
                left = middle + 1;
            }
        }

        return new int[]{first, last};
    }
}
