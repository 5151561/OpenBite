package com.openbite.core.model

/** 用户在设置里选的识别模型。真实的 API 模型 id 在 :shared:core:network 的 AnthropicModels 里。 */
enum class VisionModelChoice(val needsApiKey: Boolean) {
    CLAUDE_HAIKU(needsApiKey = true),
    CLAUDE_SONNET(needsApiKey = true),

    /** 本地流式吐出打包好的示例菜单，不调用模型、不联网。 */
    SAMPLE(needsApiKey = false),
    ;

    companion object {
        val DEFAULT = CLAUDE_HAIKU
    }
}

/** 没填 Key 时，需要 Key 的模型自动退回示例菜单。 */
fun effectiveVisionModel(choice: VisionModelChoice, hasApiKey: Boolean): VisionModelChoice =
    if (choice.needsApiKey && !hasApiKey) VisionModelChoice.SAMPLE else choice

/** 识别失败的原因，界面按它给出提示和下一步。 */
enum class ScanError {
    /** 选了真实模型但没有 Key。 */
    NO_API_KEY,

    /** 401 / 403：Key 无效或没权限。 */
    INVALID_API_KEY,

    /** 429：请求太频繁或额度用完。 */
    RATE_LIMITED,

    /** 5xx / 529：模型服务暂时不可用。 */
    SERVICE_UNAVAILABLE,

    /** 连接或读取超时。 */
    TIMEOUT,

    /** 没有网络或连不上。 */
    NETWORK,

    /** 模型输出没写完（连接中途断了，或写到长度上限），结果不完整，没有保存。 */
    INCOMPLETE,

    /** 照片读不出来。 */
    IMAGE_UNREADABLE,

    /** 模型没认出任何菜（照片不是菜单，或太模糊）。 */
    NO_DISHES,

    /** 要补拍的店铺已经被删掉。 */
    PLACE_MISSING,

    UNKNOWN,
    ;

    /** 换成示例菜单就能继续体验。 */
    val sampleHelps: Boolean get() = this == NO_API_KEY || this == INVALID_API_KEY || this == RATE_LIMITED

    val retryable: Boolean get() = this in setOf(RATE_LIMITED, SERVICE_UNAVAILABLE, TIMEOUT, NETWORK, INCOMPLETE, NO_DISHES, UNKNOWN)
}

class ScanException(val error: ScanError, cause: Throwable? = null) : Exception(error.name, cause)

/** 设置页显示 Key 的方式：“sk-ant-…abcd”。不足 12 位的只显示圆点。 */
fun maskApiKey(key: String): String {
    val trimmed = key.trim()
    if (trimmed.length < 12) return "•".repeat(8)
    // Anthropic 的 Key 形如 sk-ant-api03-…，保留前两段方便认出是哪家的。
    val parts = trimmed.split('-')
    val prefix = if (parts.size >= 3) "${parts[0]}-${parts[1]}-" else ""
    return prefix + "••••" + trimmed.takeLast(4)
}
