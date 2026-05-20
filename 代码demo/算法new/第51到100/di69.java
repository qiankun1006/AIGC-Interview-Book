package 算法new.第51到100;

public class di69 {

    public int mySqrt(int x) {
        if(x == 0 || x==1) {
            return x;
        }
        int left  = 0;
        int right = x;
        while(left < right) {
            int mid = (left + right) / 2;
            if(x / mid == mid) {
                return mid;
            } else if(x / mid > mid) {
                left = mid + 1;
            } else {
                right = mid;
            }
        }
        //todo 这个二分查找实际上在找第一个平方大于 x 的整数，
        // 即最小的 k 使得 k² > x。找到后，答案就是 k-1。
        return left -1;
    }

    // v2: 用 long 乘法代替除法，逻辑更直观
    public int mySqrtV2(int x) {
        if (x == 0 || x == 1) {
            return x;
        }
        long left = 1;
        long right = x;
        while (left < right) {
            long mid = (left + right) / 2;
            long sq = mid * mid;
            if (sq == x) {
                return (int) mid;
            } else if (sq < x) {
                left = mid + 1;
            } else {
                right = mid;
            }
        }
        return (int) left - 1;
    }
}
