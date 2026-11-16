package com.openbite.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openbite.core.domain.RenamePlaceUseCase
import com.openbite.core.domain.ScanMenuRequest
import com.openbite.core.domain.ScanMenuUseCase
import com.openbite.core.domain.ScanResult
import com.openbite.core.domain.ScanUpdate
import com.openbite.core.model.ScanError
import com.openbite.core.model.ScanException
import com.openbite.core.model.VisionModelChoice
import com.openbite.core.network.ScannedDish
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScanPhase { WAITING_PHOTO, PREPARING, READING, SAVING, DONE, FAILED }

data class ScanUiState(
    val phase: ScanPhase = ScanPhase.WAITING_PHOTO,
    val model: VisionModelChoice? = null,
    /** 处理好的照片 JPEG（和发给模型的同一张）。 */
    val photo: ByteArray? = null,
    /** null：还不知道。 */
    val hasLocation: Boolean? = null,
    val estimatedDishCount: Int? = null,
    val dishes: List<ScannedDish> = emptyList(),
    /** 正在写的那道菜，原名已经出来、译名和价格还没到。 */
    val pendingName: String? = null,
    val result: ScanResult? = null,
    val error: ScanError? = null,
    /** 要新建店铺、照片又没有 GPS：界面这时去要定位权限，然后调 [ScanViewModel.onLocationPermissionResult]。 */
    val askingLocationPermission: Boolean = false,
) {
    /** 0..1；不知道一共几道菜时为 null（界面显示不定进度）。 */
    val progress: Float?
        get() = when {
            phase == ScanPhase.DONE -> 1f
            estimatedDishCount == null -> null
            else -> (dishes.size.toFloat() / estimatedDishCount).coerceAtMost(0.95f)
        }

    val isWorking: Boolean get() = phase in setOf(ScanPhase.PREPARING, ScanPhase.READING, ScanPhase.SAVING)
}

/**
 * 识别菜单页。[placeId] 不为 null 时识别结果挂到这家店（补拍 / 又来了），否则新建店铺。
 * 照片由界面拍好或选好后交给 [onPhoto]。
 */
class ScanViewModel(
    private val placeId: String?,
    private val scanMenu: ScanMenuUseCase,
    private val renamePlace: RenamePlaceUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = state.asStateFlow()

    private var job: Job? = null
    private var permissionAnswer: CompletableDeferred<Boolean>? = null
    private var lastRequest: ScanMenuRequest? = null

    fun onPhoto(sourceUri: String, defaultPlaceName: String) {
        if (state.value.phase != ScanPhase.WAITING_PHOTO) return
        start(ScanMenuRequest(sourceUri, placeId, defaultPlaceName))
    }

    /** 重试；[useSample] 为 true 时这次改用示例菜单（没 Key / Key 无效 / 额度用完时给的出路）。 */
    fun retry(useSample: Boolean = false) {
        val request = lastRequest ?: return
        if (state.value.isWorking) return
        val photo = state.value.photo
        start(request.copy(modelOverride = if (useSample) VisionModelChoice.SAMPLE else request.modelOverride))
        // 重试时先把上次的照片放回去，界面不闪空。
        if (photo != null) state.update { it.copy(photo = photo) }
    }

    /** 重新拍：回到等照片的状态。 */
    fun reset() {
        job?.cancel()
        permissionAnswer?.cancel()
        permissionAnswer = null
        lastRequest = null
        state.value = ScanUiState()
    }

    fun onLocationPermissionResult(granted: Boolean) {
        permissionAnswer?.complete(granted)
        permissionAnswer = null
        state.update { it.copy(askingLocationPermission = false) }
    }

    /** 新建的店铺改名（菜单抬头没认出店名时用的是默认名）。 */
    fun rename(name: String) {
        val result = state.value.result ?: return
        viewModelScope.launch {
            if (renamePlace(result.placeId, name)) {
                state.update { it.copy(result = result.copy(placeName = name.trim())) }
            }
        }
    }

    private fun start(request: ScanMenuRequest) {
        lastRequest = request
        job?.cancel()
        state.value = ScanUiState(phase = ScanPhase.PREPARING)
        job = viewModelScope.launch {
            try {
                scanMenu(request, ::askLocationPermission).collect(::apply)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ScanException) {
                fail(e.error)
            } catch (e: Exception) {
                fail(ScanError.UNKNOWN)
            }
        }
    }

    private suspend fun askLocationPermission(): Boolean {
        val answer = CompletableDeferred<Boolean>()
        permissionAnswer = answer
        state.update { it.copy(askingLocationPermission = true) }
        return answer.await()
    }

    private fun apply(update: ScanUpdate) = state.update { s ->
        when (update) {
            is ScanUpdate.Started -> s.copy(model = update.model)
            is ScanUpdate.PhotoReady -> s.copy(photo = update.jpegBytes)
            is ScanUpdate.Prepared -> s.copy(phase = ScanPhase.READING, hasLocation = update.hasLocation)
            is ScanUpdate.Header -> s.copy(estimatedDishCount = update.estimatedDishCount)
            is ScanUpdate.DishStarted -> s.copy(pendingName = update.nameOriginal)
            is ScanUpdate.DishFound -> s.copy(dishes = s.dishes + update.dish, pendingName = null)
            ScanUpdate.Saving -> s.copy(phase = ScanPhase.SAVING, pendingName = null)
            is ScanUpdate.Saved -> s.copy(phase = ScanPhase.DONE, result = update.result)
        }
    }

    private fun fail(error: ScanError) = state.update {
        it.copy(phase = ScanPhase.FAILED, error = error, pendingName = null, askingLocationPermission = false)
    }
}
