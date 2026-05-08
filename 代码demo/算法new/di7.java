package 算法new;

public class di7 {

    //示例 1：
    //输入：x = 123
    //输出：321

    //示例 2：
    //输入：x = -123
    //输出：-321

    //示例 3：
    //输入：x = 120
    //输出：21

    //示例 4：
    //输入：x = 0
    //输出：0
    public int reverse(int x) {
        int res = 0;
        while(x != 0) {
            int cur = x % 10;
            x = x / 10;
            //如果是负数
            if(x < 0) {

            } else {
                //如果是正数
                int com = (Integer.MAX_VALUE - cur) / 10;
                int remain = (Integer.MAX_VALUE - cur) % 10;
                if(res > com || (res == com && remain > 0)) {
                    //溢出了
                    return 0;
                } else {
                    res = res * 10 + cur;
                }
            }
        }
        return res;
    }
}
