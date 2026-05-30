package 算法new.第101到150;

public class di134 {

    //在一条环路上有 n 个加油站，其中第 i 个加油站有汽油 gas[i] 升。
    //
    //你有一辆油箱容量无限的的汽车，从第 i 个加油站开往第 i+1 个加油站需要消耗汽油 cost[i] 升。你从其中的一个加油站出发，开始时油箱为空。
    //
    //给定两个整数数组 gas 和 cost ，如果你可以按顺序绕环路行驶一周，则返回出发时加油站的编号，否则返回 -1 。如果存在解，则 保证 它是 唯一 的。
    //
    //示例 1:
    //
    //输入: gas = [1,2,3,4,5], cost = [3,4,5,1,2]
    //输出: 3
    //解释:
    //从 3 号加油站(索引为 3 处)出发，可获得 4 升汽油。此时油箱有 = 0 + 4 = 4 升汽油
    //开往 4 号加油站，此时油箱有 4 - 1 + 5 = 8 升汽油
    //开往 0 号加油站，此时油箱有 8 - 2 + 1 = 7 升汽油
    //开往 1 号加油站，此时油箱有 7 - 3 + 2 = 6 升汽油
    //开往 2 号加油站，此时油箱有 6 - 4 + 3 = 5 升汽油
    //开往 3 号加油站，你需要消耗 5 升汽油，正好足够你返回到 3 号加油站。
    //因此，3 可为起始索引。
    //示例 2:
    //
    //输入: gas = [2,3,4], cost = [3,4,3]
    //输出: -1
    //解释:
    //你不能从 0 号或 1 号加油站出发，因为没有足够的汽油可以让你行驶到下一个加油站。
    //我们从 2 号加油站出发，可以获得 4 升汽油。 此时油箱有 = 0 + 4 = 4 升汽油
    //开往 0 号加油站，此时油箱有 4 - 3 + 2 = 3 升汽油
    //开往 1 号加油站，此时油箱有 3 - 3 + 3 = 3 升汽油
    //你无法返回 2 号加油站，因为返程需要消耗 4 升汽油，但是你的油箱只有 3 升汽油。
    //因此，无论怎样，你都不可能绕环路行驶一周。
    // 贪心：O(n) 时间，O(1) 空间
    // 核心：如果从 start 出发，走到 i 时油箱变负，
    //       则 start..i 之间任何一站都不可能是起点（从中间出发初始油更少），
    //       直接把候选起点挪到 i+1
    public int canCompleteCircuit(int[] gas, int[] cost) {
        int totalGas = 0;  // 全程油量总差值，判断解是否存在
        int curGas = 0;    // 从当前起点出发到当前站的油量
        int start = 0;     // 候选起点

        for (int i = 0; i < gas.length; i++) {
            int diff = gas[i] - cost[i];
            totalGas += diff;
            curGas += diff;
            System.out.printf("站%d: gas=%d cost=%d diff=%d | curGas=%d totalGas=%d start=%d%n",
                    i, gas[i], cost[i], diff, curGas, totalGas, start);
            // 从 start 出发走到 i 时油箱见底，start 到 i 都不能做起点
            if (curGas < 0) {
                System.out.printf("  → 油箱不足！起点从%d移到%d%n", start, i + 1);
                start = i + 1;  // 候选起点移到下一站
                curGas = 0;     // 重新从新起点开始累计
            }
        }

        // 总油量不足，无解；否则贪心选出的 start 就是唯一答案
        return totalGas < 0 ? -1 : start;
    }

    public static void main(String[] args) {
        di134 solution = new di134();

        // 复杂示例1（有解，diff 正负相间）：8个站
        // gas  = [3, 5, 1, 6, 2, 4, 1, 7]
        // cost = [5, 2, 4, 3, 5, 1, 4, 3]
        // diff = [-2, 3,-3, 3,-3, 3,-3, 4]  总和=2>0 有解，答案=5
        // 特点：正负交替，curGas 多次被拉低，起点最终稳定在5
        System.out.println("===== 复杂示例1（有解，diff正负相间，期望起点=5）=====");
        int[] gas1  = {3, 5, 1, 6, 2, 4, 1, 7};
        int[] cost1 = {5, 2, 4, 3, 5, 1, 4, 3};
        int result1 = solution.canCompleteCircuit(gas1, cost1);
        System.out.println("起点站索引: " + result1);  // 5

        System.out.println();

        // 复杂示例2（无解，diff 正负相间）：8个站
        // gas  = [4, 2, 5, 1, 6, 1, 4, 2]
        // cost = [3, 5, 2, 6, 2, 5, 2, 5]
        // diff = [ 1,-3, 3,-5, 4,-4, 2,-3]  总和=-5<0 无解
        // 特点：正负交替，看似有希望但总量不足
        System.out.println("===== 复杂示例2（无解，diff正负相间，期望=-1）=====");
        int[] gas2  = {4, 2, 5, 1, 6, 1, 4, 2};
        int[] cost2 = {3, 5, 2, 6, 2, 5, 2, 5};
        int result2 = solution.canCompleteCircuit(gas2, cost2);
        System.out.println("起点站索引: " + result2);  // -1
    }
}
