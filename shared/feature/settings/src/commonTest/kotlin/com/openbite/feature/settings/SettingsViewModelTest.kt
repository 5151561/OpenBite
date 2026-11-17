package com.openbite.feature.settings

import com.openbite.core.domain.ClearAllDataUseCase
import com.openbite.core.domain.LoadSampleDataUseCase
import com.openbite.core.domain.ObserveAiOverviewUseCase
import com.openbite.core.domain.SetDemoModeUseCase
import com.openbite.core.domain.ObserveKeepLocationUseCase
import com.openbite.core.domain.ObserveReminderSettingsUseCase
import com.openbite.core.domain.ObserveTasteProfileUseCase
import com.openbite.core.domain.SetKeepLocationUseCase
import com.openbite.core.domain.SetReminderSettingsUseCase
import com.openbite.core.model.ReminderSettings
import com.openbite.core.testing.FakePhotoStore
import com.openbite.core.testing.FakePreferences
import com.openbite.core.testing.FakeReminders
import com.openbite.core.testing.FakeSampleData
import com.openbite.core.testing.FakeSecrets
import com.openbite.core.testing.FakeTempFiles
import com.openbite.core.testing.keepCollecting
import com.openbite.core.testing.runViewModelTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsViewModelTest {
    private val prefs = FakePreferences()
    private val secrets = FakeSecrets()
    private val sample = FakeSampleData()
    private val photos = FakePhotoStore()
    private val temp = FakeTempFiles()
    private val reminders = FakeReminders()

    private fun viewModel() = SettingsViewModel(
        LoadSampleDataUseCase(sample),
        ClearAllDataUseCase(sample, photos, temp),
        ObserveAiOverviewUseCase(prefs, secrets),
        SetDemoModeUseCase(prefs),
        ObserveTasteProfileUseCase(prefs),
        ObserveReminderSettingsUseCase(prefs),
        SetReminderSettingsUseCase(prefs, reminders),
        ObserveKeepLocationUseCase(prefs),
        SetKeepLocationUseCase(prefs),
    )

    @Test
    fun oneStateCombinesEverything() = runViewModelTest {
        val vm = viewModel()
        assertNull(vm.uiState.value.ai, "加载完之前为 null")
        keepCollecting(vm.uiState)
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(true, state.keepLocation)
        assertEquals(ReminderSettings(), state.reminders)
        assertNull(state.tasteSummary)
        val ai = state.ai!!
        assertFalse(ai.demoMode, "演示模式默认关")
        assertNull(ai.selected)
        assertEquals(0, ai.connectedCount)
    }

    @Test
    fun clearNeedsConfirmationAndWipesTempFiles() = runViewModelTest {
        val vm = viewModel()
        keepCollecting(vm.uiState)
        vm.requestClear()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.confirmingClear)
        vm.cancelClear()
        advanceUntilIdle()
        assertEquals(0, sample.clears)

        vm.requestClear()
        vm.confirmClear()
        advanceUntilIdle()
        assertEquals(1, sample.clears)
        assertEquals(1, temp.cleared)
        assertEquals(SettingsMessage.CLEARED, vm.uiState.value.message)
        assertFalse(vm.uiState.value.isWorking)
    }

    @Test
    fun sampleDataAndFailures() = runViewModelTest {
        val vm = viewModel()
        keepCollecting(vm.uiState)
        vm.loadSample()
        advanceUntilIdle()
        assertEquals(SettingsMessage.SAMPLE_LOADED, vm.uiState.value.message)
        vm.loadSample()
        advanceUntilIdle()
        assertEquals(SettingsMessage.SAMPLE_SKIPPED_NOT_EMPTY, vm.uiState.value.message)
        sample.fail = true
        vm.loadSample()
        advanceUntilIdle()
        assertEquals(SettingsMessage.FAILED, vm.uiState.value.message)
    }

    @Test
    fun togglesWriteThrough() = runViewModelTest {
        val vm = viewModel()
        keepCollecting(vm.uiState)
        vm.setKeepLocation(false)
        vm.setReminders(meal = false, milestones = true)
        vm.setDemoMode(true)
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.keepLocation)
        assertEquals(ReminderSettings(mealFollowUp = false, milestones = true), vm.uiState.value.reminders)
        assertEquals(1, reminders.milestoneChecks)
        assertTrue(vm.uiState.value.ai!!.demoMode)
    }
}
