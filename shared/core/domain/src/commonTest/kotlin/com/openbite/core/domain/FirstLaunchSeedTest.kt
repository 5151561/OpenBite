package com.openbite.core.domain

import com.openbite.core.testing.FakePreferences
import com.openbite.core.testing.FakeSampleData
import com.openbite.core.data.SampleDataRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FirstLaunchSeedTest {
    @Test
    fun importsOnceThenRemembers() = runTest {
        val sample = FakeSampleData()
        val prefs = FakePreferences()
        val seed = FirstLaunchSeedUseCase(LoadSampleDataUseCase(sample), prefs)
        seed()
        seed()
        assertEquals(1, sample.loads)
        assertTrue(prefs.seedDone)
    }

    @Test
    fun alreadyImportedSkips() = runTest {
        val sample = FakeSampleData()
        val prefs = FakePreferences().apply { seedDone = true }
        FirstLaunchSeedUseCase(LoadSampleDataUseCase(sample), prefs)()
        assertEquals(0, sample.loads)
    }
}
