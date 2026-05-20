package 算法new.第51到100;

public class di58 {

    public int lengthOfLastWord(String s) {
        int length = s.length() - 1;
        int count = 0;
        while (length >= 0) {
            if (s.charAt(length) == ' ') {
                length--;
                continue;
            }
            while (length >= 0 && s.charAt(length) != ' ') {
                length--;
                count++;
            }
            break;
        }
        return count;
    }
}
