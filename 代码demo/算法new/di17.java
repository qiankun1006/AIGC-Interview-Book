package 算法new;

import java.util.*;

public class di17 {

    public List<String> letterCombinations(String digits) {
        String[] strArray = new String[]{"", "", "abc", "def", "ghi", "jkl", "mno",
                "pqrs", "tuv", "wxyz"};
        List<String> res = new ArrayList<>();
        dfs("", res, digits, 0, strArray);
        return res;
    }

    void dfs(String cur, List<String> res, String digits, int deep, String[] strArray) {
        if (cur.length() == digits.length()) {
            res.add(cur);
            return;
        }
        int index = digits.charAt(deep) - '0';
        for (int i = 0; i < strArray[index].length(); i++) {
            dfs(cur + strArray[index].charAt(i), res, digits, deep + 1, strArray);
        }
    }
}
