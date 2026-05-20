package 算法new.第51到100;


import java.util.ArrayList;
import java.util.List;

public class di68 {

    //输入: words = ["This", "is", "an", "example", "of", "text", "justification."], maxWidth = 16
    // 输出: [ "This is an", "example of text", "justification. " ] 要求尽可能均匀，空格优先放在左边，比如2 2 1
    public List<String> fullJustify(String[] words, int maxWidth) {
        List<String> result = new ArrayList<>();
        int i = 0;
        while (i < words.length) {
            List<String> currentLine = new ArrayList<>();
            int currentLen = 0;
            // 贪心：尽可能多地往当前行塞单词
            while (i < words.length && currentLen + words[i].length() + currentLine.size() <= maxWidth) {
                currentLine.add(words[i]);
                currentLen += words[i].length();
                i++;
            }
            // 最后一行或只有一个单词：左对齐，末尾补空格
            if (i == words.length || currentLine.size() == 1) {
                result.add(leftJustify(currentLine, maxWidth));
            } else {
                // 中间行：均匀分布空格
                result.add(convert(currentLine, currentLen, maxWidth));
            }
        }
        return result;
    }


    // 左对齐：单词间一个空格，末尾补空格
    private String leftJustify(List<String> strList, int maxWidth) {
        String line = "";
        for (int j = 0; j < strList.size(); j++) {
            line = line + strList.get(j);
            if (j < strList.size() - 1) {
                line = line + " ";
            }
        }
        int remaining = maxWidth - line.length();
        for (int k = 0; k < remaining; k++) {
            line = line + " ";
        }
        return line;
    }

    private String convert(List<String> strList, int sumSize, int maxWidth) {
        int subSize = maxWidth - sumSize;
        int gapCount = strList.size() - 1;
        String res = "";
        if (gapCount == 0) {
            //
            res = res + strList.get(0);
            for (int i = 0; i < subSize; i++) {
                res = res + " ";
            }
            return res;
        }
        int remain = subSize % gapCount;
        int avg = subSize / gapCount;

        for (int i = 0; i < gapCount; i++) {
            res = res + strList.get(i);
            for (int j = 0; j < avg; j++) {
                res = res + " ";
            }
            if (remain > 0) {
                res = res + " ";
                remain--;
            }
        }
        res = res + strList.get(strList.size() - 1);
        return res;
    }
}
