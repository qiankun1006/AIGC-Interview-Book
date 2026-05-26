package 算法new.第51到100;

import java.util.*;

public class di93 {

    //todo 这题看着不难，写起来很多细节点，自己再写一遍
    public List<String> restoreIpAddresses(String s) {
        List<String> res = new ArrayList<>();
        dfs(s, 0, 0, "", res);
        return res;
    }

    public void dfs(String s, int count, int deep, String sb, List<String> res) {
        if (deep == s.length()) {
            res.add(sb.substring(1, sb.length()));
            return;
        }
        int end = deep + 3;
        if (s.charAt(deep) == '0') {
            end = deep + 1;
        }
        for (int i = deep; i < Math.min(end, s.length()); i++) {
            int num = Integer.valueOf(s.substring(deep, i + 1));
            if (num > 255 || (s.length() - 1 - i > (4 - count - 1) * 3) || (s.length() - 1 - i) < (4 - count - 1)) {
                continue;
            }
            dfs(s, count + 1, i + 1, sb + "." + num, res);
        }
    }
}
