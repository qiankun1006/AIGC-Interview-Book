# LangChain / LangGraph / Spring AI 面试题库（自测用，无答案）

> 题目覆盖：LangChain 核心模块 → Chain 与 LCEL → Memory → Agent → Callback → LangGraph 核心概念 → Graph 构建 → 状态管理 → 多 Agent → Spring AI 基础 → 三框架横向对比 → 实战场景

---

## 一、LangChain 基础概念

1. LangChain 是什么？它解决了 LLM 应用开发的哪些痛点？
2. LangChain 的核心抽象层有哪几个（Model I/O / Retrieval / Chains / Agents / Memory / Callbacks）？各自负责什么？
3. LangChain 支持哪些编程语言版本？Python 版和 JS/TS 版在功能上有什么差异？
4. `langchain` / `langchain-core` / `langchain-community` / `langchain-experimental` 这几个包分别包含什么内容？为什么要拆分？
5. LangChain 0.1 到 0.3 版本的演进中，有哪些重大架构变化？LCEL 是在哪个版本引入的？

---

## 二、Model I/O 模块

### LLM 与 ChatModel
6. LangChain 中 `LLM` 和 `ChatModel` 的区别是什么？分别适合什么场景？
7. `ChatOpenAI` / `ChatAnthropic` / `ChatBedrock` 等模型类的统一接口是什么？切换不同模型需要改多少代码？
8. LangChain 如何处理不同 LLM Provider 的 API 差异（如 token 计费、context 长度、工具调用格式）？
9. `invoke` / `stream` / `batch` / `ainvoke` 这几种调用方式有什么区别？`batch` 的并发是如何实现的？
10. LangChain 的模型回调（Callbacks）和模型本身的流式输出（Streaming）有什么区别？

### Prompt 模板
11. `PromptTemplate` 和 `ChatPromptTemplate` 的区别是什么？`SystemMessagePromptTemplate` 和 `HumanMessagePromptTemplate` 分别用来做什么？
12. Few-shot Prompt 在 LangChain 中如何实现？`FewShotPromptTemplate` 的 `ExampleSelector` 有哪些实现？
13. `MessagesPlaceholder` 的作用是什么？它在多轮对话中如何使用？
14. Prompt 的部分变量填充（Partial Prompt）是如何实现的？有什么使用场景？
15. 如何在 LangChain 中对 Prompt 进行版本管理？LangSmith 的 Prompt Hub 是什么？

### Output Parser
16. LangChain 中有哪些常用的 Output Parser？`PydanticOutputParser` 和 `JsonOutputParser` 有什么区别？
17. `StructuredOutputParser` 是如何工作的？它的 `format_instructions` 是如何注入到 Prompt 中的？
18. 当 LLM 输出不符合预期格式时，`OutputFixingParser` 是如何自动修复的？
19. `with_structured_output()` 方法和手动使用 Output Parser 有什么区别？底层实现有何不同？

---

## 三、LCEL（LangChain Expression Language）

20. LCEL 是什么？它的设计目标是什么？为什么要用它取代老的 `Chain` 类？
21. LCEL 的核心接口 `Runnable` 包含哪些方法？`Runnable` 接口的设计有什么特点？
22. LCEL 中 `|` 管道操作符是如何工作的？`RunnableSequence` 的内部实现原理是什么？
23. `RunnableParallel` 的作用是什么？如何用它实现多路并发调用？
24. `RunnablePassthrough` 和 `RunnableLambda` 分别在什么场景下使用？
25. `RunnableBranch` 如何实现条件分支逻辑？有没有更好的替代写法？
26. LCEL 的 `bind()` 方法有什么用？如何用它预绑定模型参数？
27. LCEL 链中如何统一处理异常？`.with_fallbacks()` 和 `.with_retry()` 的区别是什么？
28. LCEL 的流式输出（`.stream()`）是如何在整个链路中传递的？中间有 Output Parser 时流式输出还有效吗？
29. `ConfigurableField` 和 `configurable_fields()` 是什么？如何在运行时动态替换链中的组件？

---

## 四、Memory 模块

30. LangChain 的 Memory 模块的作用是什么？它在 LCEL 时代的地位如何变化？
31. `ConversationBufferMemory` 和 `ConversationBufferWindowMemory` 的区别是什么？
32. `ConversationSummaryMemory` 和 `ConversationSummaryBufferMemory` 分别是什么？什么时候用摘要型记忆？
33. `ConversationTokenBufferMemory` 的作用是什么？它如何控制 token 消耗？
34. `VectorStoreRetrieverMemory` 的原理是什么？它和普通 RAG 的记忆有什么区别？
35. 在 LCEL 链中手动管理对话历史（`chat_history`）和使用 Memory 类的优缺点各是什么？
36. `RunnableWithMessageHistory` 是什么？它如何与外部存储（Redis / 数据库）集成实现持久化记忆？

---

## 五、Retrieval 与 RAG

37. LangChain 的 RAG 流程分为哪几个步骤？`DocumentLoader` / `TextSplitter` / `Embedding` / `VectorStore` / `Retriever` 各自的职责是什么？
38. LangChain 支持哪些 `TextSplitter`？`RecursiveCharacterTextSplitter` 和 `CharacterTextSplitter` 有什么区别？
39. `VectorStoreRetriever` 的几种搜索类型（similarity / mmr / similarity_score_threshold）分别是什么？
40. `MultiQueryRetriever` 的原理是什么？它解决了什么问题？
41. `ContextualCompressionRetriever` 是什么？`LLMChainFilter` 和 `EmbeddingsFilter` 有什么区别？
42. `EnsembleRetriever` 如何实现多路召回融合？`BM25Retriever` 和向量检索如何混合排序？
43. `ParentDocumentRetriever` 是什么？它解决了 chunk 过小导致语义割裂的什么问题？
44. LangChain 中如何实现 Reranker（重排序）？有哪些方案？
45. `create_retrieval_chain` 和手动用 LCEL 构建 RAG 链有什么区别？

---

## 六、LangChain Agent

46. LangChain 中 Agent 的核心组件有哪些（AgentExecutor / Tool / Prompt / LLM / Memory）？
47. `AgentExecutor` 的执行循环是什么？它和 ReAct 框架的关系是什么？
48. LangChain 中有哪些内置的 Agent 类型（ReAct / OpenAI Tools / OpenAI Functions / Structured Chat）？各适合什么场景？
49. 如何自定义一个 Tool？`@tool` 装饰器和继承 `BaseTool` 有什么区别？
50. Tool 的 `args_schema` 是什么？为什么推荐用 Pydantic 定义工具输入 schema？
51. `ToolException` 如何处理？当工具执行失败时 Agent 会怎么做？
52. `max_iterations` 和 `max_execution_time` 参数的作用是什么？如何防止 Agent 无限循环？
53. `handle_parsing_errors` 参数是什么？LLM 输出无法解析为 Action 时如何处理？
54. LCEL 时代如何用 `create_react_agent` 取代老的 `initialize_agent`？两者有什么本质区别？
55. LangChain 中的 `Toolkits`（如 `SQLDatabaseToolkit` / `GmailToolkit`）是什么？

---

## 七、Callbacks 与可观测性

56. LangChain 的 Callback 系统是如何设计的？有哪些内置的事件钩子（`on_llm_start` / `on_chain_end` 等）？
57. `StdOutCallbackHandler` / `FileCallbackHandler` / `WandbCallbackHandler` 分别是什么？
58. 如何实现一个自定义 `CallbackHandler`？需要继承哪个基类，重写哪些方法？
59. 同步 Callback 和异步 Callback 的区别是什么？在异步链中应该用哪种？
60. LangSmith 是什么？如何在 LangChain 中接入 LangSmith 做链路追踪？
61. 如何用 LangSmith 对 LLM 输出质量进行评测（Evaluation）？`RunEvaluator` 是什么？

---

## 八、LangGraph 核心概念

62. LangGraph 是什么？它解决了 LangChain `AgentExecutor` 的哪些局限性？
63. LangGraph 的三个核心抽象是什么（State / Node / Edge）？各自的职责是什么？
64. `StateGraph` 和 `MessageGraph` 有什么区别？各适合什么场景？
65. LangGraph 中的 State 是如何定义的？`TypedDict` 和 `Annotated` 在 State 定义中如何使用？
66. `add_messages` reducer 的作用是什么？为什么 LangGraph 默认使用追加而不是覆盖方式更新消息？
67. 自定义 reducer 是什么？什么情况下需要自定义 State 的更新逻辑？
68. LangGraph 的编译（`compile()`）做了什么？`CompiledGraph` 和 `StateGraph` 有什么区别？

---

## 九、LangGraph 图的构建与控制流

69. `add_node()` / `add_edge()` / `add_conditional_edges()` 分别如何使用？
70. `START` 和 `END` 节点的作用是什么？一个 Graph 可以有多个 END 节点吗？
71. 条件边（Conditional Edge）的路由函数如何设计？返回值的格式有什么要求？
72. 如何在 LangGraph 中实现循环（Loop）？循环终止条件如何设计？
73. LangGraph 的并行节点（Fan-out / Fan-in）如何实现？并行分支的结果如何合并？
74. `Send` API 是什么？它如何实现动态的 Map-Reduce 模式？
75. Subgraph（子图）如何定义和嵌套？父图和子图之间如何传递 State？
76. `Command` 对象是什么？它相比条件边有什么优势？

---

## 十、LangGraph 持久化与人机协作

77. LangGraph 的 Checkpointer（检查点）是什么？它如何实现断点续跑？
78. 内置的 Checkpointer 有哪几种（MemorySaver / SqliteSaver / PostgresSaver）？生产环境推荐用哪种？
79. `thread_id` 和 `checkpoint_id` 的作用是什么？如何用它们实现多会话隔离？
80. LangGraph 的 Human-in-the-Loop 是如何实现的？`interrupt_before` 和 `interrupt_after` 有什么区别？
81. `graph.update_state()` 的作用是什么？人工介入后如何修改 State 并恢复执行？
82. LangGraph 如何实现"时间旅行"（Time Travel）？如何回滚到某个历史 checkpoint 重新执行？
83. 长时间运行的异步任务（Long Running Tasks）在 LangGraph 中如何处理？

---

## 十一、LangGraph Multi-Agent

84. LangGraph 中的 Multi-Agent 有哪几种常见的拓扑结构（网络型 / 监督者型 / 分层型）？
85. Supervisor（监督者）模式如何实现？Supervisor 如何决定把任务交给哪个 Sub-Agent？
86. 如何用 LangGraph 实现 Agent 之间的对话（Swarm 模式）？`handoff` 工具的原理是什么？
87. Multi-Agent 中各个 Agent 如何共享 State？各自私有的 State 如何隔离？
88. Supervisor 模式和 Hierarchical（分层）模式有什么区别？什么时候需要多层 Supervisor？
89. 在 Multi-Agent 系统中如何处理 Agent 执行失败的情况？有哪些错误传播和恢复策略？
90. LangGraph Platform / LangGraph Cloud 是什么？它提供了哪些生产化能力（部署 / 监控 / Streaming）？

---

## 十二、Spring AI 基础

91. Spring AI 是什么？它的定位和 LangChain 有什么相同和不同之处？
92. Spring AI 的核心模块有哪些（`ChatClient` / `EmbeddingModel` / `VectorStore` / `ImageModel` 等）？
93. `ChatClient` 和 `ChatModel` 的区别是什么？`ChatClient` 的 Fluent API 是如何设计的？
94. Spring AI 的 `Advisor` 机制是什么？它和 LangChain 的 Callback / LCEL 中间件有什么异同？
95. `MessageChatMemoryAdvisor` 和 `VectorStoreChatMemoryAdvisor` 分别如何工作？
96. Spring AI 的 RAG 流程是如何构建的？`QuestionAnswerAdvisor` 和手动构建 RAG 有什么区别？
97. Spring AI 的 `DocumentReader` / `DocumentTransformer` / `DocumentWriter` 的设计思路是什么？
98. Spring AI 如何接入不同的 LLM Provider（OpenAI / Azure OpenAI / Ollama / Anthropic）？切换成本是多少？
99. Spring AI 的 `EmbeddingModel` 接口的作用是什么？支持哪些向量数据库（pgvector / Milvus / Redis）？
100. Spring AI 的 Function Calling（Tool Call）是如何实现的？`@Tool` 注解和 LangChain 的 `@tool` 有什么异同？
101. Spring AI 的 `ToolCallbackProvider` 是什么？如何自动发现和注册 Spring Bean 中的工具方法？
102. Spring AI 的 Structured Output（结构化输出）是如何实现的？和 LangChain 的 Output Parser 有什么区别？
103. Spring AI 对 Multimodal（多模态，图片 / 音频）的支持是如何设计的？
104. Spring AI 的 Model Options（如 `OpenAiChatOptions`）机制是什么？如何在运行时动态覆盖参数？
105. Spring AI Observability（可观测性）是如何实现的？如何接入 Micrometer / Zipkin / Prometheus？

---

## 十三、三框架横向对比

### 设计理念对比
106. LangChain、LangGraph、Spring AI 在设计哲学上各自的核心理念是什么？面向的用户群体有何不同？
107. LangChain 的 LCEL 链式设计 和 Spring AI 的 Fluent API + Advisor 链式设计，在灵活性和易用性上有什么取舍？
108. LangGraph 的显式状态机（State Machine）设计相比 LangChain `AgentExecutor` 的隐式循环，在什么场景下有明显优势？

### 技术能力对比
109. 在 Memory / 对话历史管理上，LangChain、LangGraph、Spring AI 分别是如何实现的？各自的持久化方案有什么不同？
110. 在 RAG 构建上，三个框架各自的抽象层次和可定制程度有什么差异？
111. 在 Agent / Tool Calling 的实现上，三个框架有什么本质区别？谁更适合复杂 Agent 场景？
112. 在可观测性（Tracing / Logging / Metrics）上，三个框架各自的方案是什么？哪个与企业级监控体系结合最好？
113. 在流式输出（Streaming）的支持上，三个框架有什么差异？Server-Sent Events 如何在各框架中实现？
114. 在错误处理和容错机制上，三个框架有什么不同（retry / fallback / human-in-the-loop）？

### 生态与工程化对比
115. LangChain 的生态（社区插件 / Integrations 数量）和 Spring AI 的生态（Spring 全家桶集成）各有什么优势？
116. 在与现有后端系统集成上，Spring AI 相比 LangChain/LangGraph 有什么天然优势？
117. LangSmith 和 Spring AI 的 Observability 在功能上有什么差异？各自适合什么团队？
118. LangGraph Platform 提供的部署方案和 Spring Boot 的原生部署方式有什么区别？
119. Python 生态（LangChain/LangGraph）和 Java 生态（Spring AI）在 LLM 应用开发上各有哪些不可替代的优势？
120. 如果你要在公司内落地一个生产级 Agent 系统，Java 技术栈选 Spring AI、Python 技术栈选 LangGraph，分别应该考虑哪些因素？

---

## 十四、实战与综合场景

121. 用 LangChain LCEL 设计一个带对话历史和 RAG 的问答系统，核心代码结构是怎样的？
122. 用 LangGraph 设计一个能自主纠错的 Code Generation Agent（生成代码 → 执行 → 检查报错 → 修复），State 和节点如何设计？
123. 用 LangGraph 设计一个 Supervisor + 多个专家 Sub-Agent 的协作系统，如何防止 Agent 之间产生循环依赖？
124. 在 Spring AI 中如何实现一个带记忆的多轮客服机器人，并将对话历史持久化到 Redis？
125. LangGraph 的 Human-in-the-Loop 在审批流场景（如 AI 生成 SQL 后需要人工确认再执行）中如何实现？
126. 如何用 LangChain 的 `EnsembleRetriever`（BM25 + 向量）加上 Reranker 构建一个高精度 RAG 系统？
127. 在高并发场景下，LangChain 的 `batch()` 异步调用 LLM 如何做限流和背压控制？
128. LangGraph 的 Checkpointer 用 PostgreSQL 实现时，如何处理高并发下的 State 写入冲突？
129. 如何在 Spring AI 中实现 MCP（Model Context Protocol）Server，让外部 MCP Client 能调用 Spring Bean 中定义的工具？
130. 如果一个复杂 Agent 任务跑了 10 分钟后 LLM 报错中断，LangGraph 和 LangChain 分别能提供什么程度的恢复能力？

