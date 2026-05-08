package 算法new;

public class di38 {

    //todo 贪心算法，得自己写一遍
    public String countAndSay(int n) {
        //初始是1
        String start = "1";
        for (int i = 0; i < n; i++) {
            start = next(start);
        }
        return start;
    }

    private String next(String cur) {
        //1211
        int start = 0;
        String res = "";
        while(start < cur.length()) {
            int end = start;
            while(end < cur.length() - 1 && cur.charAt(end) ==cur.charAt(end+1)) {
                end ++;
            }
            int count = end - start +1;
            res = res + count + start;
            start = end +1;
        }
        return res;
    }

}
