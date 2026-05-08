package 算法题;

public class 第260题只出现一次的数字3 {

    public int[] singleNumber(int[] nums) {
        int a = 0;
        for (int i = 0; i < nums.length; i++) {
            a = a ^ nums[i];
        }
        int compare = 1;
        //找到最高位
        while ((compare & a) == 0) {
            compare = compare << 1;
        }
        int num1 = 0;
        int num2 = 0;
        for (int i = 0; i < nums.length; i++) {
            if ((compare & nums[i]) != 0) {
                num1 = num1 ^ nums[i];
            } else {
                num2 = num2 ^ nums[i];
            }
        }
        return new int[]{num1, num2};
    }
}
