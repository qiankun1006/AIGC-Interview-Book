package 算法new.第101到150;

public class di137 {

    //给你一个整数数组 nums ，除某个元素仅出现 一次 外，其余每个元素都恰出现 三次 。请你找出并返回那个只出现了一次的元素。
    //你必须设计并实现线性时间复杂度的算法且使用常数级空间来解决此问题。
    //示例 1：
    //输入：nums = [2,2,3,2]
    //输出：3

    //示例 2：
    //输入：nums = [0,1,0,1,0,1,99]
    //输出：99
    //todo 还是位运算
    public int singleNumber(int[] nums) {
        int count = 0;
        int res = 0;
        for (int i = 0; i < 32; i++) {
            count = 0;
            int compare = 1 << i;
            for (int j = 0; j < nums.length; j++) {
                if ((nums[j] & compare) != 0) {
                    count++;
                }
            }
            if (count % 3 != 0) {
                res += compare;
            }
        }
        return res;
    }
}
