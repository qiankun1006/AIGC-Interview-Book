package 算法new;

public class di29 {

    //1、Integer.MIN_VALUE / -1 会溢出
    //2、如果divisor就是1或-1，直接返回结果
    //3、如果dividend和divisor符合不一样，那结果一定是负数，记录负号，然后统一转成负数（因为肯定要转成相同符号的数才能用加法，负数多一个，不会有溢出问题）
    //4、转成相同号之后，dividend > divisor，则直接返回
    public int divide(int dividend, int divisor) {
        if(dividend == Integer.MIN_VALUE && divisor == -1) {
            return 0;
        }
        if(divisor == 1) return dividend;
        if(divisor == -1) return -dividend;
        boolean negaFlag = false;
        if(divisor > 0 && dividend < 0) {
            negaFlag = true;
            divisor = -divisor;
        } else if(divisor < 0 && dividend > 0) {
            negaFlag = true;
            dividend = -dividend;
        } else if(divisor > 0 && dividend > 0){
            dividend = -dividend;
            divisor = -divisor;
        }
        return div(dividend, divisor);
    }

    private int div(int dividend, int divisor) {
        if(dividend > divisor) {
            return 0;
        }
        int count = 1;
        int sum = divisor;
        //sum + sum > 0 溢出了就别加了
        while(sum + sum > dividend && sum + sum > 0) {
            sum = sum + sum;
            count = count + count;
        }
        return count + div(dividend - sum, divisor);
    }

}
