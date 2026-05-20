package 算法new.前50;

public class di50 {

    public double myPow(double x, int n) {
        // 1. 处理基准情况
        if (n == 0) {
            return 1.0;
        }

        // 2. 极其重要的边界处理！
        // 当 n = Integer.MIN_VALUE 时，-n 会溢出变成 MIN_VALUE 本身。
        // 所以我们先把 n 转为 long 类型，或者先除以 2 再处理。
        long N = n;

        // 3. 如果是负数次幂，转为正数处理，最后取倒数
        if (N < 0) {
            x = 1 / x;
            N = -N;
        }

        // 4. 调用快速幂函数
        return fastPow(x, N);
    }

    private double fastPow(double x, long n) {
        // 递归终止条件
        if (n == 1) {
            return x;
        }

        // 1. 折半计算
        double half = fastPow(x, n / 2);

        // 2. 判断奇偶，合并结果
        if (n % 2 == 0) {
            // 偶数：直接平方
            return half * half;
        } else {
            // 奇数：多乘一个 x
            return half * half * x;
        }
    }
}
