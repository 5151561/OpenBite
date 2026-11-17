package com.openbite.core.network

import com.openbite.core.model.AiProvider
import com.openbite.core.model.AiWire
import com.openbite.core.model.ProviderModel
import io.ktor.client.HttpClient

/** 连一家要的东西：哪家、接口地址（已经规范过，结尾不带 /）。Key 每次调用时传。 */
data class ProviderEndpoint(val provider: AiProvider, val baseUrl: String)

/** 按服务商造出识别模型、拉模型列表。演示模式用 [sample]。 */
class AiProviderClient(private val client: HttpClient) {
    val sample: VisionModel = SampleVisionModel()

    fun modelFor(endpoint: ProviderEndpoint, modelId: String): VisionModel = when (endpoint.provider.wire) {
        AiWire.ANTHROPIC -> ClaudeVisionModel(client, modelId, endpoint.baseUrl)
        AiWire.OPENAI -> OpenAiCompatVisionModel(client, modelId, endpoint.baseUrl)
    }

    /** 这家现在有的全部模型。Key 错抛 [VisionException.Unauthorized]，其他错误同样抛 [VisionException]。 */
    suspend fun listModels(endpoint: ProviderEndpoint, apiKey: String): List<ProviderModel> = when (endpoint.provider.wire) {
        AiWire.ANTHROPIC -> listAnthropicModels(client, endpoint.baseUrl, apiKey)
        AiWire.OPENAI -> listOpenAiModels(
            client,
            endpoint.baseUrl,
            apiKey,
            stripPrefix = if (endpoint.provider == AiProvider.GEMINI) "models/" else null,
        )
    }
}
