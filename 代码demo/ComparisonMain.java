/**
 * ============================================================
 * Function Call vs MCP 对比演示主入口
 * ============================================================
 *
 * 编译命令（在 代码demo 目录下执行）：
 *   javac FunctionCallDemo.java MCPDemo.java ComparisonMain.java
 *
 * 运行命令：
 *   java ComparisonMain
 *
 * ============================================================
 *
 * 核心区别总结：
 *
 *  维度              Function Call                 MCP (Model Context Protocol)
 * ─────────────────────────────────────────────────────────────────────────────
 *  工具实现位置      与 LLM 客户端同进程            独立进程/服务（可跨语言）
 *  工具发现方式      开发者硬编码写入请求 body       运行时通过 tools/list 协议动态发现
 *  通信协议          无标准，各 LLM 厂商自定义        JSON-RPC 2.0（Anthropic 开放标准）
 *  解耦程度          低（工具换了得改调用方代码）     高（Server/Client 独立演进）
 *  跨 LLM 复用       不能（每家 API 格式不同）        可以（MCP Server 对所有 LLM 通用）
 *  适用场景          简单、快速、工具数量少           复杂工具链、多智能体、企业级集成
 *  典型产品          OpenAI Function Calling         Claude Desktop、Cursor MCP 插件
 *
 * ============================================================
 */
public class ComparisonMain {

    public static void main(String[] args) {

        System.out.println("\n");
        System.out.println("########################################################");
        System.out.println("#                                                      #");
        System.out.println("#      Function Call  vs  MCP  对比演示                #");
        System.out.println("#                                                      #");
        System.out.println("########################################################");

        // -------- Part 1: Function Call --------
        System.out.println("\n\n");
        System.out.println("★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★");
        System.out.println("  PART 1 — Function Call");
        System.out.println("  工具实现与 LLM 客户端在同一进程，LLM 输出 JSON 指令");
        System.out.println("★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★");
        FunctionCallDemo.demo();

        // -------- Part 2: MCP --------
        System.out.println("\n\n");
        System.out.println("★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★");
        System.out.println("  PART 2 — MCP (Model Context Protocol)");
        System.out.println("  工具运行在独立进程，通过 JSON-RPC 2.0 协议通信");
        System.out.println("★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★");
        MCPDemo.demo();

        // -------- Part 3: 差异对比 --------
        System.out.println("\n\n");
        System.out.println("★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★");
        System.out.println("  PART 3 — 关键差异对比");
        System.out.println("★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★");
        printComparison();
    }

    static void printComparison() {
        System.out.println();
        String fmt = "  %-18s  %-28s  %-35s%n";
        System.out.printf(fmt, "维度", "Function Call", "MCP");
        System.out.println("  -------------------------------------------------------------------------------------");
        System.out.printf(fmt, "工具实现位置",   "与 LLM 客户端同一进程",      "独立进程/服务（可跨语言跨机器）");
        System.out.printf(fmt, "工具发现方式",   "开发者写入请求 body",         "运行时 tools/list 协议动态发现");
        System.out.printf(fmt, "通信协议",       "各厂商自定义 JSON 格式",      "JSON-RPC 2.0 开放标准");
        System.out.printf(fmt, "解耦程度",       "低（紧耦合）",               "高（Server/Client 独立演进）");
        System.out.printf(fmt, "跨 LLM 复用",   "不能（每家 API 格式不同）",   "能（MCP Server 对所有 LLM 通用）");
        System.out.printf(fmt, "安全边界",       "工具与 Host 共享进程权限",    "Server 独立进程，权限可隔离");
        System.out.printf(fmt, "适用场景",       "简单快速、工具数量少",        "复杂工具链、企业级多智能体集成");
        System.out.printf(fmt, "典型产品",       "OpenAI Function Calling",    "Claude Desktop、Cursor MCP 插件");
        System.out.println();
        System.out.println("  结论：");
        System.out.println("  Function Call 适合 快速开发、工具少、单一 LLM 场景");
        System.out.println("  MCP 适合 工具复用、多智能体、生产级跨模型工具生态构建");
        System.out.println();
    }
}

