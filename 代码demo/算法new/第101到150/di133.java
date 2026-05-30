package 算法new.第101到150;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class di133 {

    //todo 这题是没看答案做出来的
    // 举一个三角形的情况代入
    public Node cloneGraph(Node node) {
        if (node == null) return null;
        // map 放在方法内，避免多次调用时状态残留
        return dfs(node, new HashMap<>());
    }

    private Node dfs(Node node, Map<Node, Node> map) {
        // 已经克隆过，直接返回（守卫条件，合并了原来的 if/else 两个分支）
        if (map.containsKey(node)) return map.get(node);

        Node cur = new Node(node.val);
        map.put(node, cur); // 先放入 map，再处理邻居，防止环形图死循环

        for (Node neighbor : node.neighbors) {
            cur.neighbors.add(dfs(neighbor, map));
        }
        return cur;
    }

    class Node {
        public int val;
        public List<Node> neighbors;

        public Node() {
            val = 0;
            neighbors = new ArrayList<Node>();
        }

        public Node(int _val) {
            val = _val;
            neighbors = new ArrayList<Node>();
        }

        public Node(int _val, ArrayList<Node> _neighbors) {
            val = _val;
            neighbors = _neighbors;
        }
    }
}
