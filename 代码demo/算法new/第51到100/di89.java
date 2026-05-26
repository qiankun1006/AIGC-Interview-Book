package 算法new.第51到100;

import java.util.*;

public class di89 {

    //i=101111  i>>1=010111  i^(i>>1)=111000
    //i=110000  i>>1=011000  i^(i>>1)=101000
    public List<Integer> grayCode(int n) {
        List<Integer> res = new ArrayList<>();
        int store = (1 << n);
        for (int i = 0; i < store; i++) {
            res.add(i ^ (i >> 1));
        }
        return res;
    }

    public static void main(String[] args) {
        int n = 4;
        int store = (1 << n);
        for (int i = 0; i < store; i++) {
            String bi     = String.format("%3s", Integer.toBinaryString(i)).replace(' ', '0');
            String biShift = String.format("%3s", Integer.toBinaryString(i >> 1)).replace(' ', '0');
            String biGray  = String.format("%3s", Integer.toBinaryString(i ^ (i >> 1))).replace(' ', '0');
            System.out.println("i=" + bi + "  i>>1=" + biShift + "  i^(i>>1)=" + biGray);
        }
    }
}
