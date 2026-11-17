package com.openbite.android.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.contains
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/** 把栈顶 entry 放进 ModalBottomSheet，下面一页照常显示（Navigation 3 bottom sheet recipe）。 */
@OptIn(ExperimentalMaterial3Api::class)
private class BottomSheetScene<T : Any>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    private val onBack: () -> Unit,
) : OverlayScene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)

    private var sheetState: SheetState? = null

    override val content: @Composable () -> Unit = {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        sheetState = state
        ModalBottomSheet(onDismissRequest = onBack, sheetState = state) { entry.Content() }
    }

    /** 出栈时先播收起动画。 */
    override suspend fun onRemove() {
        sheetState?.hide()
    }
}

/** 带 [bottomSheet] 元数据的 entry 显示成底部弹窗。要排在其他（非浮层）策略前面。 */
class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val last = entries.lastOrNull() ?: return null
        if (!last.metadata.contains(BottomSheetKey)) return null
        return BottomSheetScene(
            key = last.contentKey,
            previousEntries = entries.dropLast(1),
            overlaidEntries = entries.dropLast(1),
            entry = last,
            onBack = onBack,
        )
    }

    companion object {
        fun bottomSheet(): Map<String, Any> = metadata { put(BottomSheetKey, true) }

        private object BottomSheetKey : NavMetadataKey<Boolean>
    }
}
