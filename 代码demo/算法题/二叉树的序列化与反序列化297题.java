package 算法题;

import java.util.LinkedList;
import java.util.Queue;

public class 二叉树的序列化与反序列化297题 {

    // 序列化：将二叉树转为字符串（层序遍历，空节点用null标记）
    public String serialize(TreeNode root) {
        // 空树直接返回"null"
        if (root == null) {
            return "null";
        }
        // 层序遍历队列
        Queue<TreeNode> queue = new LinkedList<>();
        StringBuilder sb = new StringBuilder();
        queue.offer(root);

        while (!queue.isEmpty()) {
            TreeNode node = queue.poll();
            if (node == null) {
                sb.append("null,");
                continue;
            }
            // 拼接当前节点值
            sb.append(node.val).append(",");
            // 无论子节点是否为空，都加入队列（保证结构）
            queue.offer(node.left);
            queue.offer(node.right);
        }
        // 移除最后一个多余的逗号
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    // 反序列化：将字符串还原为二叉树
    public TreeNode deserialize(String data) {
        // 空字符串/空树直接返回null
        if (data == null || data.equals("null")) {
            return null;
        }
        // 分割字符串为节点值列表
        String[] nodes = data.split(",");
        // 层序遍历队列（存储待绑定子节点的父节点）
        Queue<TreeNode> queue = new LinkedList<>();
        // 创建根节点并加入队列
        TreeNode root = new TreeNode(Integer.parseInt(nodes[0]));
        queue.offer(root);

        int index = 1; // 指向当前待处理的节点值
        while (!queue.isEmpty() && index < nodes.length) {
            TreeNode parent = queue.poll();

            // 处理左子节点
            String leftVal = nodes[index++];
            if (!leftVal.equals("null")) {
                TreeNode leftNode = new TreeNode(Integer.parseInt(leftVal));
                parent.left = leftNode;
                queue.offer(leftNode);
            }

            // 处理右子节点（需判断索引是否越界）
            if (index < nodes.length) {
                String rightVal = nodes[index++];
                if (!rightVal.equals("null")) {
                    TreeNode rightNode = new TreeNode(Integer.parseInt(rightVal));
                    parent.right = rightNode;
                    queue.offer(rightNode);
                }
            }
        }
        return root;
    }

    // 测试示例
    public static void main(String[] args) {
        // 构建测试二叉树：
        //     1
        //    / \
        //   2   3
        //      / \
        //     4   5
        TreeNode root = new TreeNode(1);
        root.left = new TreeNode(2);
        root.right = new TreeNode(3);
        root.right.left = new TreeNode(4);
        root.right.right = new TreeNode(5);

        二叉树的序列化与反序列化297题 codec = new 二叉树的序列化与反序列化297题();
        String serialized = codec.serialize(root);
        System.out.println("序列化结果：" + serialized); // 输出：1,2,3,null,null,4,5,null,null,null,null

        TreeNode deserialized = codec.deserialize(serialized);
        String reserialized = codec.serialize(deserialized);
        System.out.println("反序列化后重新序列化：" + reserialized); // 与原序列化结果一致，验证正确性
    }
}

// 二叉树节点定义（题目中已隐含，需补充）
class TreeNode {
    int val;
    TreeNode left;
    TreeNode right;
    TreeNode(int x) { val = x; }
}