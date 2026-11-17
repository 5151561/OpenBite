package com.openbite.feature.settings

import com.openbite.core.domain.ConnectProviderUseCase
import com.openbite.core.domain.DisconnectProviderUseCase
import com.openbite.core.domain.NowProvider
import com.openbite.core.domain.ObserveAiOverviewUseCase
import com.openbite.core.domain.RefreshModelsUseCase
import com.openbite.core.domain.SelectModelUseCase
import com.openbite.core.model.AiProvider
import com.openbite.core.model.ModelRef
import com.openbite.core.model.VisionSupport
import com.openbite.core.network.AiProviderClient
import com.openbite.core.network.createVisionHttpClient
import com.openbite.core.testing.FakePreferences
import com.openbite.core.testing.FakeSecrets
import com.openbite.core.testing.keepCollecting
import com.openbite.core.testing.runViewModelTest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProviderDetailViewModelTest {
    private val prefs = FakePreferences()
    private val secrets = FakeSecrets()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val sse = headersOf(HttpHeaders.ContentType, "text/event-stream")

    /** Key 是 good 才放行；/models 给 3 个；只有 vision-pro 答得出 Red。 */
    private val client = AiProviderClient(
        createVisionHttpClient(
            MockEngine { req ->
                if (req.headers[HttpHeaders.Authorization] != "Bearer good") {
                    return@MockEngine respond(ByteReadChannel("{}"), HttpStatusCode.Unauthorized, json)
                }
                if (req.url.toString().endsWith("/models")) {
                    respond(
                        ByteReadChannel("""{"data":[{"id":"vision-pro","created":3},{"id":"text-only","created":2},{"id":"embed-1","created":1}]}"""),
                        HttpStatusCode.OK,
                        json,
                    )
                } else if ("vision-pro" in (req.body as TextContent).text) {
                    respond(
                        ByteReadChannel("data: {\"choices\":[{\"delta\":{\"content\":\"red\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"),
                        HttpStatusCode.OK,
                        sse,
                    )
                } else {
                    respond(ByteReadChannel("""{"error":{"code":"invalid_value"}}"""), HttpStatusCode.BadRequest, json)
                }
            },
        ),
    )

    private fun viewModel() = ProviderDetailViewModel(
        AiProvider.DEEPSEEK,
        ObserveAiOverviewUseCase(prefs, secrets),
        ConnectProviderUseCase(prefs, secrets, client, NowProvider { 7L }),
        RefreshModelsUseCase(prefs, secrets, client, NowProvider { 8L }),
        SelectModelUseCase(prefs, secrets, client),
        DisconnectProviderUseCase(prefs, secrets),
    )

    /** MockEngine 在别的线程上回包，advanceUntilIdle 等不到它：先清掉旧提示，做完再等新提示出来。 */
    private suspend fun TestScope.act(vm: ProviderDetailViewModel, action: () -> Unit) {
        vm.messageShown()
        advanceUntilIdle()
        action()
        advanceUntilIdle()
        vm.uiState.first { it.busy == null && it.message != null }
    }

    @Test
    fun connectSearchSelectDisconnect() = runViewModelTest {
        val vm = viewModel()
        keepCollecting(vm.uiState)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.connected)
        assertTrue(vm.uiState.value.models.isEmpty(), "没连上时不列任何模型")

        act(vm) { vm.connect("nope", null) }
        assertEquals(ProviderMessage.KEY_REJECTED, vm.uiState.value.message)
        assertFalse(vm.uiState.value.connected)

        act(vm) { vm.connect("good", null) }
        val connected = vm.uiState.value
        assertEquals(ProviderMessage.CONNECTED, connected.message)
        assertTrue(connected.connected)
        assertEquals(3, connected.totalModels)
        assertEquals(listOf("vision-pro", "text-only", "embed-1"), connected.models.map { it.model.id })
        assertTrue(connected.models.all { it.vision == VisionSupport.UNKNOWN })

        vm.setQuery("TEXT")
        advanceUntilIdle()
        assertEquals(listOf("text-only"), vm.uiState.value.models.map { it.model.id })
        vm.setQuery("")

        act(vm) { vm.select("text-only") }
        assertEquals(ProviderMessage.NO_VISION, vm.uiState.value.message)
        assertEquals(VisionSupport.NO, vm.uiState.value.models.first { it.model.id == "text-only" }.vision)
        assertNull(prefs.ai.value.selected)

        act(vm) { vm.select("vision-pro") }
        assertEquals(ProviderMessage.SELECTED, vm.uiState.value.message)
        assertTrue(vm.uiState.value.models.first { it.model.id == "vision-pro" }.selected)
        assertEquals(ModelRef(AiProvider.DEEPSEEK, "vision-pro"), prefs.ai.value.selectedModel)
        assertNull(vm.uiState.value.busy)

        vm.disconnect()
        advanceUntilIdle()
        assertEquals(ProviderMessage.DISCONNECTED, vm.uiState.value.message)
        assertFalse(vm.uiState.value.connected)
        assertNull(prefs.ai.value.selected)
        assertTrue(vm.uiState.value.models.isEmpty())
    }
}
