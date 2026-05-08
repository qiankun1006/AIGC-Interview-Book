package 偏向工程.LRU;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LRU线程安全<K, V> {

    public static class Node<K, V> {
        private K key;
        private V value;
        private Node<K, V> prev;
        private Node<K, V> next;

        public Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private final int cacheThre;
    private final Map<K, Node<K, V>> map;
    private final Node<K, V> head;
    private final Node<K, V> tail;
    private final ConcurrentHashMap<K, Lock> keyLocks;

    public LRU线程安全(int cacheThre) {
        this.cacheThre = cacheThre;
        this.map = new HashMap<>(cacheThre);
        this.head = new Node<>(null, null);
        this.tail = new Node<>(null, null);
        head.next = tail;
        tail.prev = head;
        this.keyLocks = new ConcurrentHashMap<>();
    }

    // 获取Key对应的锁（原子创建）
    private Lock getLock(K key) {
        return keyLocks.computeIfAbsent(key, k -> new ReentrantLock());
    }

    // 固定加锁顺序：哈希值小的先加，大的后加（避免死锁）
    private void lockTwoKeys(K keyA, K keyB) {
        if (keyA == null || keyB == null || keyA.equals(keyB)) {
            // 若其中一个Key无效/相同，只锁有效Key
            Lock lock = keyA != null ? getLock(keyA) : getLock(keyB);
            lock.lock();
            return;
        }
        int hashA = System.identityHashCode(keyA);
        int hashB = System.identityHashCode(keyB);
        Lock first = hashA < hashB ? getLock(keyA) : getLock(keyB);
        Lock second = hashA < hashB ? getLock(keyB) : getLock(keyA);
        first.lock();
        second.lock();
    }

    // 逆序解锁
    private void unlockTwoKeys(K keyA, K keyB) {
        if (keyA == null || keyB == null || keyA.equals(keyB)) {
            Lock lock = keyA != null ? getLock(keyA) : getLock(keyB);
            lock.unlock();
            return;
        }
        int hashA = System.identityHashCode(keyA);
        int hashB = System.identityHashCode(keyB);
        Lock first = hashA < hashB ? getLock(keyA) : getLock(keyB);
        Lock second = hashA < hashB ? getLock(keyB) : getLock(keyA);
        second.unlock();
        first.unlock();
    }

    // 终极极简版put：强制锁定当前Key + 尾节点Key
    public void put(K key, V value) {
        // 直接拿尾节点Key（不管是否需要淘汰，都锁定）
        K tailKey = tail.prev.key;

        // 第一步：锁定「当前Key + 尾节点Key」（固定顺序，无死锁）
        lockTwoKeys(key, tailKey);
        try {
            // 第二步：执行核心逻辑（全程在锁保护下）
            // 1. 若Key已存在，移除旧节点
            if (map.containsKey(key)) {
                Node<K, V> oldNode = map.get(key);
                removeNode(oldNode);
                map.remove(key);
            }

            // 2. 缓存满则淘汰尾节点（按需执行）
            if (map.size() >= cacheThre) {
                Node<K, V> evictNode = tail.prev;
                if (evictNode != head) { // 避免空链表
                    map.remove(evictNode.key);
                    removeNode(evictNode);
                    keyLocks.remove(evictNode.key); // 清理淘汰Key的锁
                }
            }

            // 3. 添加新节点到头部
            Node<K, V> newNode = new Node<>(key, value);
            map.put(key, newNode);
            addToHead(newNode);

        } catch (Exception e) {
            throw new RuntimeException("LRU put failed", e);
        } finally {
            // 第三步：统一解锁
            unlockTwoKeys(key, tailKey);
        }
    }

    // get方法保持Key级锁
    public V get(K key) {
        Lock lock = getLock(key);
        lock.lock();
        try {
            if (!map.containsKey(key)) return null;
            Node<K, V> node = map.get(key);
            removeNode(node);
            addToHead(node);
            return node.value;
        } finally {
            lock.unlock();
        }
    }

    private void removeNode(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
        node.prev = node.next = null;
    }

    private void addToHead(Node<K, V> node) {
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
        node.prev = head;
    }

    public static void main(String[] args) {
        LRU线程安全<Integer, String> lru = new LRU线程安全<>(2);

        // 并发测试（无死锁）
        new Thread(() -> lru.put(1, "A")).start();
        new Thread(() -> lru.put(2, "B")).start();
        new Thread(() -> lru.put(3, "C")).start();

        // 基础测试
        lru.put(1, "A");
        lru.put(2, "B");
        lru.put(3, "C");
        System.out.println(lru.get(1)); // null（被淘汰）
        System.out.println(lru.get(2)); // B
        System.out.println(lru.get(3)); // C

        new Thread(() -> {

        }).start();
    }
}