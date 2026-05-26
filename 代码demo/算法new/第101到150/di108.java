package 算法new.第101到150;

public class di108 {

    public TreeNode sortedArrayToBST(int[] nums) {
        if(nums == null) {
            return null;
        }
        return sortedArray(nums, 0, nums.length -1);
    }

    private TreeNode sortedArray(int[] nums, int left, int right) {
        if(left == right) {
            return new TreeNode(nums[left]);
        }
        TreeNode cur = new TreeNode(nums[(left + right)/2]);
        TreeNode leftT = null;
        if((left + right)/2 > left) {
            leftT = sortedArray(nums, left, (left + right)/2 -1);
        }
        TreeNode rightT = sortedArray(nums, (left + right)/2 +1, right);
        cur.left = leftT;
        cur.right = rightT;
        return cur;
    }
}
