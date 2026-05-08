package 算法题;

public class 第275题H指数2 {

    public int hIndex(int[] citations) {
        if (citations.length == 0) {
            return 0;
        }
        int left = 0;
        int right = citations.length;
        while(left < right) {
            int mid = left + (right - left) / 2;
            //刚好等于
            if(citations[mid]  == citations.length - mid) {
                return citations.length - mid;
            } else if(citations[mid] > citations.length - mid) {
                right = mid;
            } else {
                left = mid + 1;
            }
        }
        return citations.length - left;
    }
}
