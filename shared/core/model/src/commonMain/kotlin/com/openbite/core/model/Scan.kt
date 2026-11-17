package com.openbite.core.model

/** 这次识别用的是什么：演示模式（不联网），或者某一家的某个模型。 */
sealed interface ModelUse {
    data object Demo : ModelUse
    data class Real(val ref: ModelRef) : ModelUse
}

/** 识别失败的原因，界面按它给出提示和下一步。 */
enum class ScanError {
    /** 演示模式关着，又还没选识别模型（或选的那家 Key 被清掉了）。 */
    NO_MODEL,

    /** 401 / 403：Key 无效或没权限。 */
    INVALID_API_KEY,

    /** 选的模型这家已经没有了（404 / model_not_found）。 */
    MODEL_GONE,

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

    /** 要去设置里连一家、换 Key 或换模型才能好。 */
    val needsSettings: Boolean get() = this == NO_MODEL || this == INVALID_API_KEY || this == MODEL_GONE

    val retryable: Boolean get() = this in setOf(RATE_LIMITED, SERVICE_UNAVAILABLE, TIMEOUT, NETWORK, INCOMPLETE, NO_DISHES, UNKNOWN)
}

class ScanException(val error: ScanError, cause: Throwable? = null) : Exception(error.name, cause)

/** 设置页显示 Key 的方式：“sk-ant-…abcd”。不足 12 位的只显示圆点。 */
fun maskApiKey(key: String): String {
    val trimmed = key.trim()
    if (trimmed.length < 12) return "•".repeat(8)
    // Key 形如 sk-ant-api03-… / sk-proj-…，保留前两段方便认出是哪家的。
    val parts = trimmed.split('-')
    val prefix = if (parts.size >= 3) "${parts[0]}-${parts[1]}-" else ""
    return prefix + "••••" + trimmed.takeLast(4)
}
