import java.util.*;

/**
 * ============================================================
 * Function Call Demo
 * ============================================================
 *
 * 核心思想：
 *   1. 开发者把工具函数的 schema（名称、参数、描述）以接口参数形式传给 LLM
 *   2. LLM 输出结构化 JSON，描述想调用哪个函数、传什么参数
 *   3. 宿主程序解析 JSON，在本地执行对应函数，把结果回填给 LLM
 *   4. LLM 继续生成最终自然语言答案
 *
 * 与 MCP 的本质区别：
 *   Function Call：工具实现在宿主进程里，LLM 只给出 JSON 调用指令
 *   MCP          ：工具在独立进程（Server），通过标准协议通信，完全解耦
 * ============================================================
 */
public class FunctionCallDemo {

    // ===== 1. 工具函数本体（真实场景会调外部 API / DB）=====

    static String getWeather(String city) {
        Map<String, String> db = new HashMap<>();
        db.put("北京", "晴，24°C，东北风3级");
        db.put("上海", "多云，20°C，东风2级");
        db.put("广州", "小雨，28°C，南风1级");
        return db.getOrDefault(city, "暂无 " + city + " 天气数据");
    }

    static String getStockPrice(String ticker) {
        Map<String, Double> market = new HashMap<>();
        market.put("AAPL", 178.5);
        market.put("GOOG", 175.3);
        market.put("BIDU", 110.2);
        return market.containsKey(ticker)
                ? ticker + " 当前价格: $" + market.get(ticker)
                : "未找到股票代码: " + ticker;
    }

    // ===== 2. 工具 Schema 注册表（以参数形式传给 LLM API）=====

    static final List<Map<String, Object>> TOOL_SCHEMAS = new ArrayList<>();

    static {
        Map<String, Object> t1 = new LinkedHashMap<>();
        t1.put("name", "get_weather");
        t1.put("description", "获取指定城市的实时天气");
        t1.put("param:city", "字符串，城市名称，如：北京");
        TOOL_SCHEMAS.add(t1);

        Map<String, Object> t2 = new LinkedHashMap<>();
        t2.put("name", "get_stock_price");
        t2.put("description", "查询指定股票的当前价格");
        t2.put("param:ticker", "字符串，股票代码，如：AAPL");
        TOOL_SCHEMAS.add(t2);
    }

    // ===== 3. 模拟 LLM 返回的 Function Call 决策 =====
    // 真实场景：OpenAI API 返回 finish_reason="tool_calls" + tool_calls JSON

    static class ToolCall {
        String name;
        Map<String, String> args;

        ToolCall(String name, Map<String, String> args) {
            this.name = name;
            this.args = args;
        }
    }

    static ToolCall simulateLLMDecision(String query) {
        System.out.println("  [Step1] 用户提问(+工具Schema) -> LLM: " + query);
        System.out.println("  [Step2] LLM 分析，输出 Function Call JSON...");

        if (query.contains("天气")) {
            String city = query.contains("上海") ? "上海" : query.contains("广州") ? "广州" : "北京";
            System.out.println("  [Step2] => {\"name\":\"get_weather\",\"args\":{\"city\":\"" + city + "\"}}");
            return new ToolCall("get_weather", Collections.singletonMap("city", city));
        } else if (query.contains("股票") || query.contains("股价")) {
            String ticker = query.contains("苹果") ? "AAPL" : query.contains("谷歌") ? "GOOG" : "BIDU";
            System.out.println("  [Step2] => {\"name\":\"get_stock_price\",\"args\":{\"ticker\":\"" + ticker + "\"}}");
            return new ToolCall("get_stock_price", Collections.singletonMap("ticker", ticker));
        }

        System.out.println("  [Step2] => LLM 无需调用工具，直接回答");
        return null;
    }

    // ===== 4. 宿主程序本地执行工具（关键：与 LLM 客户端在同一进程）=====

    static String executeLocally(ToolCall call) {
        if (call == null) return "(LLM 直接回答，未调用工具)";
        System.out.println("  [Step3] 宿主程序本地执行工具函数: " + call.name);
        switch (call.name) {
            case "get_weather":     return getWeather(call.args.get("city"));
            case "get_stock_price": return getStockPrice(call.args.get("ticker"));
            default:                return "未知函数: " + call.name;
        }
    }

    // ===== 5. 演示入口 =====

    public static void demo() {
        System.out.println("==========================================================");
        System.out.println("              Function Call Demo");
        System.out.println("==========================================================");
        System.out.println("架构: 用户 -> 宿主程序(含工具实现) <-> LLM API");
        System.out.println("特点: 工具定义+实现在同一进程，LLM 输出 JSON 指令由宿主执行\n");
        System.out.println("已注册工具 Schema（随请求发送给 LLM）:");
        for (Map<String, Object> s : TOOL_SCHEMAS) {
            System.out.println("  * " + s.get("name") + " : " + s.get("description"));
        }

        String[] queries = {"北京今天天气？", "帮我查苹果公司股价", "你好，介绍一下你自己"};
        for (String q : queries) {
            System.out.println("\n--------------------------------------------------");
            ToolCall decision = simulateLLMDecision(q);
            String result = executeLocally(decision);
            System.out.println("  [Step4] 工具结果回填 LLM: " + result);
            System.out.println("  [Step5] LLM 最终回答: " + result);
        }
    }
}

