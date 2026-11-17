package com.openbite.core.domain

import com.openbite.core.model.AiProvider
import com.openbite.core.model.AiSettings
import com.openbite.core.model.ModelRef
import com.openbite.core.network.AiProviderClient
import com.openbite.core.network.createVisionHttpClient
import com.openbite.core.testing.FakePreferences
import com.openbite.core.testing.FakeSecrets
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReceiptLanguagesTest {
    /** 瑞典语：不在内置表里，要 AI 翻。 */
    private val swedish = PhraseTables.EN.copy(
        header = "Jag skulle vilja beställa:",
        allergyTemplate = "Jag är allergisk mot {items}.",
        andWord = " och ",
        serialComma = false,
    )

    private val sse = headersOf(HttpHeaders.ContentType, "text/event-stream")
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    /** 假的 OpenAI 兼容接口：Key 是 good 时把 [answer] 当正文流回来；[online] 为 false 时 503。 */
    private class Server(var answer: String, var online: Boolean = true) {
        var calls = 0
    }

    private fun chunk(content: String) = buildJsonObject {
        putJsonArray("choices") {
            addJsonObject {
                putJsonObject("delta") { put("content", content) }
                put("finish_reason", JsonNull)
            }
        }
    }.toString()

    private fun resolver(server: Server, prefs: FakePreferences): ResolveReceiptLanguageUseCase {
        val client = AiProviderClient(
            createVisionHttpClient(
                MockEngine { req ->
                    server.calls++
                    if (!server.online) return@MockEngine respond(ByteReadChannel("{}"), HttpStatusCode.ServiceUnavailable, json)
                    if (req.headers[HttpHeaders.Authorization] != "Bearer good") {
                        return@MockEngine respond(ByteReadChannel("{}"), HttpStatusCode.Unauthorized, json)
                    }
                    respond(
                        ByteReadChannel(
                            "data: ${chunk(server.answer)}\n\n" +
                                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n",
                        ),
                        HttpStatusCode.OK,
                        sse,
                    )
                },
            ),
        )
        return ResolveReceiptLanguageUseCase(prefs, ActiveVisionModel(prefs, FakeSecrets(mapOf(AiProvider.OPENAI to "good")), client))
    }

    private fun connectedPrefs() = FakePreferences(AiSettings(selected = ModelRef(AiProvider.OPENAI, "any-text-model").encode()))

    @Test
    fun builtInLanguagesNeverCallTheNetwork() = runTest {
        val server = Server(swedish.toJson())
        val resolve = resolver(server, connectedPrefs())
        assertEquals("de", resolve("de-AT").tag)
        assertEquals("zh-Hant", resolve("zh-TW").tag)
        assertEquals(ReceiptLanguage.ENGLISH, resolve(null))
        assertEquals(0, server.calls)
    }

    @Test
    fun translatesOnceThenUsesCache() = runTest {
        val prefs = connectedPrefs()
        val server = Server("Här är JSON:\n```json\n${swedish.toJson()}\n```")
        val resolve = resolver(server, prefs)

        val first = resolve("sv-SE")
        assertEquals("sv", first.tag)
        assertTrue(first.aiTranslated)
        assertNull(first.unavailable)
        assertEquals("Jag skulle vilja beställa:", first.table.header)
        assertNotNull(prefs.phraseCache["sv"])

        // 缓存命中：断网也能用，不再请求。
        server.online = false
        assertEquals(first, resolve("sv"))
        assertEquals(first, resolve.offline("sv"))
        assertEquals(1, server.calls)

        val receipt = buildReceipt(emptyList(), com.openbite.core.model.TasteProfile(), first, userLang = "zh-CN")
        assertEquals("Jag skulle vilja beställa:", receipt.header.primary)
        assertEquals("我要点这些", receipt.header.secondary)
    }

    @Test
    fun offlineFallsBackToEnglishAndRetriesLater() = runTest {
        val prefs = connectedPrefs()
        val server = Server(swedish.toJson(), online = false)
        val resolve = resolver(server, prefs)

        val fallback = resolve("sv")
        assertEquals("en", fallback.tag)
        assertEquals("sv", fallback.unavailable)
        assertTrue(prefs.phraseCache.isEmpty(), "失败不缓存")
        assertNull(resolve.offline("sv"))

        server.online = true
        assertEquals("sv", resolve("sv").tag, "联网后重试成功")
    }

    @Test
    fun badTranslationIsRejected() = runTest {
        val prefs = connectedPrefs()
        val server = Server(swedish.copy(allergens = swedish.allergens.dropLast(1)).toJson())
        val result = resolver(server, prefs)("sv")
        assertEquals("sv", result.unavailable)
        assertTrue(prefs.phraseCache.isEmpty())
    }

    @Test
    fun demoModeAndNoModelNeverCallTheNetwork() = runTest {
        val server = Server(swedish.toJson())
        assertEquals("sv", resolver(server, FakePreferences(AiSettings(demoMode = true)))("sv").unavailable)
        assertEquals("sv", resolver(server, FakePreferences())("sv").unavailable)
        assertEquals(0, server.calls)
    }

    @Test
    fun aiJsonValidation() {
        val good = swedish.toJson()
        assertEquals(swedish, parsePhraseTable(good))
        assertEquals(swedish, parsePhraseTable("Sure!\n$good\nEnjoy."))
        assertNull(parsePhraseTable("no json here"))
        assertNull(parsePhraseTable(good.replace("\"header\"", "\"title\"")), "键名不对")
        assertNull(parsePhraseTable(good.replace("{\"header\"", "{\"extra\":\"x\",\"header\"")), "多了键")
        assertNull(parsePhraseTable(swedish.copy(avoids = swedish.avoids + "x").toJson()), "个数不对")
        assertNull(parsePhraseTable(swedish.copy(header = " ").toJson()), "空的")
        assertNull(parsePhraseTable(swedish.copy(allergens = listOf("x".repeat(41)) + swedish.allergens.drop(1)).toJson()), "太长")
        assertNull(parsePhraseTable(swedish.copy(avoidTemplate = "Utan, tack.").toJson()), "没有占位符")
        assertNull(parsePhraseTable(swedish.copy(avoidTemplate = "{items} {items}").toJson()), "占位符两次")
        assertNull(parsePhraseTable(swedish.copy(disclaimer = "a\nb").toJson()), "换行")
        assertEquals("Hej", parsePhraseTable(swedish.copy(header = "  Hej ").toJson())?.header, "两端空白去掉")
    }
}
