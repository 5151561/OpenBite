package com.openbite.core.domain

import com.openbite.core.data.SampleDataRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FirstLaunchSeedTest {
    private class FakeSample : SampleDataRepository {
        var loads = 0
        override suspend fun loadSampleIfEmpty(): Boolean {
            loads++
            return true
        }
        override suspend fun clearAll() = Unit
    }

    @Test
    fun importsOnceThenRemembers() = runTest {
        val sample = FakeSample()
        val prefs = ScanMenuUseCaseTest.FakePrefs()
        val seed = FirstLaunchSeedUseCase(LoadSampleDataUseCase(sample), prefs)
        seed()
        seed()
        assertEquals(1, sample.loads)
        assertTrue(prefs.seedDone)
    }

    @Test
    fun alreadyImportedSkips() = runTest {
        val sample = FakeSample()
        val prefs = ScanMenuUseCaseTest.FakePrefs().apply { seedDone = true }
        FirstLaunchSeedUseCase(LoadSampleDataUseCase(sample), prefs)()
        assertEquals(0, sample.loads)
    }
}
