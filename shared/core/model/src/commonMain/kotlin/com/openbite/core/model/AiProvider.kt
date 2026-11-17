package com.openbite.core.model

import kotlinx.serialization.Serializable

/** 两种请求格式：Anthropic Messages，和 OpenAI Chat Completions（Gemini、DeepSeek、自定义都兼容这个）。 */
enum class AiWire { ANTHROPIC, OPENAI }

/**
 * 能接的 AI 服务商。这里只写服务商和接口地址，不写任何模型名：模型列表连上后从服务商拉。
 * [defaultBaseUrl] 为 null 的（自定义）要用户自己填地址。
 */
enum class AiProvider(val defaultBaseUrl: String?, val wire: AiWire) {
    ANTHROPIC("https://api.anthropic.com/v1", AiWire.ANTHROPIC),
    OPENAI("https://api.openai.com/v1", AiWire.OPENAI),
    GEMINI("https://generativelanguage.googleapis.com/v1beta/openai", AiWire.OPENAI),
    DEEPSEEK("https://api.deepseek.com", AiWire.OPENAI),
    CUSTOM(null, AiWire.OPENAI),
    ;

    /** SecretStore 里存这家 Key 的键名。Anthropic 沿用老键名，升级后不用重填。 */
    val secretKey: String get() = if (this == ANTHROPIC) "anthropic_api_key" else "${name.lowercase()}_api_key"
}

/** 选中的模型：哪一家的哪个模型 id（原样用服务商返回的 id）。 */
data class ModelRef(val provider: AiProvider, val modelId: String) {
    /** 存进设置的样子：“OPENAI:gpt-x”。模型 id 里有冒号也没关系，只按第一个冒号拆。 */
    fun encode(): String = "${provider.name}:$modelId"

    companion object {
        fun decode(value: String): ModelRef? {
            val name = value.substringBefore(':', missingDelimiterValue = "")
            val id = value.substringAfter(':', missingDelimiterValue = "")
            val provider = AiProvider.entries.firstOrNull { it.name == name } ?: return null
            return if (id.isBlank()) null else ModelRef(provider, id)
        }
    }
}

/** 服务商列出来的一个模型。[createdAtSeconds] 有就用来排序（新的在前）。 */
@Serializable
data class ProviderModel(val id: String, val displayName: String? = null, val createdAtSeconds: Long? = null)

/** 一个模型能不能看图：服务商不告诉我们，选的时候发张小图试出来。 */
enum class VisionSupport { YES, NO, UNKNOWN }

/** 某一家上次拉到的模型列表。 */
@Serializable
data class ModelCatalog(val models: List<ProviderModel>, val fetchedAtMillis: Long)

/**
 * 识别模型相关的普通设置（Key 不在这里，在 SecretStore）。整块存成一个 JSON，改的时候整块替换。
 * Map 的键是 [AiProvider.name] 或 [ModelRef.encode]，这样以后加服务商、老数据也读得出来。
 */
@Serializable
data class AiSettings(
    /** 选中的模型，[ModelRef.encode] 的样子。 */
    val selected: String? = null,
    /** 演示模式：扫菜单、读小票都用示例，不联网。默认关。 */
    val demoMode: Boolean = false,
    /** 用户改过的接口地址（自定义必填，其他几家可以填代理地址）。 */
    val baseUrls: Map<String, String> = emptyMap(),
    val catalogs: Map<String, ModelCatalog> = emptyMap(),
    /** 试过图的结果：能看图 true，不能 false，没试过不在里面。 */
    val visionChecks: Map<String, Boolean> = emptyMap(),
) {
    val selectedModel: ModelRef? get() = selected?.let(ModelRef::decode)

    fun baseUrlOf(provider: AiProvider): String? = baseUrls[provider.name] ?: provider.defaultBaseUrl

    fun catalogOf(provider: AiProvider): ModelCatalog? = catalogs[provider.name]

    fun visionOf(ref: ModelRef): VisionSupport = when (visionChecks[ref.encode()]) {
        true -> VisionSupport.YES
        false -> VisionSupport.NO
        null -> VisionSupport.UNKNOWN
    }

    /** 断开一家：忘掉它的地址、模型列表、试图结果；选的是它家的模型也一起清掉。 */
    fun forget(provider: AiProvider): AiSettings = copy(
        selected = selected?.takeIf { selectedModel?.provider != provider },
        baseUrls = baseUrls - provider.name,
        catalogs = catalogs - provider.name,
        visionChecks = visionChecks.filterKeys { ModelRef.decode(it)?.provider != provider },
    )
}

/** 只允许 https，Key 不能明文发出去。去掉结尾的 /。不合格返回 null。 */
fun normalizeBaseUrl(raw: String): String? {
    val url = raw.trim().trimEnd('/')
    if (!url.startsWith("https://", ignoreCase = true)) return null
    val host = url.substring("https://".length).substringBefore('/')
    if (host.isEmpty() || host.any { it.isWhitespace() }) return null
    return url
}
