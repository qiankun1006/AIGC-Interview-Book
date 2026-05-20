package 算法new.前50;

import java.util.Stack;

public class di20 {

    public boolean isValid(String s) {
        //1、一个右括号来的时候，栈顶必须是相同的左括号。
        //2、相同左右符合要消除。
        //3、结束之后栈要为空
        Stack<Character> charStack = new Stack<>();
        for (int i = 0; i < s.length(); i++) {
            if(s.charAt(i) == '(' || s.charAt(i) == '[' || s.charAt(i) == '{') {
                charStack.push(s.charAt(i));
            } else if(s.charAt(i) == ')') {
                if(charStack.pop() != '(') {
                    return false;
                }
            } else if(s.charAt(i) == ']') {
                if(charStack.pop() != '[') {
                    return false;
                }
            } else if(s.charAt(i) == '}') {
                if(charStack.pop() != '{') {
                    return false;
                }
            } else {
                return false;
            }
        }
        return charStack.isEmpty();
    }
}
