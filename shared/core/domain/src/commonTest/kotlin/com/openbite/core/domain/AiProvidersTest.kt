package com.openbite.core.domain

import com.openbite.core.data.platform.SecretKeys
import com.openbite.core.model.AiProvider
import com.openbite.core.model.AiSettings
import com.openbite.core.model.ModelRef
import com.openbite.core.model.ModelUse
import com.openbite.core.model.VisionSupport
import com.openbite.core.network.AiProviderClient
import com.openbite.core.network.SampleVisionModel
import com.openbite.core.network.createVisionHttpClient
import com.openbite.core.testing.FakePreferences
import com.openbite.core.testing.FakeSecrets
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiProvidersTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val sse = headersOf(HttpHeaders.ContentType, "text/event-stream")
    private val now = NowProvider { 42L }

    /** 假服务商：/models 返回两个模型；聊天接口对 [visionModels] 里的回答 Red，别的 400。Key 不是 good 一律 401。 */
    private class FakeServer(val visionModels: Set<String> = setOf("seeing")) {
        val requests = mutableListOf<String>()
    }

    private fun client(server: FakeServer) = AiProviderClient(
        createVisionHttpClient(
            MockEngine { req ->
                val url = req.url.toString()
                server.requests += url
                val auth = req.headers[HttpHeaders.Authorization] ?: req.headers["x-api-key"]?.let { "Bearer $it" }
                if (auth != "Bearer good") return@MockEngine respond(ByteReadChannel("{}"), HttpStatusCode.Unauthorized, json)
                when {
                    url.endsWith("/models") -> respond(
                        ByteReadChannel("""{"data":[{"id":"seeing","created":2},{"id":"blind","created":1}]}"""),
                        HttpStatusCode.OK,
                        json,
                    )
                    url.endsWith("/chat/completions") -> {
                        val body = (req.body as io.ktor.http.content.TextContent).text
                        if (server.visionModels.any { "\"model\":\"$it\"" in body }) {
                            respond(
                                ByteReadChannel(
                                    "data: {\"choices\":[{\"delta\":{\"content\":\"Red\"},\"finish_reason\":null}]}\n\n" +
                                        "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n",
                                ),
                                HttpStatusCode.OK,
                                sse,
                            )
                        } else {
                            respond(ByteReadChannel("""{"error":{"code":"invalid_value"}}"""), HttpStatusCode.BadRequest, json)
                        }
                    }
                    else -> respond(ByteReadChannel("{}"), HttpStatusCode.NotFound, json)
                }
            },
        ),
    )

    @Test
    fun connectSavesKeyAndModelsOnlyWhenListWorks() = runTest {
        val prefs = FakePreferences()
        val secrets = FakeSecrets()
        val connect = ConnectProviderUseCase(prefs, secrets, client(FakeServer()), now)

        assertEquals(AiActionResult.KEY_MALFORMED, connect(AiProvider.OPENAI, " ", null))
        assertEquals(AiActionResult.KEY_MALFORMED, connect(AiProvider.OPENAI, "sk bad", null))
        assertEquals(AiActionResult.KEY_REJECTED, connect(AiProvider.OPENAI, "wrong", null))
        assertNull(secrets.get(SecretKeys.apiKey(AiProvider.OPENAI)))

        assertEquals(AiActionResult.OK, connect(AiProvider.OPENAI, "  good \n", null))
        assertEquals("good", secrets.get(SecretKeys.apiKey(AiProvider.OPENAI)))
        val catalog = prefs.ai.value.catalogOf(AiProvider.OPENAI)!!
        assertEquals(listOf("seeing", "blind"), catalog.models.map { it.id })
        assertEquals(42L, catalog.fetchedAtMillis)

        val overview = ObserveAiOverviewUseCase(prefs, secrets)().first()
        assertEquals(1, overview.connectedCount)
        assertTrue(overview.status(AiProvider.OPENAI).connected)
        assertFalse(overview.status(AiProvider.CUSTOM).connected)
    }

    @Test
    fun customNeedsHttpsUrl() = runTest {
        val prefs = FakePreferences()
        val server = FakeServer()
        val connect = ConnectProviderUseCase(prefs, FakeSecrets(), client(server), now)
        assertEquals(AiActionResult.URL_INVALID, connect(AiProvider.CUSTOM, "good", null))
        assertEquals(AiActionResult.URL_INVALID, connect(AiProvider.CUSTOM, "good", "http://lan.example/v1"))
        assertTrue(server.requests.isEmpty(), "地址不对时不该把 Key 发出去")
        assertEquals(AiActionResult.OK, connect(AiProvider.CUSTOM, "good", "https://my.example/v1/"))
        assertEquals("https://my.example/v1/models", server.requests.single())
        assertEquals("https://my.example/v1", prefs.ai.value.baseUrlOf(AiProvider.CUSTOM))
    }

    @Test
    fun selectProbesOnceAndRefusesBlindModels() = runTest {
        val prefs = FakePreferences()
        val secrets = FakeSecrets(mapOf(AiProvider.OPENAI to "good"))
        val server = FakeServer()
        val select = SelectModelUseCase(prefs, secrets, client(server))

        assertEquals(AiActionResult.NO_VISION, select(ModelRef(AiProvider.OPENAI, "blind")))
        assertNull(prefs.ai.value.selected)
        assertEquals(VisionSupport.NO, prefs.ai.value.visionOf(ModelRef(AiProvider.OPENAI, "blind")))

        assertEquals(AiActionResult.OK, select(ModelRef(AiProvider.OPENAI, "seeing")))
        assertEquals(ModelRef(AiProvider.OPENAI, "seeing"), prefs.ai.value.selectedModel)
        val probes = server.requests.size

        // 已经试过的不再发请求。
        assertEquals(AiActionResult.NO_VISION, select(ModelRef(AiProvider.OPENAI, "blind")))
        assertEquals(AiActionResult.OK, select(ModelRef(AiProvider.OPENAI, "seeing")))
        assertEquals(probes, server.requests.size)
    }

    @Test
    fun badKeyDuringProbeIsNotRememberedAsBlind() = runTest {
        val prefs = FakePreferences()
        val select = SelectModelUseCase(prefs, FakeSecrets(mapOf(AiProvider.OPENAI to "expired")), client(FakeServer()))
        assertEquals(AiActionResult.KEY_REJECTED, select(ModelRef(AiProvider.OPENAI, "seeing")))
        assertEquals(VisionSupport.UNKNOWN, prefs.ai.value.visionOf(ModelRef(AiProvider.OPENAI, "seeing")))
    }

    @Test
    fun disconnectForgetsProviderAndSelection() = runTest {
        val ref = ModelRef(AiProvider.OPENAI, "seeing")
        val other = ModelRef(AiProvider.DEEPSEEK, "x")
        val prefs = FakePreferences(
            AiSettings(
                selected = ref.encode(),
                baseUrls = mapOf("OPENAI" to "https://proxy.example/v1"),
                visionChecks = mapOf(ref.encode() to true, other.encode() to true),
            ),
        )
        val secrets = FakeSecrets(mapOf(AiProvider.OPENAI to "good", AiProvider.DEEPSEEK to "good"))
        DisconnectProviderUseCase(prefs, secrets)(AiProvider.OPENAI)
        assertNull(secrets.get(SecretKeys.apiKey(AiProvider.OPENAI)))
        assertEquals("good", secrets.get(SecretKeys.apiKey(AiProvider.DEEPSEEK)))
        assertNull(prefs.ai.value.selected)
        assertEquals(AiProvider.OPENAI.defaultBaseUrl, prefs.ai.value.baseUrlOf(AiProvider.OPENAI))
        assertEquals(setOf(other.encode()), prefs.ai.value.visionChecks.keys)
    }

    @Test
    fun demoModeWinsOverSelectedModel() = runTest {
        val prefs = FakePreferences(AiSettings(selected = "OPENAI:seeing"))
        val secrets = FakeSecrets(mapOf(AiProvider.OPENAI to "good"))
        val active = ActiveVisionModel(prefs, secrets, client(FakeServer()))
        val real = active.resolve()
        assertEquals(ModelUse.Real(ModelRef(AiProvider.OPENAI, "seeing")), real.use)
        assertEquals("seeing", real.model.id)
        assertEquals("good", real.apiKey)

        SetDemoModeUseCase(prefs)(true)
        val demo = active.resolve()
        assertIs<ModelUse.Demo>(demo.use)
        assertEquals(SampleVisionModel.ID, demo.model.id)
        assertNull(demo.apiKey)
    }

    @Test
    fun modelRefRoundTrips() {
        val ref = ModelRef(AiProvider.CUSTOM, "org/model:latest")
        assertEquals(ref, ModelRef.decode(ref.encode()))
        assertNull(ModelRef.decode("NOPE:x"))
        assertNull(ModelRef.decode("OPENAI:"))
    }
}
