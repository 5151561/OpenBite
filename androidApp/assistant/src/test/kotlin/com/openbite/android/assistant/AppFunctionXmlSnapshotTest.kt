package com.openbite.android.assistant

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 钉住 KSP 生成的 AppFunctions 说明：函数 id 由类名 + 方法名生成，改名会让系统助手已缓存的调用失效。
 * 要改名就同时改这里，并在说明里写清楚。
 */
class AppFunctionXmlSnapshotTest {
    // debug / release 单测各自生成一份，内容一样；取已经生成的那份。
    private val xml = listOf("debug", "release")
        .map { File("build/generated/ksp/$it/resources/assets/openbite_app_function_service.xml") }
        .firstOrNull { it.exists() } ?: File("build/generated/ksp/debug/resources/assets/openbite_app_function_service.xml")

    private fun Element.children(tag: String) =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }

    private fun Element.text(tag: String) = children(tag).single().textContent.trim()

    @Test
    fun functionIdsAndRequiredParametersAreStable() {
        assertTrue(xml.exists(), "先跑 kspDebugKotlin：$xml")
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml).documentElement
        val functions = root.children("appfunction").associate { fn ->
            val required = fn.children("parameters").filter { it.text("isRequired") == "true" }.map { it.text("name") }
            fn.text("id") to required
        }
        val base = "com.openbite.android.assistant.BaseOpenBiteAppFunctionService#"
        assertEquals(
            mapOf(
                base + "upcomingMilestones" to emptyList(),
                base + "queryDiningLogs" to emptyList(),
                base + "searchPlaces" to emptyList(),
                base + "getPlaceMenu" to listOf("placeId"),
                base + "createDiningLog" to listOf("placeId", "items"),
            ),
            functions,
        )
    }
}
