package 算法new.第51到100;

import java.util.ArrayList;
import java.util.List;

public class di57 {

    public int[][] insert(int[][] intervals, int[] newInterval) {
        List<int[]> result = new ArrayList<>();
        int n = intervals.length;
        int i = 0;

        // 1. 遍历所有区间，将与新区间无重叠的部分（即在新区间左侧的部分）直接加入结果
        // 条件：当前区间的结束点 < 新区间的起始点
        while (i < n && intervals[i][1] < newInterval[0]) {
            result.add(intervals[i]);
            i++;
        }

        // 2. 处理重叠部分（核心步骤）
        // 此时 intervals[i][0] >= newInterval[0]
        // 我们需要不断更新新区间的边界，直到没有重叠
        while (i < n && intervals[i][0] <= newInterval[1]) {
            // 合并区间：新的起始点是两者起点的最小值
            newInterval[0] = Math.min(newInterval[0], intervals[i][0]);
            // 新的结束点是两者终点的最大值
            newInterval[1] = Math.max(newInterval[1], intervals[i][1]);
            i++;
        }
        // 将合并好的最终区间加入结果
        result.add(newInterval);

        // 3. 将剩余无重叠的部分（即在新区间右侧的部分）加入结果
        while (i < n) {
            result.add(intervals[i]);
            i++;
        }

        // 将 List 转换为二维数组返回
        return result.toArray(new int[result.size()][]);
    }

    static String fmt(int[][] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int[] a : arr) sb.append("[").append(a[0]).append(",").append(a[1]).append("]");
        sb.append("]");
        return sb.toString();
    }

    public static void main(String[] args) {
        di57 sol = new di57();

        // 情况1：新区间插在中间，与多个区间重叠
        // intervals=[[1,3],[6,9]], newInterval=[2,5]
        // 期望：[[1,5],[6,9]]
        int[][] r1 = sol.insert(new int[][]{{1,3},{6,9}}, new int[]{2,5});
        System.out.println("情况1（中间插入，部分重叠）: " + fmt(r1));

        // 情况2：新区间跨越多个已有区间，全部合并
        // intervals=[[1,2],[3,5],[6,7],[8,10],[12,16]], newInterval=[4,8]
        // 期望：[[1,2],[3,10],[12,16]]
        int[][] r2 = sol.insert(new int[][]{{1,2},{3,5},{6,7},{8,10},{12,16}}, new int[]{4,8});
        System.out.println("情况2（跨越多个区间合并）: " + fmt(r2));

        // 情况3：新区间在所有区间左侧，无重叠
        // intervals=[[3,5],[6,9]], newInterval=[1,2]
        // 期望：[[1,2],[3,5],[6,9]]
        int[][] r3 = sol.insert(new int[][]{{3,5},{6,9}}, new int[]{1,2});
        System.out.println("情况3（插到最左边，无重叠）: " + fmt(r3));

        // 情况4：新区间在所有区间右侧，无重叠
        // intervals=[[1,3],[4,6]], newInterval=[8,10]
        // 期望：[[1,3],[4,6],[8,10]]
        int[][] r4 = sol.insert(new int[][]{{1,3},{4,6}}, new int[]{8,10});
        System.out.println("情况4（插到最右边，无重叠）: " + fmt(r4));

        // 情况5：原区间为空
        // intervals=[], newInterval=[5,7]
        // 期望：[[5,7]]
        int[][] r5 = sol.insert(new int[][]{}, new int[]{5,7});
        System.out.println("情况5（原数组为空）: " + fmt(r5));

        // 情况6：新区间完全覆盖所有已有区间
        // intervals=[[1,3],[4,6],[7,9]], newInterval=[0,10]
        // 期望：[[0,10]]
        int[][] r6 = sol.insert(new int[][]{{1,3},{4,6},{7,9}}, new int[]{0,10});
        System.out.println("情况6（新区间完全覆盖所有）: " + fmt(r6));
    }
}
