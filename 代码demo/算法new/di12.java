package 算法new;

public class di12 {
    public String intToRoman(int num) {
        int[] numArray=new int[]{1000,900,500,400,100,90,50,40,10,9,5,4,1};
        String[] charArray=new String[]{"M","CM","D","CD","C","XC","L","XL","X","IX","V","IV","I"};
        String res = "";
        int start = 0;
        while(start < numArray.length) {
            while(num >= numArray[start]) {
                num = num - numArray[start];
                res = res + charArray[start];
            }
            start ++;
        }
        return res;
    }
}
