package com.openbite.feature.scan

import com.openbite.core.model.ScannedDish
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScanUiStateTest {
    private val dish = ScannedDish(nameOriginal = "冷奴")

    @Test
    fun progressUsesEstimateAndNeverReachesOneBeforeDone() {
        assertNull(ScanUiState(phase = ScanPhase.READING).progress)
        assertEquals(0.5f, ScanUiState(phase = ScanPhase.READING, estimatedDishCount = 4, dishes = List(2) { dish }).progress)
        // 模型估少了：最多 95%，等保存完才到 100%。
        assertEquals(0.95f, ScanUiState(phase = ScanPhase.READING, estimatedDishCount = 2, dishes = List(3) { dish }).progress)
        assertEquals(1f, ScanUiState(phase = ScanPhase.DONE).progress)
    }
}
