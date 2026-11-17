package com.openbite.core.network

import com.openbite.core.model.ScannedReceipt
import com.openbite.core.model.VisionModelChoice
import io.ktor.client.HttpClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * “示例菜单（不调用模型）”：不看照片、不联网，按真实模型的节奏把一份打包好的菜单一点点吐出来。
 * 走和 Claude 一样的 NDJSON 解析，评委不填 Key 也能看到完整的流式识别。
 */
class SampleVisionModel(
    private val chunkSize: Int = 14,
    private val chunkDelayMillis: Long = 70,
    private val startDelayMillis: Long = 900,
) : VisionModel {
    override val id: String = ID

    override fun scanMenu(request: MenuScanRequest): Flow<ScanEvent> = flow {
        val parser = NdjsonMenuParser()
        delay(startDelayMillis)
        SAMPLE_OUTPUT.chunked(chunkSize).forEach { chunk ->
            parser.feed(chunk).forEach { emit(it) }
            delay(chunkDelayMillis)
        }
        parser.finish().forEach { emit(it) }
        emit(ScanEvent.Finished(parser.rawOutput))
    }

    /** 不看照片，过一会儿返回一张打包好的小票（设计稿 19 的とり松）。 */
    override suspend fun readReceipt(request: ReceiptReadRequest): ScannedReceipt {
        delay(startDelayMillis)
        return checkNotNull(parseReceipt(SAMPLE_RECEIPT))
    }

    companion object {
        const val ID = "sample"

        val SAMPLE_RECEIPT: String =
            """{"placeName":"炭火焼鳥 とり松","items":[""" +
                """{"name":"焼き鳥盛り合わせ","nameTranslated":"烤鸡肉串拼盘","qty":1,"amountMinor":980},""" +
                """{"name":"だし巻き玉子","nameTranslated":"高汤煎蛋卷","qty":1,"amountMinor":580},""" +
                """{"name":"海老の天ぷら","nameTranslated":"炸虾天妇罗","qty":1,"amountMinor":1200},""" +
                """{"name":"生ビール","nameTranslated":"生啤","qty":2,"amountMinor":1100}""" +
                """],"subtotalMinor":3860,"taxMinor":386,"totalMinor":4246,"currency":"JPY"}"""

        /** 一家京都的おばんざい店，和种子数据里的店不重名。 */
        val SAMPLE_OUTPUT: String = """
            {"type":"menu","placeName":"おばんざい 小春","estimatedDishCount":7,"currency":"JPY","sourceLang":"ja"}
            {"type":"dish","nameOriginal":"本日のおばんざい三種","nameTranslated":"今日京都家常菜三拼","category":"おばんざい","priceMinor":880,"currency":"JPY","allergens":["soy","fish"]}
            {"type":"dish","nameOriginal":"湯葉刺し","nameTranslated":"生豆腐皮","category":"一品","priceMinor":680,"currency":"JPY","allergens":["soy"]}
            {"type":"dish","nameOriginal":"万願寺とうがらしの炊いたん","nameTranslated":"炖万愿寺辣椒","category":"おばんざい","priceMinor":520,"currency":"JPY","allergens":["soy","fish"]}
            {"type":"dish","nameOriginal":"鰊なす","nameTranslated":"鲱鱼炖茄子","category":"おばんざい","priceMinor":750,"currency":"JPY","allergens":["fish","soy"]}
            {"type":"dish","nameOriginal":"九条ねぎのぬた","nameTranslated":"九条葱拌醋味噌","category":"一品","priceMinor":480,"currency":"JPY","allergens":["soy"]}
            {"type":"dish","nameOriginal":"鯖寿司","nameTranslated":"青花鱼押寿司","category":"お食事","priceMinor":1200,"currency":"JPY","allergens":["fish"]}
            {"type":"dish","nameOriginal":"冷酒 一合","nameTranslated":"冷清酒（一合）","category":"飲み物","priceMinor":700,"currency":"JPY","allergens":[]}
        """.trimIndent() + "\n"
    }
}

/** 按设置选出识别模型。 */
class VisionModelRegistry(private val client: HttpClient) {
    private val sample = SampleVisionModel()
    private val haiku by lazy { ClaudeVisionModel(client, AnthropicModels.HAIKU) }
    private val sonnet by lazy { ClaudeVisionModel(client, AnthropicModels.SONNET) }

    fun modelFor(choice: VisionModelChoice): VisionModel = when (choice) {
        VisionModelChoice.CLAUDE_HAIKU -> haiku
        VisionModelChoice.CLAUDE_SONNET -> sonnet
        VisionModelChoice.SAMPLE -> sample
    }
}
