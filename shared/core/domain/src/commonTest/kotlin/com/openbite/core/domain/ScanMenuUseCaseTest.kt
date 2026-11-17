package com.openbite.core.domain

import com.openbite.core.testing.noNetworkClient
import com.openbite.core.testing.FakeSecrets
import com.openbite.core.testing.FakeTempFiles
import com.openbite.core.testing.FakePhotoStore
import com.openbite.core.testing.FakePreferences
import com.openbite.core.testing.FakeMenuScans
import com.openbite.core.model.ReminderSettings
import com.openbite.core.data.MenuScanRepository
import com.openbite.core.data.NewMenuScan
import com.openbite.core.data.platform.ImageProcessor
import com.openbite.core.data.platform.LocationProvider
import com.openbite.core.data.platform.PhotoStore
import com.openbite.core.data.platform.PreferencesStore
import com.openbite.core.data.platform.ProcessedImage
import com.openbite.core.data.platform.SecretKeys
import com.openbite.core.data.platform.SecretStore
import com.openbite.core.data.platform.TempFiles
import com.openbite.core.model.Dish
import com.openbite.core.model.Money
import com.openbite.core.model.Place
import com.openbite.core.model.ScanError
import com.openbite.core.model.ScanException
import com.openbite.core.model.TasteProfile
import com.openbite.core.model.AiProvider
import com.openbite.core.model.AiSettings
import com.openbite.core.model.ModelUse
import com.openbite.core.model.maskApiKey
import com.openbite.core.network.SampleVisionModel
import com.openbite.core.network.AiProviderClient
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
    private val noNetwork = noNetworkClient()

    private fun useCase(
        repo: FakeMenuScans = FakeMenuScans(),
        photos: FakePhotoStore = FakePhotoStore(),
        gps: Pair<Double, Double>? = null,
        current: Pair<Double, Double>? = 35.0 to 135.7,
        prefs: FakePreferences = FakePreferences(AiSettings(demoMode = true)),
        secrets: FakeSecrets = FakeSecrets(),
        client: HttpClient = noNetwork,
        temp: FakeTempFiles = FakeTempFiles(),
    ): ScanMenuUseCase {
        var n = 0
        return ScanMenuUseCase(
            imageProcessor = object : ImageProcessor {
                override suspend fun process(sourceUri: String, maxLongEdgePx: Int) =
                    ProcessedImage(byteArrayOf(9), gps?.first, gps?.second)

                override suspend fun reencode(jpegBytes: ByteArray, maxLongEdgePx: Int) = jpegBytes
            },
            locationProvider = LocationProvider { current },
            photoStore = photos,
            scans = repo,
            preferences = prefs,
            activeModel = ActiveVisionModel(prefs, secrets, AiProviderClient(client)),
            ids = IdGenerator { "id-${++n}" },
            now = NowProvider { 1_000L },
            tempFiles = temp,
        )
    }

    @Test
    fun capturedOriginalIsDeletedOnlyAfterSuccessfulSave() = runTest {
        val ok = FakeTempFiles()
        useCase(temp = ok).invoke(request()) { true }.toList()
        assertEquals(listOf("content://photo/1"), ok.discarded)

        val failed = FakeTempFiles()
        assertFailsWith<ScanException> {
            useCase(prefs = realModel(), secrets = badKey(), temp = failed).invoke(request()) { true }.toList()
        }
        assertTrue(failed.discarded.isEmpty(), "失败时留着原图给重试")
    }

    private fun request(placeId: String? = null) = ScanMenuRequest("content://photo/1", placeId, defaultPlaceName = "新店铺")

    /** 演示模式关着，选了 Anthropic 的一个模型。 */
    private fun realModel() = FakePreferences(AiSettings(selected = "ANTHROPIC:claude-test"))

    private fun badKey() = FakeSecrets(mapOf(AiProvider.ANTHROPIC to "sk-ant-bad"))

    @Test
    fun demoModeUsesSampleAndCreatesPlaceFromHeader() = runTest {
        val repo = FakeMenuScans()
        val photos = FakePhotoStore()
        var asked = false
        val updates = useCase(repo, photos, gps = 35.01 to 135.77).invoke(request()) { asked = true; true }.toList()

        assertEquals(ScanUpdate.Started(ModelUse.Demo), updates.first())
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
        val repo = FakeMenuScans()
        var asked = 0
        val updates = useCase(repo, current = 34.99 to 135.75).invoke(request()) { asked++; true }.toList()
        assertEquals(1, asked)
        assertTrue(updates.filterIsInstance<ScanUpdate.Prepared>().single().hasLocation)
        assertEquals(34.99, repo.saved!!.first!!.lat)
    }

    @Test
    fun deniedPermissionMeansNoLocation() = runTest {
        val repo = FakeMenuScans()
        val updates = useCase(repo).invoke(request()) { false }.toList()
        assertFalse(updates.filterIsInstance<ScanUpdate.Prepared>().single().hasLocation)
        assertEquals(null, repo.saved!!.first!!.lat)
    }

    @Test
    fun scanFromPlaceAttachesAndMergesWithoutAskingLocation() = runTest {
        val place = Place("p", "おばんざい 小春", 35.0, 135.0, null)
        val oldDish = Dish("d-old", "p", "一品", "湯葉刺し", "豆腐皮", Money(600, "JPY"))
        val repo = FakeMenuScans(mutableMapOf("p" to place), mapOf("p" to listOf(oldDish)))
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
    fun keepLocationOffIgnoresGpsAndNeverAsks() = runTest {
        val repo = FakeMenuScans()
        val prefs = FakePreferences(AiSettings(demoMode = true)).apply { keepLocation.value = false }
        var asked = false
        val updates = useCase(repo, gps = 35.01 to 135.77, prefs = prefs).invoke(request()) { asked = true; true }.toList()
        assertFalse(asked)
        assertFalse(updates.filterIsInstance<ScanUpdate.Prepared>().single().hasLocation)
        assertEquals(null, repo.saved!!.first!!.lat)
        assertEquals(null, repo.saved!!.first!!.lon)
    }

    @Test
    fun missingPlaceFails() = runTest {
        val error = assertFailsWith<ScanException> { useCase().invoke(request(placeId = "gone")) { true }.toList() }
        assertEquals(ScanError.PLACE_MISSING, error.error)
    }

    @Test
    fun noModelOrNoKeyFails() = runTest {
        val none = assertFailsWith<ScanException> { useCase(prefs = FakePreferences()).invoke(request()) { true }.toList() }
        assertEquals(ScanError.NO_MODEL, none.error)
        // 选了模型，但那家的 Key 被清掉了。
        val noKey = assertFailsWith<ScanException> { useCase(prefs = realModel()).invoke(request()) { true }.toList() }
        assertEquals(ScanError.NO_MODEL, noKey.error)
    }

    @Test
    fun invalidKeyMapsToErrorAndSavesNothing() = runTest {
        val repo = FakeMenuScans()
        val photos = FakePhotoStore()
        val error = assertFailsWith<ScanException> {
            useCase(repo, photos, prefs = realModel(), secrets = badKey()).invoke(request()) { true }.toList()
        }
        assertEquals(ScanError.INVALID_API_KEY, error.error)
        assertEquals(null, repo.saved)
        assertTrue(photos.files.isEmpty())
    }

    @Test
    fun masksKeys() {
        assertEquals("sk-ant-••••WXYZ", maskApiKey("sk-ant-api03-abcdefghijklWXYZ"))
        assertEquals("sk-proj-••••WXYZ", maskApiKey("sk-proj-abcdefghijklWXYZ"))
        assertEquals("••••WXYZ", maskApiKey("AIzaabcdefghijklWXYZ"))
        assertEquals("••••••••", maskApiKey("short"))
    }
}
