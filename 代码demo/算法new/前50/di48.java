package 算法new.前50;

public class di48 {

    public void rotate(int[][] matrix) {
        int len = matrix.length;
        for (int i = 0; i < len / 2; i++) {
            int start = i;
            int end = len - 1 - i;
            for (int j = 0; j < end - start; j++) {
                int r1 = start,     c1 = start + j;   // top-left
                int r2 = start + j, c2 = end;          // top-right
                int r3 = end,       c3 = end - j;      // bottom-right
                int r4 = end - j,   c4 = start;        // bottom-left

                int temp = matrix[r1][c1];
                System.out.printf("  圈%d 偏移%d: [%d,%d]=%d → [%d,%d]=%d → [%d,%d]=%d → [%d,%d]=%d → [%d,%d]=%d%n",
                        i, j,
                        r1, c1, matrix[r1][c1],
                        r4, c4, matrix[r4][c4],
                        r3, c3, matrix[r3][c3],
                        r2, c2, matrix[r2][c2],
                        r1, c1, matrix[r1][c1]);

                // 四个角位置的循环交换（顺时针90°）
                // top-left ← bottom-left ← bottom-right ← top-right ← top-left
                matrix[r1][c1] = matrix[r4][c4];
                matrix[r4][c4] = matrix[r3][c3];
                matrix[r3][c3] = matrix[r2][c2];
                matrix[r2][c2] = temp;
            }
        }
    }

    static void print(int[][] m) {
        for (int[] row : m) {
            StringBuilder sb = new StringBuilder();
            for (int v : row) sb.append(String.format("%3d", v));
            System.out.println(sb.toString());
        }
    }

    public static void main(String[] args) {
        di48 sol = new di48();

        // 情况1：3×3 矩阵
        // 旋转前:       旋转后(期望):
        // 1 2 3         7 4 1
        // 4 5 6    →    8 5 2
        // 7 8 9         9 6 3
        int[][] m1 = {
            { 1,  2,  3,  4,  5},
            { 6,  7,  8,  9, 10},
            {11, 12, 13, 14, 15},
            {16, 17, 18, 19, 20},
            {21, 22, 23, 24, 25}
        };
        System.out.println("情况1 旋转前（5×5）:");
        print(m1);
        sol.rotate(m1);
        System.out.println("情况1 旋转后:");
        print(m1);
        System.out.println();

        // 情况2：4×4 矩阵
        // 旋转前:             旋转后(期望):
        //  5  1  9 11         15 13  2  5
        //  2  4  8 10    →    14  3  4  1
        // 13  3  6  7          7  8  6  9
        // 15 14 12 16         12 11  9 16  (注意 16 固定不动？不对，16也动)
        // 期望: [[15,13,2,5],[14,3,4,1],[12,7,8,10],[16,12,9,11]] -- 以LeetCode为准
        int[][] m2 = {{5,1,9,11},{2,4,8,10},{13,3,6,7},{15,14,12,16}};
        System.out.println("情况2 旋转前（4×4）:");
        print(m2);
        sol.rotate(m2);
        System.out.println("情况2 旋转后:");
        print(m2);
        System.out.println();

        // 情况3：1×1 矩阵（边界情况，旋转后不变）
        int[][] m3 = {{42}};
        System.out.println("情况3 旋转前（1×1）:");
        print(m3);
        sol.rotate(m3);
        System.out.println("情况3 旋转后:");
        print(m3);
    }
}
