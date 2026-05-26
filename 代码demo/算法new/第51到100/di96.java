package 算法new.第51到100;

public class di96 {

    public int numTrees(int n) {
        return rescur(1, n);
    }

    int rescur(int min, int max) {
        if (min >= max) {
            return 1;
        }
        int res = 0;
        for (int i = min; i <= max; i++) {
            int left = rescur(min, i - 1);
            int right = rescur(i + 1, max);
            res += left * right;
        }
        return res;
    }
}
