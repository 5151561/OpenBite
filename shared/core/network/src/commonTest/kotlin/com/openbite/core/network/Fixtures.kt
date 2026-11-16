package com.openbite.core.network

/**
 * 一段 Anthropic Messages API 的 SSE 流（按官方文档的事件格式录制），内容是设计稿 13 的菜单。
 * 文本增量故意在 JSON 中间和中文字符中间断开，测试解析是否稳。
 */
internal val CLAUDE_SSE_FIXTURE: String = buildString {
    fun event(name: String, data: String) {
        append("event: ").append(name).append('\n')
        append("data: ").append(data).append("\n\n")
    }
    fun delta(text: String) {
        val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        event("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"$escaped"}}""")
    }
    event("message_start", """{"type":"message_start","message":{"id":"msg_01","type":"message","role":"assistant","content":[],"model":"claude-haiku-5-5","stop_reason":null,"usage":{"input_tokens":1520,"output_tokens":1}}}""")
    event("content_block_start", """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""")
    append(": keep-alive comment\n\n")
    event("ping", """{"type":"ping"}""")
    delta("{\"type\":\"menu\",\"placeName\":null,\"estimatedDishCount\":6,")
    delta("\"currency\":\"JPY\",\"sourceLang\":\"ja\"}\n{\"type\":\"dish\",\"nameOriginal\":\"焼き鳥盛り")
    delta("合わせ\",\"nameTranslated\":\"烤鸡肉串拼盘\",\"category\":\"焼き鳥\",\"priceMinor\":980,\"currency\":\"JPY\",\"allergens\":[\"soy\"]}\n")
    delta("{\"type\":\"dish\",\"nameOriginal\":\"鯖の塩焼き\",\"nameTranslated\":\"盐烤青")
    delta("花鱼\",\"category\":\"焼き物\",\"priceMinor\":780,\"currency\":\"JPY\",\"allergens\":[\"fish\"]}\n")
    delta("{\"type\":\"dish\",\"nameOriginal\":\"生ビール\",\"nameTranslated\":\"生啤\",\"category\":\"飲み物\",\"priceMinor\":550,\"currency\":\"JPY\",\"allergens\":[\"wheat\"]}")
    event("content_block_stop", """{"type":"content_block_stop","index":0}""")
    event("message_delta", """{"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":210}}""")
    event("message_stop", """{"type":"message_stop"}""")
}

internal val CLAUDE_SSE_OVERLOADED: String =
    "event: message_start\ndata: {\"type\":\"message_start\",\"message\":{}}\n\n" +
        "event: error\ndata: {\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}\n\n"
