package 偏向工程.CAS;

import sun.misc.Unsafe;
import java.lang.reflect.Field;

/**
 * 手写支持解决ABA问题的CAS实现（值 + 版本号双校验）
 * 核心：每次修改值时，版本号必须递增，CAS时同时校验值和版本号
 */
public class MyAtomicStampedReference<T> {
    // 封装「值 + 版本号」的不可变内部类（保证线程安全）
    private static class Pair<T> {
        final T value;    // 实际存储的值
        final int stamp;  // 版本号（递增，不可回退）

        private Pair(T value, int stamp) {
            this.value = value;
            this.stamp = stamp;
        }

        // 静态工厂方法，创建新的Pair（值/版本变化时必须新建对象）
        static <T> Pair<T> of(T value, int stamp) {
            return new Pair<>(value, stamp);
        }
    }

    // 核心存储：volatile修饰，保证可见性（Pair不可变，只需保证引用可见）
    private volatile Pair<T> pair;

    // 用于CAS操作的Unsafe实例（通过反射获取，JDK底层CAS依赖）
    private static final Unsafe UNSAFE;
    // pair字段的内存偏移量（Unsafe CAS需要）
    private static final long PAIR_OFFSET;

    // 静态代码块：初始化Unsafe和字段偏移量
    static {
        try {
            // 1. 获取Unsafe实例（Unsafe的构造方法是私有的，需反射）
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            UNSAFE = (Unsafe) unsafeField.get(null);

            // 2. 获取pair字段的内存偏移量
            Field pairField = MyAtomicStampedReference.class.getDeclaredField("pair");
            PAIR_OFFSET = UNSAFE.objectFieldOffset(pairField);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new Error(e);
        }
    }

    /**
     * 构造方法：初始化值和版本号
     * @param initialRef 初始值
     * @param initialStamp 初始版本号
     */
    public MyAtomicStampedReference(T initialRef, int initialStamp) {
        this.pair = Pair.of(initialRef, initialStamp);
    }

    /**
     * 获取当前值和版本号（通过数组参数返回版本号，避免返回多个值）
     * @param stampHolder 长度为1的数组，用于接收当前版本号
     * @return 当前值
     */
    public T get(int[] stampHolder) {
        Pair<T> currentPair = this.pair;
        // 版本号通过数组回传（Java方法无法返回多个值）
        stampHolder[0] = currentPair.stamp;
        return currentPair.value;
    }

    /**
     * 核心CAS方法：同时校验「预期值 + 预期版本号」，匹配则更新为「新值 + 新版本号」
     * @param expectedRef 预期值
     * @param newRef 新值
     * @param expectedStamp 预期版本号
     * @param newStamp 新版本号
     * @return true：CAS成功；false：CAS失败
     */
    public boolean compareAndSet(T expectedRef, T newRef, int expectedStamp, int newStamp) {
        // 1. 获取当前的Pair（volatile读，保证可见性）
        Pair<T> currentPair = this.pair;

        // 2. 快速判断：如果预期值/版本号和当前不一致，直接返回false（避免无效CAS）
        if (currentPair.value != expectedRef || currentPair.stamp != expectedStamp) {
            return false;
        }

        // 3. 如果新值和新版本号和当前一致，无需更新，返回true
        if (currentPair.value == newRef && currentPair.stamp == newStamp) {
            return true;
        }

        // 4. 核心CAS操作：自旋重试 + 原子更新Pair引用
        Pair<T> newPair = Pair.of(newRef, newStamp);
        while (true) {
            // Unsafe的CAS核心方法：
            // 参数1：目标对象；参数2：字段偏移量；参数3：预期值；参数4：新值
            boolean success = UNSAFE.compareAndSwapObject(
                    this, PAIR_OFFSET, currentPair, newPair
            );
            if (success) {
                return true; // CAS成功，退出循环
            }

            // CAS失败，重新获取当前Pair，再次校验（处理并发修改）
            currentPair = this.pair;
            // 如果此时预期值/版本号已经不匹配，直接返回false
            if (currentPair.value != expectedRef || currentPair.stamp != expectedStamp) {
                return false;
            }

            // 如果新值/版本号和当前一致，无需更新
            if (currentPair.value == newRef && currentPair.stamp == newStamp) {
                return true;
            }

            // 否则继续自旋重试CAS
        }
    }

    /**
     * 无条件更新值和版本号（不校验，仅用于测试）
     * @param newRef 新值
     * @param newStamp 新版本号
     */
    public void set(T newRef, int newStamp) {
        Pair<T> currentPair = this.pair;
        if (newRef != currentPair.value || newStamp != currentPair.stamp) {
            this.pair = Pair.of(newRef, newStamp);
        }
    }

    // 辅助方法：获取当前版本号
    public int getStamp() {
        return this.pair.stamp;
    }

    // 辅助方法：获取当前值
    public T getReference() {
        return this.pair.value;
    }

    // ===================== 测试代码 =====================
    public static void main(String[] args) throws InterruptedException {
        // 1. 初始化：值=10，版本号=1
        MyAtomicStampedReference<Integer> cas = new MyAtomicStampedReference<>(10, 1);
        System.out.println("初始状态：值=" + cas.getReference() + "，版本号=" + cas.getStamp());

        // 2. 线程2：执行ABA操作（10→5→10），版本号从1→2→3
        Thread t2 = new Thread(() -> {
            int[] stampHolder = new int[1];
            // 获取当前值和版本号
            Integer currValue = cas.get(stampHolder);
            int currStamp = stampHolder[0];
            System.out.println("线程2 - 初始值：" + currValue + "，初始版本：" + currStamp);

            // 第一步：10→5，版本1→2
            boolean success1 = cas.compareAndSet(currValue, 5, currStamp, currStamp + 1);
            System.out.println("线程2 - 10→5：" + (success1 ? "成功" : "失败") +
                    "，当前值=" + cas.getReference() + "，版本=" + cas.getStamp());

            // 第二步：5→10，版本2→3（完成ABA）
            currValue = cas.get(stampHolder);
            currStamp = stampHolder[0];
            boolean success2 = cas.compareAndSet(currValue, 10, currStamp, currStamp + 1);
            System.out.println("线程2 - 5→10：" + (success2 ? "成功" : "失败") +
                    "，当前值=" + cas.getReference() + "，版本=" + cas.getStamp());
        });

        // 3. 线程1：尝试CAS更新（预期值=10，预期版本=1 → 会失败，因为版本号已变）
        Thread t1 = new Thread(() -> {
            try {
                // 挂起1秒，让线程2先完成ABA操作
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            int[] stampHolder = new int[1];
            Integer currValue = cas.get(stampHolder);
            int currStamp = stampHolder[0];
            System.out.println("\n线程1 - 当前值：" + currValue + "，当前版本：" + currStamp);

            // 尝试CAS：预期值=10，预期版本=1 → 更新为20，版本=2
            boolean success = cas.compareAndSet(10, 20, 1, 2);
            System.out.println("线程1 - CAS结果（预期值10+版本1 → 新值20+版本2）：" + (success ? "成功" : "失败"));
            System.out.println("线程1 - 最终状态：值=" + cas.getReference() + "，版本=" + cas.getStamp());
        });

        // 启动线程
        t2.start();
        t1.start();
        // 等待线程执行完成
        t2.join();
        t1.join();
    }
}