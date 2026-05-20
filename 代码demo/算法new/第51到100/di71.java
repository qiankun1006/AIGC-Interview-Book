package 算法new.第51到100;

import java.util.*;

public class di71 {

    // v2: split + Deque，可读性更好，无需额外数组还原顺序
    // 时间 O(n)，空间 O(n)，和原版一致，但常数更小
    //todo "/.../a/../b//c/../d/./"
    public String simplifyPath(String path) {
        // 重写一遍
        Deque<String> st = new LinkedList<>();
        String[] pathSplits = path.split("/");
        for (String part : pathSplits) {
            if (part.equals("..")) {
                if(!st.isEmpty()) {
                    st.pollLast();
                }
            } else if (!part.equals(".") && !part.equals("")) {
                st.addLast(part);
            }
        }
        StringBuilder res = new StringBuilder();
        for (String part : st) {
            res.append("/").append(part);
        }
        return res.length() == 0 ? "/" : res.toString();
    }

    public static void main(String[] args) {
        di71 di71 = new di71();
        di71.simplifyPath("/.../a/../b//c/../d/./");
    }

}
