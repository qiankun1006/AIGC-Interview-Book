# JVM 面试题库（自测用，无答案）

> 题目覆盖：内存区域 → 对象模型 → 类加载 → 垃圾回收基础 → GC 算法 → 垃圾收集器 → JIT 编译 → 调优工具 → 性能调优 → 实战场景

---

## 一、JVM 整体架构

1. JVM 的整体架构分为哪几个子系统？各自的职责是什么？
2. JVM 规范和 JVM 实现有什么区别？HotSpot 是什么？除了 HotSpot 还有哪些 JVM 实现？
3. Java 程序从源码到执行的完整流程是什么？经历了哪些步骤？
4. 编译型语言和解释型语言的区别是什么？Java 是哪种？为什么说 Java 是"半编译半解释"？
5. `.class` 字节码文件的结构是怎样的？包含哪些关键信息？

---

## 二、运行时数据区

### 线程私有区域
6. JVM 的运行时数据区分为哪几块？哪些是线程私有的，哪些是线程共享的？
7. 程序计数器（PC Register）的作用是什么？为什么它是唯一不会发生 OOM 的区域？
8. Java 虚拟机栈的结构是什么？栈帧（Stack Frame）包含哪些内容？
9. 局部变量表（Local Variable Table）的槽位（Slot）是如何分配的？long/double 为什么占两个槽？
10. 操作数栈（Operand Stack）的作用是什么？举例说明 `a + b` 的字节码执行过程。
11. 动态链接（Dynamic Linking）和方法返回地址分别是什么？
12. `StackOverflowError` 和 `OutOfMemoryError` 在虚拟机栈场景下分别在什么情况下抛出？
13. 本地方法栈（Native Method Stack）是什么？HotSpot 中它和虚拟机栈有什么关系？

### 堆
14. Java 堆是如何分代的？新生代和老年代的比例默认是多少？
15. 新生代中 Eden 区和两个 Survivor 区（S0/S1）的比例默认是多少？为什么要有两个 Survivor 区？
16. 为什么要分代？分代假说（Generational Hypothesis）是什么？
17. 大对象为什么会直接进入老年代？阈值是多少？如何配置？
18. 堆内存的 `-Xms` 和 `-Xmx` 有什么区别？为什么生产环境建议两者设置相同？
19. Java 8 中的元空间（Metaspace）和 Java 7 的永久代（PermGen）有什么区别？
20. 堆外内存（Direct Memory）是什么？`-XX:MaxDirectMemorySize` 的作用是什么？

### 方法区 / 元空间
21. 方法区存储什么内容？元空间的内存来自哪里？
22. 运行时常量池（Runtime Constant Pool）和字符串常量池（String Pool）有什么区别？分别在哪里？
23. `String.intern()` 方法的作用是什么？在 Java 6 和 Java 7+ 中行为有什么不同？
24. 为什么用 Java 8 的元空间替换永久代后，`java.lang.OutOfMemoryError: PermGen space` 这个错误就消失了？
25. 元空间会发生内存溢出吗？什么情况下会触发？

---

## 三、对象的创建与内存布局

### 对象创建
26. `new` 一个对象在 JVM 层面经历了哪些步骤？
27. JVM 为对象分配内存时有哪两种方式（指针碰撞和空闲列表）？各在什么情况下使用？
28. 多线程并发创建对象时，如何保证内存分配的线程安全？TLAB 是什么？
29. 对象创建完成后，成员变量的零值初始化和代码中的显式初始化分别在什么时机完成？

### 对象内存布局
30. Java 对象在内存中的布局由哪几部分组成？
31. 对象头（Object Header）包含哪两个部分？Mark Word 里存了哪些信息？
32. Mark Word 在不同锁状态下（无锁/偏向锁/轻量级锁/重量级锁）的布局分别是什么？
33. 实例数据（Instance Data）中字段的内存排列顺序是什么？
34. 什么是内存对齐（Padding）？为什么 JVM 要做 8 字节对齐？
35. 在 64 位 JVM 中，一个空对象（`new Object()`）占多少字节？
36. 指针压缩（Compressed OOPs）是什么？开启后对象引用从 8 字节压缩到几字节？什么情况下会自动关闭？

### 对象访问定位
37. JVM 通过引用访问对象有哪两种方式（句柄池和直接指针）？HotSpot 用的是哪种？各有什么优缺点？
38. 什么是逃逸分析（Escape Analysis）？它能带来哪些优化（栈上分配、标量替换、同步消除）？

---

## 四、类加载机制

### 类加载流程
39. 类的生命周期分为哪几个阶段？
40. 类加载的三个阶段（加载、链接、初始化）分别做了什么事？
41. 链接阶段的验证（Verification）、准备（Preparation）、解析（Resolution）分别做什么？
42. 准备阶段给静态变量赋的是什么值？初始化阶段才赋什么值？
43. 类的初始化（`<clinit>`）在哪些情况下会被触发（主动引用的 6 种场景）？
44. 什么是被动引用？为什么通过子类引用父类静态字段不会初始化子类？

### 类加载器
45. JVM 有哪几种内置类加载器？各自加载哪些类？
46. 双亲委派模型（Parent Delegation Model）的工作原理是什么？为什么要这么设计？
47. 如何打破双亲委派模型？有哪些经典案例（SPI / JNDI / OSGi / Java 9 模块化）？
48. `ClassLoader.loadClass()` 和 `Class.forName()` 的区别是什么？
49. 自定义类加载器需要重写哪个方法？为什么推荐重写 `findClass()` 而不是 `loadClass()`？
50. 同一个 `.class` 文件被两个不同的类加载器加载，得到的 Class 对象相等吗？
51. Tomcat 的类加载器是如何设计的？为什么要打破双亲委派？

---

## 五、垃圾回收基础

### 判断对象存活
52. JVM 如何判断一个对象是否可以被回收？
53. 引用计数法（Reference Counting）有什么缺点？为什么 JVM 不用它？
54. 可达性分析（Reachability Analysis）的原理是什么？GC Roots 都有哪些？
55. 对象被判定为不可达后一定会被立即回收吗？`finalize()` 方法在什么情况下会被调用？
56. 为什么不推荐使用 `finalize()` 方法？它有哪些问题？

### 引用类型
57. Java 的四种引用类型（强引用/软引用/弱引用/虚引用）分别是什么？各在什么场景下使用？
58. `SoftReference` 什么时候会被回收？与堆内存大小的关系是什么？
59. `WeakReference` 和 `SoftReference` 的区别是什么？`WeakHashMap` 的使用场景是什么？
60. `PhantomReference`（虚引用）的特点是什么？它一般配合什么使用？
61. `ReferenceQueue` 是什么？它在软/弱/虚引用中起什么作用？

---

## 六、GC 算法

62. 标记-清除（Mark-Sweep）算法的原理是什么？有哪两个主要缺点？
63. 标记-复制（Mark-Copy）算法的原理是什么？为什么适合新生代？它的空间利用率是多少？
64. 标记-整理（Mark-Compact）算法的原理是什么？相比标记-清除有什么优势和代价？
65. 分代收集算法的核心思想是什么？Minor GC、Major GC、Full GC 分别回收哪些区域？
66. Minor GC 的触发条件是什么？Major GC / Full GC 的触发条件是什么？
67. 什么是 Stop-The-World（STW）？为什么垃圾回收需要 STW？
68. 安全点（Safepoint）是什么？JVM 如何让所有线程在 Safepoint 暂停？
69. 安全区域（Safe Region）是什么？它和 Safepoint 有什么区别？
70. 三色标记法（Tri-color Marking）的原理是什么？黑/灰/白分别代表什么？
71. 三色标记中的"漏标"和"多标"问题是什么？如何解决（增量更新 vs 原始快照）？
72. 写屏障（Write Barrier）在 GC 中的作用是什么？读屏障（Read Barrier）呢？
73. 卡表（Card Table）是什么？它如何解决跨代引用的问题？
74. 记忆集（Remembered Set）是什么？它和卡表是什么关系？

---

## 七、垃圾收集器

### 经典收集器
75. Serial 收集器的特点是什么？适合什么场景？
76. ParNew 收集器和 Serial 收集器有什么关系？为什么 ParNew 能和 CMS 配合使用？
77. Parallel Scavenge 收集器和 ParNew 的区别是什么？它的设计目标是什么（吞吐量优先）？
78. `-XX:GCTimeRatio` 和 `-XX:MaxGCPauseMillis` 参数的含义是什么？两者是否会冲突？
79. Serial Old 和 Parallel Old 分别是什么？它们针对哪个区域？

### CMS 收集器
80. CMS（Concurrent Mark-Sweep）收集器的工作流程分为哪几个阶段？
81. CMS 的初始标记和重新标记阶段为什么需要 STW？耗时分别如何？
82. CMS 在并发标记和并发清除阶段，用户线程仍在运行，会不会产生新的垃圾（浮动垃圾）？
83. CMS 为什么会产生内存碎片？`-XX:+UseCMSCompactAtFullCollection` 的作用是什么？
84. CMS 的并发失败（Concurrent Mode Failure）是什么？会带来什么后果？
85. CMS 触发的条件是什么？`-XX:CMSInitiatingOccupancyFraction` 参数的作用是什么？

### G1 收集器
86. G1 的设计目标是什么？它与 CMS 的最大区别是什么？
87. G1 的 Region 是什么？Humongous Region 是什么？
88. G1 的工作流程分为哪几个阶段（Young GC / Concurrent Marking / Mixed GC / Full GC）？
89. G1 的 Mixed GC 是什么？它如何选择回收哪些 Region（回收收益优先）？
90. G1 是如何实现可预测暂停时间（Pause Prediction）的？`-XX:MaxGCPauseMillis` 是硬保证还是软目标？
91. G1 中的 Remembered Set 是如何实现的？为什么 G1 的 RSet 内存开销比其他收集器大？
92. G1 什么情况下会退化为 Full GC？如何避免 G1 Full GC？

### ZGC 与 Shenandoah
93. ZGC 的核心设计目标是什么？它如何将 STW 时间控制在毫秒以内？
94. ZGC 的染色指针（Colored Pointers）技术是什么？它有什么作用？
95. ZGC 的并发转移（Concurrent Relocation）是如何实现的？读屏障在其中起什么作用？
96. Shenandoah GC 和 ZGC 的核心思路有什么异同？
97. Java 11 / Java 17 / Java 21 在 GC 方面有哪些重要变化？

### 收集器选型
98. 如何根据业务场景选择合适的垃圾收集器？你会考虑哪些维度？
99. 吞吐量优先和低延迟优先的场景分别应该选哪个收集器？
100. G1 和 ZGC 应该如何选择？堆内存大小对选型有什么影响？

---

## 八、JIT 编译与执行优化

101. JVM 的执行引擎有哪几种模式（解释执行、编译执行、混合执行）？
102. 什么是 JIT（Just-In-Time）编译？JVM 为什么不一开始就全部 JIT 编译？
103. HotSpot 的两个 JIT 编译器（C1 和 C2）有什么区别？分层编译（Tiered Compilation）是什么？
104. 什么是热点代码（Hot Code）？JVM 如何检测热点代码（方法调用计数器 / 回边计数器）？
105. JIT 的主要优化技术有哪些（方法内联 / 逃逸分析 / 循环展开 / 公共子表达式消除）？
106. 方法内联（Inlining）是如何工作的？`@inline` 和内联的深度限制是什么？
107. 即时编译的代码在什么情况下会被"去优化"（Deoptimization）？
108. AOT（Ahead-Of-Time）编译和 JIT 编译有什么区别？GraalVM 的 Native Image 是什么？
109. Java 9+ 的 JVMCI（JVM Compiler Interface）是什么？Graal JIT 是如何基于它实现的？

---

## 九、并发相关的 JVM 机制

110. Java 内存模型（JMM）是什么？它解决了什么问题？
111. JMM 中的主内存和工作内存是什么？线程间通信如何发生？
112. `volatile` 关键字在 JVM 层面是如何实现的？内存屏障（Memory Barrier）是什么？
113. `volatile` 能保证原子性吗？`i++` 为什么不是线程安全的？
114. happens-before 原则有哪些规则？为什么它是理解 JMM 的核心？
115. 指令重排序有哪几种来源（编译器重排 / 处理器重排 / 内存系统重排）？
116. `synchronized` 在 JVM 层面是如何实现的？`monitorenter` 和 `monitorexit` 字节码指令是什么？
117. 锁升级的过程是什么（无锁 → 偏向锁 → 轻量级锁 → 重量级锁）？每个阶段的触发条件是什么？
118. 偏向锁在 Java 15 中被废弃了，原因是什么？
119. `CAS`（Compare-And-Swap）的原理是什么？它存在哪些问题（ABA 问题）？
120. `AtomicInteger` 的 `incrementAndGet()` 在 JVM 层面是如何实现的？

---

## 十、JVM 调优工具

121. JDK 自带的性能分析工具有哪些？分别适合什么场景？
122. `jps` 命令的作用是什么？如何查看当前 JVM 进程的 PID？
123. `jstat` 命令能查看哪些信息？如何用它实时监控 GC 情况？
124. `jmap` 有哪些常用用法？如何 dump 堆内存快照？`-histo` 和 `-dump` 有什么区别？
125. `jstack` 的作用是什么？如何用它分析死锁和线程 CPU 飙高的问题？
126. `jinfo` 命令的作用是什么？能否在运行时动态修改 JVM 参数？
127. `jcmd` 和传统的 `jmap` / `jstack` 相比有什么优势？
128. `VisualVM` 和 `JConsole` 有什么区别？各自适合什么使用场景？
129. Arthas 是什么？你用过它的哪些命令（`watch` / `trace` / `dashboard` / `jad`）？
130. 如何用 `async-profiler` 做 CPU 火焰图分析？它和 JVM 内置的采样有什么区别？
131. 什么是 GC 日志？如何开启并解读 GC 日志（`-Xlog:gc*` / `-XX:+PrintGCDetails`）？

---

## 十一、OOM 排查与性能调优

### OOM 问题排查
132. `java.lang.OutOfMemoryError: Java heap space` 的常见原因有哪些？排查步骤是什么？
133. `java.lang.OutOfMemoryError: Metaspace` 什么情况下会出现？常见原因是什么？
134. `java.lang.OutOfMemoryError: Direct buffer memory` 是什么问题？如何排查？
135. `java.lang.OutOfMemoryError: unable to create new native thread` 为什么会出现？
136. `java.lang.StackOverflowError` 和 `OutOfMemoryError` 在栈场景下各在什么情况发生？
137. 内存泄漏（Memory Leak）和内存溢出（Memory Overflow）的区别是什么？如何定位内存泄漏？
138. 如何用 MAT（Memory Analyzer Tool）分析堆转储文件（Heap Dump）？Dominator Tree 是什么？

### GC 调优
139. GC 调优的目标通常是什么？吞吐量、延迟、内存占用三者如何取舍？
140. 如何通过 GC 日志判断系统是否存在 GC 问题？关注哪些指标？
141. 频繁 Minor GC 的原因是什么？如何解决？
142. 频繁 Full GC 的原因有哪些？逐一如何排查？
143. 如何设置合理的新生代和老年代大小？有哪些经验法则？
144. 对象晋升（Promotion）到老年代的条件有哪些？如何调整晋升阈值？
145. 什么是 GC 停顿时间过长？如何系统性地降低 GC 停顿时间？

### CPU 与线程问题
146. Java 进程 CPU 使用率飙高，完整的排查流程是什么？
147. 如何用 `jstack` 排查死锁？死锁的日志特征是什么？
148. 什么是线程泄漏（Thread Leak）？如何检测和排查？
149. 线程池大小如何合理配置？CPU 密集型和 IO 密集型有什么不同的公式？
150. 如何分析一个 Java 服务的 TP99 突然升高？从 JVM 角度有哪些排查方向？

---

## 十二、JVM 进阶与实战

151. Java Agent 是什么？`premain` 和 `agentmain` 有什么区别？字节码增强工具（ASM / Javassist / Byte Buddy）各有什么特点？
152. `Instrumentation` API 能做什么？APM（应用性能监控）是如何利用它实现无侵入埋点的？
153. JVM TI（JVM Tool Interface）是什么？它与 Java Agent 的关系是什么？
154. GraalVM 是什么？它与普通 HotSpot JVM 有哪些核心区别？
155. Native Image（AOT 编译）的优缺点是什么？适合什么类型的应用？
156. Java 9 的模块化系统（JPMS）对类加载机制有什么影响？
157. 虚拟线程（Virtual Thread，Java 21 正式引入）和平台线程有什么区别？它在 JVM 层面是如何实现的？
158. 虚拟线程对传统线程池模型有什么冲击？使用虚拟线程需要注意哪些陷阱？
159. 如何对一个 JVM 应用做启动时间优化？有哪些手段（AppCDS / AOT / 懒加载）？
160. 在容器（Docker/Kubernetes）环境中运行 JVM 应用需要注意哪些问题？`-XX:+UseContainerSupport` 的作用是什么？

