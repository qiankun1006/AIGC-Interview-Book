package 算法new;

public class di11 {

    public int maxArea(int[] height) {
        //从外向内，内部比最小的边大，才有意义。所以每次移动最小边
        if(height.length == 0) {
            return 0;
        }
        int left = 0;
        int right = height.length - 1;
        int res = 0;
        while(left < right) {
            int area = (right - left) * Math.min(height[left], height[right]);
            res = Math.max(area, res);
            if(height[left] < height[right]) {
                left ++;
            } else {
                right --;
            }
        }
        return res;
    }
}
