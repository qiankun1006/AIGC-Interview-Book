package 算法题;

import java.util.*;

public class 第225用队列实现栈 {

    Queue<Integer> queue;

    public 第225用队列实现栈() {
        queue = new LinkedList<>();
    }

    public void push(int x) {
        if (queue.isEmpty()) {
            queue.add(x);
            return;
        }
        int len = queue.size();
        queue.add(x);
        for (int i = 0; i < len; i++) {
            int cache = queue.poll();
            queue.add(cache);
        }
    }

    public int pop() {
        return queue.poll();
    }

    public int top() {
        return queue.peek();
    }

    public boolean empty() {
        return queue.isEmpty();
    }
}

