package 基础;

public class JVM {
    public static int num = 10; // 静态变量 → 方法区
    public static void main(String[] args) { // main方法 → 方法区
        int a = 1; // 局部变量a → 虚拟机栈的main方法栈帧（局部变量表）
        //User user = new User(); // user引用 → 虚拟机栈；User对象 → 堆
    }

}
