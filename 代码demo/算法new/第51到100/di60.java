package 算法new.第51到100;

import java.util.*;

public class di60 {

    public String getPermutation(int n, int k) {
        //先求(n-1)!
        int div = 1;
        for (int i = 1; i <= n; i++)
            div *= i;
        List<Integer> list = new ArrayList<>();
        for (int i = 1; i <= n; i++)
            list.add(i);
        StringBuilder str = new StringBuilder();
        for (int i = n; i >= 1; i--) {
            div /= i;
            int index = (k - 1) / div;
            k -= index * div;
            str.append(list.get(index));
            list.remove(index);
        }
        return str.toString();
    }
}
