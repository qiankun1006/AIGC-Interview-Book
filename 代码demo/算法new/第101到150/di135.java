package 算法new.第101到150;

public class di135 {

    //n 个孩子站成一排。给你一个整数数组 ratings 表示每个孩子的评分。
    //你需要按照以下要求，给这些孩子分发糖果：
    //每个孩子至少分配到 1 个糖果。
    //相邻两个孩子中，评分更高的那个会获得更多的糖果。
    //请你给每个孩子分发糖果，计算并返回需要准备的 最少糖果数目 。
    //
    //示例 1：
    //
    //输入：ratings = [1,0,2]
    //输出：5
    //解释：你可以分别给第一个、第二个、第三个孩子分发 2、1、2 颗糖果。
    //示例 2：
    //
    //输入：ratings = [1,2,2]
    //输出：4
    //解释：你可以分别给第一个、第二个、第三个孩子分发 1、2、1 颗糖果。
    //     第三个孩子只得到 1 颗糖果，这满足题面中的两个条件。
    // 贪心：两次遍历，O(n) 时间，O(n) 空间
    // 第一次从左到右：右边比左边评分高，则右边糖果 = 左边 + 1
    // 第二次从右到左：左边比右边评分高，则左边糖果 = max(当前, 右边 + 1)
    // 两次遍历各自保证了"左右关系"，取 max 同时满足两个方向
    public int candy(int[] ratings) {
        int n = ratings.length;
        int[] candies = new int[n];

        // 初始每人1颗
        java.util.Arrays.fill(candies, 1);

        //todo 两次遍历，我以前也是这样想的，这题反而不算困难

        // 第一次：从左到右，保证右边比左边高分时糖更多
        for (int i = 1; i < n; i++) {
            if (ratings[i] > ratings[i - 1]) {
                candies[i] = candies[i - 1] + 1;
            }
        }

        // 第二次：从右到左，保证左边比右边高分时糖更多，同时顺便累加求和
        int total = candies[n - 1];  // 最右一个单独初始化（循环从 n-2 开始）
        for (int i = n - 2; i >= 0; i--) {
            if (ratings[i] > ratings[i + 1]) {
                candies[i] = Math.max(candies[i], candies[i + 1] + 1);
            }
            total += candies[i];  // 更新完当前值后立即累加，省掉第三次遍历
        }
        return total;
    }

    // 一次遍历坡度法：O(n) 时间，O(1) 空间
    // 把序列拆成若干段"上坡（inc）+ 下坡（dec）"，用等差数列求和公式直接计算糖果数
    // 上坡长度 inc：贡献 1+2+...+inc = inc*(inc+1)/2
    // 下坡长度 dec：贡献 1+2+...+dec = dec*(dec+1)/2
    // 坡顶被上坡和下坡共享，取较大值，所以减去 min(inc,dec) 避免重复计算
    //todo 能大概理解，但是不想花时间了
    public int candyV2(int[] ratings) {
        int n = ratings.length;
        if (n == 1) return 1;

        int total = 1;  // 第一个孩子至少1颗
        int inc = 1;    // 当前上坡长度（含坡顶，初始为1）
        int dec = 0;    // 当前下坡长度

        for (int i = 1; i < n; i++) {
            if (ratings[i] > ratings[i - 1]) {
                // 上坡：结束上一段下坡，开始新的上坡
                dec = 0;
                inc++;
                total += inc;
            } else if (ratings[i] == ratings[i - 1]) {
                // 平坡：重置，新起点只需1颗
                inc = 1;
                dec = 0;
                total += 1;
            } else {
                // 下坡：dec 累加，坡顶若不够高则补差值
                dec++;
                // 若下坡已经和上坡一样长，说明坡顶的糖不够分配给下坡，需要+1
                if (inc == dec) total++;
                total += dec;  // 下坡新增孩子的糖 = 当前下坡长度（等差数列末项）
            }
        }
        return total;
    }

    public static void main(String[] args) {
        di135 solution = new di135();

        // 示例1：ratings = [1,0,2]  期望输出 5  -> [2,1,2]
        int[] r1 = {1, 0, 2};
        System.out.println("示例1: " + solution.candy(r1));  // 5

        // 示例2：ratings = [1,2,2]  期望输出 4  -> [1,2,1]
        int[] r2 = {1, 2, 2};
        System.out.println("示例2: " + solution.candy(r2));  // 4

        // 复杂示例：ratings = [1,3,2,2,1]  期望输出 7  -> [1,2,1,2,1]
        int[] r3 = {1, 3, 2, 2, 1};
        System.out.println("复杂示例: " + solution.candy(r3));  // 7

        System.out.println("--- candyV2（一次遍历 O(1)空间）---");
        System.out.println("示例1: " + solution.candyV2(r1));      // 5
        System.out.println("示例2: " + solution.candyV2(r2));      // 4
        System.out.println("复杂示例: " + solution.candyV2(r3));   // 7
    }
}
