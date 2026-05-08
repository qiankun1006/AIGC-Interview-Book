package 算法new;

public class di36 {
    //请你判断一个 9 x 9 的数独是否有效。只需要 根据以下规则 ，验证已经填入的数字是否有效即可。
    //
    //数字 1-9 在每一行只能出现一次。
    //数字 1-9 在每一列只能出现一次。
    //数字 1-9 在每一个以粗实线分隔的 3x3 宫内只能出现一次。（请参考示例图）
    //todo 就是哈希记录，空间换时间
    // i / 3 * 3 + j / 3 和 board[i][j] - '1'
    public boolean isValidSudoku(char[][] board) {
        boolean[][] row = new boolean[9][9];
        boolean[][] col = new boolean[9][9];
        boolean[][] area = new boolean[9][9];
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                if (board[i][j] != '.') {  //存在有效的数
                    if (row[i][board[i][j] - '1'] == true) return false;
                    else row[i][board[i][j] - '1'] = true;
                    if (col[board[i][j] - '1'][j] == true) return false;
                    else col[board[i][j] - '1'][j] = true;
                    if (area[i / 3 * 3 + j / 3][board[i][j] - '1'] == true) return false;
                    else area[i / 3 * 3 + j / 3][board[i][j] - '1'] = true;
                }
            }
        }
        return true;
    }
}
