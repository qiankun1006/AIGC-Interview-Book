package 算法new.前50;

public class di42 {

    //接雨水：记录两边的最大就行了
    public int trap(int[] height) {
        if(height == null) {
            return 0;
        }
        int leftMax = 0;
        int rightMax = 0;
        int res = 0;
        int[] rightMaxArr = new int[height.length];
        for(int i=height.length -1; i> 0;i--) {
            rightMax = Math.max(rightMax, height[i]);
            rightMaxArr[i] = rightMax;
        }
        for(int i=0;i<height.length ;i++) {
            leftMax = Math.max(leftMax, height[i]);
            if(height[i] < leftMax && height[i] < rightMaxArr[i]) {
                res = res + Math.min(leftMax, rightMaxArr[i]) - height[i];
            }
        }
        return res;
    }
}
