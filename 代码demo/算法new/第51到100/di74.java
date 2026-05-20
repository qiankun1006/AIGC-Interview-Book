package 算法new.第51到100;

public class di74 {

    //todo 当前一维数组就可以了，不要把问题想复杂了
    public boolean searchMatrix(int[][] matrix, int target) {
        int xLength = matrix.length;
        int yLength = matrix[0].length;
        int left = 0;
        int right = xLength * yLength;
        while(left < right) {
            int mid = (left + right) / 2;
            int x = mid / yLength;
            int y = mid % yLength;
            if(matrix[x][y] == target) {
                return true;
            } else if(matrix[x][y] > target) {
                right = mid;
            } else {
                left = mid + 1;
            }
        }
        return false;
    }
}
