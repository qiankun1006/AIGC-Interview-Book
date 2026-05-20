Tool 设计原则（面试角度）
1. 原子性（Atomicity）
   一个 Tool 只做一件有清晰输入输出的事。反例：这个 GodotGamePlannerTool 既做 NLU 解析又做计划生成。
2. 幂等性（Idempotency）
   相同输入永远产生相同输出，没有副作用。读类 Tool 天然幂等，写类 Tool 要刻意设计（比如 CreateScene 要能判断文件已存在）。
3. 输出边界清晰（Bounded Output）
   Tool 返回值要有明确的大小上限，防止单次输出占满上下文窗口。你们代码里 AgentToolExecutor 的 MAX_TOOL_RESULT_CHARS = 40_000 就是这个思路。
4. 错误信息面向 AI（AI-readable Errors）
   返回 "Error: file not found: res://scenes/player.tscn" 而不是 Java 异常堆栈——AI 要能根据错误信息决定下一步。
5. 最小权限（Least Privilege）
   只读 Tool（GetSceneStructure）不应该有写文件能力；写 Tool 的作用范围应该限定在 workDir 以内。你们的 PermissionRuleEngine 就是在系统层面强制这一点。
6. 对 AI 透明（Schema First）
   inputSchema() 是 Tool 的契约，要比代码注释更重要。AI 完全依赖 schema 决定如何调用，描述含糊的 schema 会导致 AI 传错参数。
7. Tool 不应该做 AI 的工作
   把"理解用户意图"留给 AI，把"确定性执行"留给 Tool。这个 //todo 这匹配方式太粗糙了 的根本原因就是 Tool 越俎代庖地去做了 NLU。