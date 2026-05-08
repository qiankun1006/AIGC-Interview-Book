package 算法题;


import java.util.*;

public class di399 {
    public static void main(String[] args) {
        // 构造 equations: [["a","b"],["b","c"]]
        List<List<String>> equations = new ArrayList<>();
        equations.add(Arrays.asList("a", "b"));
        equations.add(Arrays.asList("a", "c"));

        // 构造 values: [2.0, 3.0]
        double[] values = {2.0, 3.0};

        // 构造 queries: [["a","c"],["b","a"],["a","e"],["a","a"],["x","x"]]
        List<List<String>> queries = new ArrayList<>();
        queries.add(Arrays.asList("b", "c"));
        queries.add(Arrays.asList("b", "a"));
        queries.add(Arrays.asList("a", "e"));
        queries.add(Arrays.asList("a", "a"));
        queries.add(Arrays.asList("x", "x"));

        Solution solution = new Solution();
        double[] result = solution.calcEquation(equations, values, queries);

        // 打印结果: 预期 [6.00000, 0.50000, -1.00000, 1.00000, -1.00000]
        System.out.println(Arrays.toString(result));
    }
}

class Solution {
    public double[] calcEquation(List<List<String>> equations, double[] values, List<List<String>> queries) {
        // 1. 使用Map来记录每个变量对应的id，方便并查集操作
        Map<String, Integer> idMap = new HashMap<>();
        int id = 0;

        // 首先给所有变量分配id
        for (List<String> equation : equations) {
            String a = equation.get(0);
            String b = equation.get(1);
            if (!idMap.containsKey(a)) {
                idMap.put(a, id++);
            }
            if (!idMap.containsKey(b)) {
                idMap.put(b, id++);
            }
        }

        int n = idMap.size(); // 变量的总数
        UnionFind uf = new UnionFind(n);

        // 2. 处理所有等式，构建并查集
        for (int i = 0; i < equations.size(); i++) {
            String a = equations.get(i).get(0);
            String b = equations.get(i).get(1);
            int idA = idMap.get(a);
            int idB = idMap.get(b);
            uf.union(idA, idB, values[i]);
        }

        // 3. 处理查询
        double[] result = new double[queries.size()];
        for (int i = 0; i < queries.size(); i++) {
            String c = queries.get(i).get(0);
            String d = queries.get(i).get(1);

            // 如果变量不在已知变量集中，直接返回-1.0
            if (!idMap.containsKey(c) || !idMap.containsKey(d)) {
                result[i] = -1.0;
                continue;
            }

            int idC = idMap.get(c);
            int idD = idMap.get(d);
            result[i] = uf.calc(idC, idD);
        }

        return result;
    }

    // 带权并查集类
    class UnionFind {
        int[] parent;  // 父节点
        double[] weight;  // weight[x] = x / parent[x]，即x除以父节点的值

        public UnionFind(int n) {
            parent = new int[n];
            weight = new double[n];
            for (int i = 0; i < n; i++) {
                parent[i] = i;  // 初始时，每个节点的父节点是自己
                weight[i] = 1.0;  // 自己除以自己为1.0
            }
        }

        /**
         * 查找节点x的根节点，并进行路径压缩
         * 在查找过程中，同时更新weight[x]为x除以根节点的值
         */
        public int find(int x) {
            if (x != parent[x]) {
                // 递归找到根节点
                int root = find(parent[x]);
                // 关键步骤：更新weight[x]
                // weight[x]原本是x/parent[x]，现在需要更新为x/root
                // 而x/root = (x/parent[x]) * (parent[x]/root)
                weight[x] *= weight[parent[x]];
                // 路径压缩
                parent[x] = root;
            }
            return parent[x];
        }

        /**
         * 合并两个节点
         * @param x 分子
         * @param y 分母
         * @param value x/y的值
         */
        public void union(int x, int y, double value) {
            int rootX = find(x);
            int rootY = find(y);

            // 如果已经在同一个集合，不需要合并
            if (rootX == rootY) {
                return;
            }

            // 将rootX的父节点设为rootY
            parent[rootX] = rootY;
            // 关键：更新weight[rootX] = rootX/rootY
            // 已知：x/y = value
            // 且：x/rootX = weight[x], y/rootY = weight[y]
            // 所以：rootX/rootY = (rootX/x) * (x/y) * (y/rootY) = (1/weight[x]) * value * weight[y]
            weight[rootX] = (1.0 / weight[x]) * value * weight[y];
        }

        /**
         * 计算x/y的值
         * 如果x和y不在同一个集合，返回-1.0
         */
        public double calc(int x, int y) {
            int rootX = find(x);
            int rootY = find(y);

            // 如果不在同一个集合，无法计算
            if (rootX != rootY) {
                return -1.0;
            }

            // x/y = (x/rootX) * (rootX/rootY) * (rootY/y)
            // 由于rootX == rootY，所以rootX/rootY = 1
            // 所以x/y = (x/rootX) * (1/(y/rootY)) = weight[x] / weight[y]
            return weight[x] / weight[y];
        }
    }
}
//leetcode submit region end(Prohibit modification and deletion)

