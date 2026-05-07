import java.util.*;
import java.util.function.Function;

/**
 * ============================================================
 * MCP (Model Context Protocol) Demo
 * ============================================================
 *
 * 核心思想：
 *   MCP 是 Anthropic 2024 年发布的开放标准协议，目标是让 LLM 以统一方式
 *   连接任意外部工具/数据源，彻底解耦"谁实现工具"与"谁使用工具"。
 *
 * 三大角色：
 *   MCP Host   : 运行 LLM 的宿主应用（如 Claude Desktop、IDE 插件）
 *   MCP Client : 内嵌在 Host 里的协议客户端，负责与 Server 通信
 *   MCP Server : 独立进程，暴露 tools/resources/prompts，不依赖任何特定 LLM
 *
 * 通信流程：
 *   Host/Client --> [JSON-RPC 2.0 over stdio/SSE] --> MCP Server
 *                   tools/list  (列举工具)
 *                   tools/call  (调用工具)
 *                   resources/read (读资源)
 *
 * 与 Function Call 的本质区别：
 *   Function Call：工具实现和 LLM 客户端在同一进程，schema 随每次请求传入
 *   MCP          ：工具运行在完全独立的进程/服务，协议标准化，可跨语言复用
 * ============================================================
 */
public class MCPDemo {

    // ===== 模拟 JSON-RPC 2.0 消息格式 =====

    static class JsonRpcRequest {
        String jsonrpc = "2.0";
        String method;
        Map<String, Object> params;
        int id;

        JsonRpcRequest(String method, Map<String, Object> params, int id) {
            this.method = method;
            this.params = params;
            this.id = id;
        }

        @Override
        public String toString() {
            return "{jsonrpc:\"2.0\", method:\"" + method + "\", params:" + params + ", id:" + id + "}";
        }
    }

    static class JsonRpcResponse {
        String jsonrpc = "2.0";
        Object result;
        int id;

        JsonRpcResponse(Object result, int id) {
            this.result = result;
            this.id = id;
        }

        @Override
        public String toString() {
            return "{jsonrpc:\"2.0\", result:" + result + ", id:" + id + "}";
        }
    }

    // ===== MCP Tool 描述结构 =====

    static class MCPTool {
        String name;
        String description;
        Map<String, String> inputSchema;

        MCPTool(String name, String description, Map<String, String> inputSchema) {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
        }

        @Override
        public String toString() {
            return "{name:\"" + name + "\", description:\"" + description + "\", inputSchema:" + inputSchema + "}";
        }
    }

    // ===================================================
    // MCP Server（独立进程/服务）
    // 真实场景：独立 Node.js / Python / Java 进程，通过 stdio 或 HTTP/SSE 暴露
    // 这里用内部类模拟，但在概念上它是完全隔离的独立服务
    // ===================================================

    static class MCPServer {

        private final String serverName = "weather-stock-server";
        private final String serverVersion = "1.0.0";

        // Server 内部注册的工具（与 Host 进程完全无关）
        private final Map<String, Function<Map<String, Object>, String>> handlers = new HashMap<>();
        private final List<MCPTool> tools = new ArrayList<>();

        MCPServer() {
            // 注册工具：get_weather
            Map<String, String> weatherSchema = new LinkedHashMap<>();
            weatherSchema.put("city", "string - 城市名称，如：北京");
            tools.add(new MCPTool("get_weather", "获取指定城市的实时天气", weatherSchema));
            handlers.put("get_weather", args -> {
                String city = (String) args.get("city");
                Map<String, String> db = new HashMap<>();
                db.put("北京", "晴，24°C，东北风3级");
                db.put("上海", "多云，20°C，东风2级");
                db.put("广州", "小雨，28°C，南风1级");
                return db.getOrDefault(city, "暂无 " + city + " 天气数据");
            });

            // 注册工具：get_stock_price
            Map<String, String> stockSchema = new LinkedHashMap<>();
            stockSchema.put("ticker", "string - 股票代码，如：AAPL");
            tools.add(new MCPTool("get_stock_price", "查询指定股票的当前价格", stockSchema));
            handlers.put("get_stock_price", args -> {
                String ticker = (String) args.get("ticker");
                Map<String, Double> market = new HashMap<>();
                market.put("AAPL", 178.5);
                market.put("GOOG", 175.3);
                market.put("BIDU", 110.2);
                return market.containsKey(ticker)
                        ? ticker + " 当前价格: $" + market.get(ticker)
                        : "未找到股票代码: " + ticker;
            });

            // 注册工具：read_file（展示 MCP 的 resource 能力，Function Call 很难统一处理）
            Map<String, String> fileSchema = new LinkedHashMap<>();
            fileSchema.put("path", "string - 文件路径");
            tools.add(new MCPTool("read_file", "读取指定路径的文件内容（模拟）", fileSchema));
            handlers.put("read_file", args -> {
                String path = (String) args.get("path");
                return "[MCP Server 读取文件] " + path + " 内容: Hello from MCP Resource!";
            });
        }

        /**
         * 处理 JSON-RPC 请求（模拟 MCP Server 的消息处理循环）
         * 真实场景：通过 stdin/stdout 或 HTTP+SSE 收发 JSON-RPC 消息
         */
        JsonRpcResponse handle(JsonRpcRequest req) {
            System.out.println("    [MCP Server 收到请求] " + req);

            switch (req.method) {

                // initialize：握手，Client 告知 Server 自己的能力
                case "initialize": {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("protocolVersion", "2024-11-05");
                    result.put("serverName", serverName);
                    result.put("serverVersion", serverVersion);
                    result.put("capabilities", Collections.singletonMap("tools", true));
                    return new JsonRpcResponse(result, req.id);
                }

                // tools/list：列举 Server 提供的所有工具
                case "tools/list": {
                    List<String> toolList = new ArrayList<>();
                    for (MCPTool t : tools) toolList.add(t.toString());
                    return new JsonRpcResponse(toolList, req.id);
                }

                // tools/call：调用具体工具
                case "tools/call": {
                    String toolName = (String) req.params.get("name");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> toolArgs = (Map<String, Object>) req.params.getOrDefault("arguments", new HashMap<>());
                    Function<Map<String, Object>, String> handler = handlers.get(toolName);
                    if (handler == null) {
                        return new JsonRpcResponse("Error: unknown tool " + toolName, req.id);
                    }
                    String toolResult = handler.apply(toolArgs);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("content", Collections.singletonList(
                            Collections.singletonMap("text", toolResult)));
                    return new JsonRpcResponse(result, req.id);
                }

                default:
                    return new JsonRpcResponse("Error: unknown method " + req.method, req.id);
            }
        }
    }

    // ===================================================
    // MCP Client（内嵌在 Host 应用中）
    // 负责：1) 与 Server 握手  2) 获取工具列表  3) 调用工具
    // ===================================================

    static class MCPClient {

        private final MCPServer server; // 真实场景：通过 Process.stdin/stdout 或 HTTP 连接
        private int requestId = 0;

        MCPClient(MCPServer server) {
            this.server = server;
        }

        /** 第一步：初始化握手 */
        void initialize() {
            System.out.println("  [MCP Client] 发送 initialize 请求...");
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("protocolVersion", "2024-11-05");
            params.put("clientName", "demo-host");
            JsonRpcRequest req = new JsonRpcRequest("initialize", params, ++requestId);
            JsonRpcResponse resp = server.handle(req);
            System.out.println("  [MCP Client] 握手成功，Server 信息: " + resp.result);
        }

        /** 第二步：获取 Server 暴露的工具列表 */
        List<String> listTools() {
            System.out.println("  [MCP Client] 请求工具列表 (tools/list)...");
            JsonRpcRequest req = new JsonRpcRequest("tools/list", new HashMap<>(), ++requestId);
            JsonRpcResponse resp = server.handle(req);
            @SuppressWarnings("unchecked")
            List<String> toolList = (List<String>) resp.result;
            return toolList;
        }

        /** 第三步：调用工具 */
        String callTool(String toolName, Map<String, Object> args) {
            System.out.println("  [MCP Client] 调用工具 (tools/call): " + toolName + " args=" + args);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("name", toolName);
            params.put("arguments", args);
            JsonRpcRequest req = new JsonRpcRequest("tools/call", params, ++requestId);
            JsonRpcResponse resp = server.handle(req);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) resp.result;
            @SuppressWarnings("unchecked")
            List<Map<String, String>> content = (List<Map<String, String>>) result.get("content");
            return content.get(0).get("text");
        }
    }

    // ===================================================
    // MCP Host：运行 LLM，协调 Client 和用户
    // ===================================================

    static class MCPHost {

        private final MCPClient client;

        MCPHost(MCPClient client) {
            this.client = client;
        }

        /** 模拟 LLM 决定调用哪个 MCP 工具（真实场景是 LLM 推理） */
        void handleQuery(String userQuery) {
            System.out.println("\n--------------------------------------------------");
            System.out.println("  [Host/LLM] 收到用户问题: " + userQuery);
            System.out.println("  [Host/LLM] LLM 推理，决定调用 MCP Server 的工具...");

            String toolName;
            Map<String, Object> args = new LinkedHashMap<>();

            if (userQuery.contains("天气")) {
                toolName = "get_weather";
                String city = userQuery.contains("上海") ? "上海" : userQuery.contains("广州") ? "广州" : "北京";
                args.put("city", city);
            } else if (userQuery.contains("股票") || userQuery.contains("股价")) {
                toolName = "get_stock_price";
                String ticker = userQuery.contains("苹果") ? "AAPL" : userQuery.contains("谷歌") ? "GOOG" : "BIDU";
                args.put("ticker", ticker);
            } else if (userQuery.contains("文件")) {
                toolName = "read_file";
                args.put("path", "/data/report.txt");
            } else {
                System.out.println("  [Host/LLM] 无需工具，LLM 直接回答");
                return;
            }

            // MCP Client 通过协议调用 Server 上的工具
            String result = client.callTool(toolName, args);
            System.out.println("  [Host/LLM] 工具返回结果: " + result);
            System.out.println("  [Host/LLM] LLM 最终回答: " + result);
        }
    }

    // ===== 演示入口 =====

    public static void demo() {
        System.out.println("==========================================================");
        System.out.println("                 MCP Demo");
        System.out.println("==========================================================");
        System.out.println("架构: 用户 -> MCP Host(LLM) -> MCP Client -> [协议] -> MCP Server(独立进程)");
        System.out.println("特点: 工具运行在独立进程/服务，通过 JSON-RPC 2.0 标准协议通信，彻底解耦\n");

        // 启动 MCP Server（真实场景是单独启动一个进程）
        System.out.println("[启动 MCP Server 独立进程...]");
        MCPServer server = new MCPServer();

        // MCP Client 连接 Server
        MCPClient client = new MCPClient(server);

        // Step1: 握手
        client.initialize();

        // Step2: 获取工具列表（Host 把工具信息注入 LLM 上下文）
        List<String> tools = client.listTools();
        System.out.println("  [MCP Client] 获取到工具列表，注入 LLM 上下文:");
        for (String t : tools) {
            System.out.println("    - " + t);
        }

        // Step3: Host + LLM 处理用户请求
        MCPHost host = new MCPHost(client);
        String[] queries = {"北京今天天气？", "帮我查苹果公司股价", "帮我读取一个文件", "你好，介绍一下你自己"};
        for (String q : queries) {
            host.handleQuery(q);
        }
    }
}

