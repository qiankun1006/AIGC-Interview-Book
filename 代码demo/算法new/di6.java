package 算法new;

public class di6 {

    public String convert(String s, int numRows) {
        String str = "";
        for (int i = 0; i < numRows; i++) {
            //按顺序遍历，判断当前字母是否符合是在这一行
            //判断条件：
            //1、当前下标 j 对 2 * numRows - 2取余，是不是等于i
            //2、当前下标 j 对 2 * numRows - 1取余，是不是 2 * numRows -1 - 取余 = i
            for (int j = 0; j < s.length(); j++) {
                int remain1 = j % (2 * numRows - 2);
                int remain2 = j % (2 * numRows -1);
                if(remain1 == i || 2 * numRows -1 - remain2 == i) {
                    str = str + s.charAt(j);
                }
            }
        }
        return str;
    }
}
