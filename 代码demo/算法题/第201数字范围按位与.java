package 算法题;

public class 第201数字范围按位与 {

    public int rangeBitwiseAnd(int left, int right) {
        int compare = 1 << 30;
        int ans = 0;
        while (compare > 0) {
            if ((compare & left) == (compare & right)) {
                ans += compare & left;
            } else {
                break;
            }
            compare >>= 1;
        }
        return ans;
    }
}
