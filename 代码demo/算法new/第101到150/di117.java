package 算法new.第101到150;



public class di117 {
    //给定一个二叉树：
    //struct Node {
    //  int val;
    //  Node *left;
    //  Node *right;
    //  Node *next;
    //}
    //填充它的每个 next 指针，让这个指针指向其下一个右侧节点。如果找不到下一个右侧节点，则将 next 指针设置为 NULL 。
    //
    //初始状态下，所有 next 指针都被设置为 NULL 。
    //示例 1：
    //输入：root = [1,2,3,4,5,null,7]
    //输出：[1,#,2,3,#,4,5,7,#]
    //解释：给定二叉树如图 A 所示，你的函数应该填充它的每个 next 指针，以指向其下一个右侧节点，如图 B 所示。
    // 序列化输出按层序遍历顺序（由 next 指针连接），'#' 表示每层的末尾。
    public Node connect(Node root) {
        if(root == null) {
            return null;
        }
        Node cache = null;
        if(root.left != null) {
            cache = root.left;
        }
        if(root.right != null) {
            cache = root.right;
        }
        if(cache == null) {
            return root;
        }
        Node next = root.next;
        while(next != null) {
            if(next.left != null) {
                cache.next = next.left;
                break;
            }
            if(next.right != null) {
                cache.next = next.right;
                break;
            }
            next = next.next;
        }
        if(root.left != null && root.right != null) {
            root.left.next = root.right;
        }
        //todo 必须先递归右子树，再递归左子树
        // 因为左子树最右节点的 next 需要去 root.next 方向查找，
        // 而那侧节点的内部 next 链要先建好才能正确遍历
        // 真没注意到！！！还是得写一遍！！！
        connect(root.right);
        connect(root.left);
        return root;
    }
}

class Node {
    public int val;
    public Node left;
    public Node right;
    public Node next;

    public Node() {}

    public Node(int _val) {
        val = _val;
    }

    public Node(int _val, Node _left, Node _right, Node _next) {
        val = _val;
        left = _left;
        right = _right;
        next = _next;
    }
}

