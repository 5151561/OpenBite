package com.openbite.core.domain

import com.openbite.core.data.MenuScanRepository
import com.openbite.core.data.NewMenuScan
import com.openbite.core.data.platform.ImageProcessor
import com.openbite.core.data.platform.LocationProvider
import com.openbite.core.data.platform.PhotoStore
import com.openbite.core.data.platform.PreferencesStore
import com.openbite.core.data.platform.ProcessedImage
import com.openbite.core.data.platform.SecretKeys
import com.openbite.core.data.platform.SecretStore
import com.openbite.core.model.Dish
import com.openbite.core.model.Money
import com.openbite.core.model.Place
import com.openbite.core.model.ScanError
import com.openbite.core.model.ScanException
import com.openbite.core.model.TasteProfile
import com.openbite.core.model.VisionModelChoice
import com.openbite.core.model.maskApiKey
import com.openbite.core.network.SampleVisionModel
import com.openbite.core.network.VisionModelRegistry
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ScanMenuUseCaseTest {
    private class FakeSecrets(initial: String? = null) : SecretStore {
        val values = MutableStateFlow(initial?.let { mapOf(SecretKeys.ANTHROPIC_API_KEY to it) } ?: emptyMap())
        override fun observe(key: String): Flow<String?> = values.map { it[key] }
        override suspend fun get(key: String) = values.value[key]
        override suspend fun put(key: String, value: String) { values.value += key to value }
        override suspend fun remove(key: String) { values.value -= key }
    }

    internal class FakePrefs(initial: VisionModelChoice? = null) : PreferencesStore {
        val value = MutableStateFlow(initial)
        override fun observeVisionModel() = value
        override suspend fun setVisionModel(choice: VisionModelChoice) { value.value = choice }
        val taste = MutableStateFlow(TasteProfile.EMPTY)
        override fun observeTasteProfile() = taste
        override suspend fun setTasteProfile(profile: TasteProfile) { taste.value = profile }
        val reminderSettings = MutableStateFlow(com.openbite.core.data.platform.ReminderSettings())
        override fun observeReminderSettings() = reminderSettings
        override suspend fun setReminderSettings(settings: com.openbite.core.data.platform.ReminderSettings) { reminderSettings.value = settings }
        val sent = mutableSetOf<String>()
        override suspend fun sentMilestoneReminders(): Set<String> = sent.toSet()
        override suspend fun markMilestoneRemindersSent(keys: Set<String>) { sent += keys }
    }

    private class FakeRepo(val places: MutableMap<String, Place> = mutableMapOf(), val menus: Map<String, List<Dish>> = emptyMap()) :
        MenuScanRepository {
        var saved: Triple<Place?, NewMenuScan, List<Dish>>? = null
        override suspend fun place(placeId: String) = places[placeId]
        override suspend fun menuOf(placeId: String) = menus[placeId].orEmpty()
        override suspend fun save(newPlace: Place?, scan: NewMenuScan, dishes: List<Dish>) { saved = Triple(newPlace, scan, dishes) }
    }

    private class FakePhotos : PhotoStore {
        val files = mutableMapOf<String, ByteArray>()
        override suspend fun save(id: String, jpegBytes: ByteArray) = "photos/$id.jpg".also { files[it] = jpegBytes }
        override suspend fun delete(relativePath: String) { files.remove(relativePath) }
        override suspend fun deleteAll() = files.clear()
    }

    private val noNetwork = HttpClient(MockEngine { respondError(HttpStatusCode.Unauthorized) })

    private fun useCase(
        repo: FakeRepo = FakeRepo(),
        photos: FakePhotos = FakePhotos(),
        gps: Pair<Double, Double>? = null,
        current: Pair<Double, Double>? = 35.0 to 135.7,
        prefs: FakePrefs = FakePrefs(),
        secrets: FakeSecrets = FakeSecrets(),
        client: HttpClient = noNetwork,
    ): ScanMenuUseCase {
        var n = 0
        return ScanMenuUseCase(
            imageProcessor = object : ImageProcessor {
                override suspend fun process(sourceUri: String, maxLongEdgePx: Int) =
                    ProcessedImage(byteArrayOf(9), gps?.first, gps?.second)
            },
            locationProvider = LocationProvider { current },
            photoStore = photos,
            scans = repo,
            preferences = prefs,
            secrets = secrets,
            models = VisionModelRegistry(client),
            ids = IdGenerator { "id-${++n}" },
            now = NowProvider { 1_000L },
        )
    }

    private fun request(placeId: String? = null, override: VisionModelChoice? = null) =
        ScanMenuRequest("content://photo/1", placeId, defaultPlaceName = "新店铺", modelOverride = override)

    @Test
    fun noKeyFallsBackToSampleAndCreatesPlaceFromHeader() = runTest {
        val repo = FakeRepo()
        val photos = FakePhotos()
        var asked = false
        val updates = useCase(repo, photos, gps = 35.01 to 135.77).invoke(request()) { asked = true; true }.toList()

        assertEquals(ScanUpdate.Started(VisionModelChoice.SAMPLE), updates.first())
        assertTrue(updates.filterIsInstance<ScanUpdate.Prepared>().single().hasLocation)
        assertFalse(asked, "照片有 GPS 时不该要定位权限")
        assertEquals(7, updates.filterIsInstance<ScanUpdate.DishFound>().size)
        val saved = assertIs<ScanUpdate.Saved>(updates.last()).result
        assertTrue(saved.isNewPlace)
        assertEquals("おばんざい 小春", saved.placeName)
        assertEquals(7, saved.added)
        assertEquals(saved.dishIds.getValue("湯葉刺し"), repo.saved!!.third.first { it.nameOriginal == "湯葉刺し" }.id)

        val (place, scan, dishes) = repo.saved!!
        assertEquals(35.01, place!!.lat)
        assertEquals("photos/${scan.id}.jpg", scan.photoPath)
        assertEquals(SampleVisionModel.ID, scan.model)
        assertEquals("ja", scan.sourceLang)
        assertEquals(SampleVisionModel.SAMPLE_OUTPUT, scan.rawOutput)
        assertTrue(dishes.all { it.scanId == scan.id && it.placeId == place.id })
        assertTrue(scan.photoPath in photos.files)
    }

    @Test
    fun withoutGpsAsksPermissionThenUsesCurrentLocation() = runTest {
        val repo = FakeRepo()
        var asked = 0
        val updates = useCase(repo, current = 34.99 to 135.75).invoke(request()) { asked++; true }.toList()
        assertEquals(1, asked)
        assertTrue(updates.filterIsInstance<ScanUpdate.Prepared>().single().hasLocation)
        assertEquals(34.99, repo.saved!!.first!!.lat)
    }

    @Test
    fun deniedPermissionMeansNoLocation() = runTest {
        val repo = FakeRepo()
        val updates = useCase(repo).invoke(request()) { false }.toList()
        assertFalse(updates.filterIsInstance<ScanUpdate.Prepared>().single().hasLocation)
        assertEquals(null, repo.saved!!.first!!.lat)
    }

    @Test
    fun scanFromPlaceAttachesAndMergesWithoutAskingLocation() = runTest {
        val place = Place("p", "おばんざい 小春", 35.0, 135.0, null)
        val oldDish = Dish("d-old", "p", "一品", "湯葉刺し", "豆腐皮", Money(600, "JPY"))
        val repo = FakeRepo(mutableMapOf("p" to place), mapOf("p" to listOf(oldDish)))
        var asked = false
        val updates = useCase(repo).invoke(request(placeId = "p")) { asked = true; true }.toList()
        assertFalse(asked)
        val result = assertIs<ScanUpdate.Saved>(updates.last()).result
        assertFalse(result.isNewPlace)
        assertEquals("おばんざい 小春", assertIs<ScanUpdate.Started>(updates.first()).placeName)
        assertEquals("d-old", result.dishIds["湯葉刺し"])
        assertEquals("p", result.placeId)
        assertEquals(1, result.updated)
        assertEquals(6, result.added)
        val (newPlace, _, dishes) = repo.saved!!
        assertEquals(null, newPlace)
        assertEquals(Money(680, "JPY"), dishes.first { it.id == "d-old" }.price)
    }

    @Test
    fun missingPlaceFails() = runTest {
        val error = assertFailsWith<ScanException> { useCase().invoke(request(placeId = "gone")) { true }.toList() }
        assertEquals(ScanError.PLACE_MISSING, error.error)
    }

    @Test
    fun forcingRealModelWithoutKeyFails() = runTest {
        val error = assertFailsWith<ScanException> {
            useCase().invoke(request(override = VisionModelChoice.CLAUDE_HAIKU)) { true }.toList()
        }
        assertEquals(ScanError.NO_API_KEY, error.error)
    }

    @Test
    fun invalidKeyMapsToErrorAndSavesNothing() = runTest {
        val repo = FakeRepo()
        val photos = FakePhotos()
        val error = assertFailsWith<ScanException> {
            useCase(repo, photos, secrets = FakeSecrets("sk-ant-bad")).invoke(request()) { true }.toList()
        }
        assertEquals(ScanError.INVALID_API_KEY, error.error)
        assertEquals(null, repo.saved)
        assertTrue(photos.files.isEmpty())
    }

    @Test
    fun visionSettingsMaskKeyAndFallBack() = runTest {
        val secrets = FakeSecrets()
        val prefs = FakePrefs(VisionModelChoice.CLAUDE_SONNET)
        val observe = ObserveVisionSettingsUseCase(prefs, secrets)
        assertEquals(VisionModelChoice.SAMPLE, observe().first().effective)
        assertFalse(SaveApiKeyUseCase(secrets)("  "))
        assertFalse(SaveApiKeyUseCase(secrets)("sk ant"))
        assertTrue(SaveApiKeyUseCase(secrets)("  sk-ant-api03-abcdefghijklWXYZ \n"))
        val settings = observe().first()
        assertEquals(VisionModelChoice.CLAUDE_SONNET, settings.effective)
        assertEquals("sk-ant-••••WXYZ", settings.maskedKey)
        assertEquals("sk-ant-api03-abcdefghijklWXYZ", secrets.get(SecretKeys.ANTHROPIC_API_KEY))
        ClearApiKeyUseCase(secrets)()
        assertEquals(null, observe().first().maskedKey)
        assertEquals("••••••••", maskApiKey("short"))
    }
}
