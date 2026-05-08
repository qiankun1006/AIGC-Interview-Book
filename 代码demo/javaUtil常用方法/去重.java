package javaUtil常用方法;

public class 去重 {

    /**
     * 去除字符串中的重复字符，保持原有字符顺序
     * @param input 输入字符串
     * @return 去重后的字符串
     */
    public static String removeDuplicateChars(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuilder result = new StringBuilder();
        java.util.Set<Character> seen = new java.util.HashSet<>();

        for (char c : input.toCharArray()) {
            if (!seen.contains(c)) {
                seen.add(c);
                result.append(c);
            }
        }

        return result.toString();
    }

    /**
     * 去除字符串中的重复字符（使用Set实现）
     * @param input 输入字符串
     * @return 去重后的字符串
     */
    public static String removeDuplicateCharsWithSet(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuilder result = new StringBuilder();
        java.util.Set<Character> seen = new java.util.LinkedHashSet<>();

        for (char c : input.toCharArray()) {
            seen.add(c);
        }

        for (char c : seen) {
            result.append(c);
        }

        return result.toString();
    }

    // 测试方法
    public static void main(String[] args) {
        String test1 = "故事模式经历精彩的故事情节，体验丰富的角色互动和战术策略。提供多个难度等级，适合不同游戏水平的玩家。联网对战与世界各地的玩家进行实时战斗对比。展示你的战术能力，争夺排行榜的最高位置，赢得荣耀。\n" +
                "AI创造尽情发挥想象力，创建自己的游戏关卡和角色。与其他玩家分享你的创意，体验无限的创意自由。\n" +
                "读取存档读取进度设置退出游戏开始游戏入初新手教程关卡，学习基本操作和战斗技巧\n" +
                "进入关卡选择确认返回主菜单移待机显格物品装备使用丢弃\n" +
                "火焰特效粒子发射持续延迟最大最小速度尺寸缩放颜色透明旋转加速度重力风力生命周期边界消失开始结束创建销毁暂停继续清空显示隐藏状态数量频率管理配置加载初始化渲染更新绘制批次纹理坐标偏移角度重力空气阻力燃烧熄灭上升扩散烟雾粒子特效特效数粒子数切换类型点击创建清空隐藏状态退出当前后当前小火苗篝火爆炸火焰柴捆标枪\n" +
                "攻";

        System.out.println("原字符串长度: " + test1.length());
        System.out.println("原字符串: " + test1.replace("\n", "\\n"));
        System.out.println();

        String result = removeDuplicateChars(test1);
        System.out.println("去重后长度: " + result.length());
        System.out.println("去重后: " + result.replace("\n", "\\n"));
        System.out.println();

        // 简单测试
        String simple = "hello\nworld";
        System.out.println("简单测试 - 原字符串: " + simple.replace("\n", "\\n"));
        System.out.println("简单测试 - 去重后: " + removeDuplicateChars(simple).replace("\n", "\\n"));
    }
}
