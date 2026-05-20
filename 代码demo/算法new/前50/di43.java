package 算法new.前50;

public class di43 {

    //给定两个以字符串形式表示的非负整数 num1 和 num2，返回 num1 和 num2 的乘积，它们的乘积也表示为字符串形式。
    //注意：不能使用任何内置的 BigInteger 库或直接将输入转换为整数。
    //todo 自己写一遍吧
    // 两个点：1、输入 不能转成Integer来计算。2、计算结果也不能用Integer，因为会溢出。
    // 用一个长数组来存，Len1 + Len2
    public String multiply(String num1, String num2) {
        if (num1.equals("0") || num2.equals("0")) return "0";
        int length1 = num1.length();
        int length2 = num2.length();
        int[] value = new int[length1 + length2];
        for (int i = length1 - 1; i >= 0; i--) {
            for (int j = length2 - 1; j >= 0; j--) {
                int tmp = (num1.charAt(i) - '0') * (num2.charAt(j) - '0');
                value[i + j + 1] += tmp;
            }
        }
        int jinwei = 0;
        for (int i = length1 + length2 - 1; i >= 0; i--) {
            int add = value[i] + jinwei;
            value[i] = add % 10;
            jinwei = add / 10;
        }
        StringBuilder res = new StringBuilder();
        for (int i = 0; i < length1 + length2; i++) {
            //todo 要注意的点就在这，最高位可能没有，比如10 * 10 = 100
            if (i == 0 && value[i] == 0) continue;
            res.append(value[i]);
        }
        return res.toString();
    }
}
