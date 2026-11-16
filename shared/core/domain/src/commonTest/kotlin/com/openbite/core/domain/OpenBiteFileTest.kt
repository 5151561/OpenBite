package com.openbite.core.domain

import com.openbite.core.data.NewMenuScan
import com.openbite.core.data.PlaceCounts
import com.openbite.core.data.PlaceImportRepository
import com.openbite.core.data.PlaceRepository
import com.openbite.core.data.platform.ImageProcessor
import com.openbite.core.data.platform.IncomingFile
import com.openbite.core.data.platform.IncomingFileSource
import com.openbite.core.data.platform.ProcessedImage
import com.openbite.core.model.Dish
import com.openbite.core.model.DiningLog
import com.openbite.core.model.DiningLogItem
import com.openbite.core.model.DiningStatus
import com.openbite.core.model.LogSource
import com.openbite.core.model.MenuScan
import com.openbite.core.model.Money
import com.openbite.core.model.Person
import com.openbite.core.model.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenBiteFileTest {
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
    private val minato = Place("minato", "割烹 みなと", 35.0085, 135.7713, "京都市中京区木屋町通三条下ル", listOf("适合约会", "纪念日"))
    private val menu = listOf(
        Dish("d1", "minato", "蒸物", "うに茶碗蒸し", "海胆茶碗蒸", Money(2800, "JPY"), listOf("shellfish", "egg"), "s1"),
        Dish("d2", "minato", "甘味", "わらび餅", "蕨饼", null, emptyList(), "s2"),
    )
    private val scans = listOf(
        MenuScan("s1", "minato", "seed/menus/minato-1.jpg", 1, "ja"),
        MenuScan("s2", "minato", "photos/s2.jpg", 2, "ja"),
    )

    private class FakePlaces(val place: Place?, val menu: List<Dish>, val scans: List<MenuScan>) : PlaceRepository {
        override fun observePlaces(): Flow<List<Place>> = flowOf(listOfNotNull(place))
        override fun observePlace(placeId: String) = flowOf(place)
        override fun observeMenu(placeId: String) = flowOf(menu)
        override fun observeScans(placeId: String) = flowOf(scans)
        override fun observeCounts(): Flow<Map<String, PlaceCounts>> = flowOf(emptyMap())
        override suspend fun rename(placeId: String, name: String) = Unit
        override suspend fun setLocation(placeId: String, lat: Double, lon: Double) = Unit
        override suspend fun addTag(placeId: String, tag: String) = Unit
        override suspend fun removeTag(placeId: String, tag: String) = Unit
        override fun observeAllTags(): Flow<List<String>> = flowOf(emptyList())
        override suspend fun updateDish(dishId: String, nameTranslated: String, category: String, price: Money?) = Unit
        override suspend fun searchIds(pattern: String, limit: Int) = emptyList<String>()
    }

    private class FakeImport : PlaceImportRepository {
        var place: Place? = null
        var scans: List<NewMenuScan> = emptyList()
        var dishes: List<Dish> = emptyList()
        var fail = false
        override suspend fun importPlace(place: Place, scans: List<NewMenuScan>, dishes: List<Dish>, createdAt: Long) {
            if (fail) error("disk full")
            this.place = place
            this.scans = scans
            this.dishes = dishes
        }
    }

    private class FakeImages(val fail: Boolean = false) : ImageProcessor {
        override suspend fun process(sourceUri: String, maxLongEdgePx: Int) = ProcessedImage(byteArrayOf(), null, null)
        override suspend fun reencode(jpegBytes: ByteArray, maxLongEdgePx: Int): ByteArray =
            if (fail) error("decode") else byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 9)
    }

    private fun importer(repo: FakeImport, photos: ScanMenuUseCaseTest.FakePhotos, images: ImageProcessor = FakeImages()): ImportPlaceUseCase {
        var n = 0
        return ImportPlaceUseCase(images, photos, repo, { "new-${++n}" }, { 5_000L })
    }

    private fun zip(vararg entries: Pair<String, ByteArray>) = writeZipEntries(entries.toList())

    private fun manifestBytes(json: String) = OPENBITE_MANIFEST to json.encodeToByteArray()

    private val minimal = """{"formatVersion":1,"place":{"name":"割烹 みなと"}}"""

    @Test
    fun roundTripKeepsShopAndMenu() = runTest {
        val photos = ScanMenuUseCaseTest.FakePhotos().apply {
            files["seed/menus/minato-1.jpg"] = jpeg
            files["photos/s2.jpg"] = jpeg
        }
        val exported = ExportPlaceUseCase(FakePlaces(minato, menu, scans), photos)("minato", ShareOptions(photos = true))!!
        assertEquals("みなと.openbite", exported.fileName)

        val pkg = OpenBiteCodec.decode(exported.bytes)
        assertEquals(listOf("photos/1.jpg", "photos/2.jpg"), pkg.manifest.photos)
        assertEquals("ja", pkg.manifest.menuLanguage)

        val repo = FakeImport()
        val target = ScanMenuUseCaseTest.FakePhotos()
        val placeId = importer(repo, target)(pkg)
        val place = repo.place!!
        assertEquals(placeId, place.id)
        assertEquals(minato.name, place.name)
        assertEquals(minato.address, place.address)
        assertEquals(minato.lat, place.lat)
        assertEquals(minato.lon, place.lon)
        assertEquals(minato.tags, place.tags)
        val strip = { d: Dish -> listOf(d.category, d.nameOriginal, d.nameTranslated, d.price, d.allergens) }
        assertEquals(menu.map(strip), repo.dishes.map(strip))
        assertTrue(repo.dishes.all { it.placeId == placeId && it.id != "d1" && it.id != "d2" })
        assertEquals(2, repo.scans.size)
        assertTrue(repo.scans.all { it.photoPath in target.files && it.model == ImportPlaceUseCase.IMPORT_MODEL })
    }

    @Test
    fun exportRespectsToggles() {
        val bare = buildManifest(minato, menu, scans, ShareOptions(menu = false, location = false, tags = false, photos = false))
        assertTrue(bare.dishes.isEmpty())
        assertNull(bare.place.address)
        assertNull(bare.place.lat)
        assertTrue(bare.place.tags.isEmpty())
        assertTrue(bare.photos.isEmpty())
        val preview = importPreview(buildManifest(minato, menu, scans, ShareOptions()))
        assertEquals(ImportPreview("割烹 みなと", "京都市中京区", listOf("适合约会", "纪念日"), 2, true, 0), preview)
    }

    @Test
    fun fileNamesAreSafe() {
        assertEquals("みなと.openbite", openBiteFileName("割烹 みなと"))
        assertEquals("ab.openbite", openBiteFileName("x a/b"))
        assertEquals("openbite-place.openbite", openBiteFileName(" ../ "))
    }

    @Test
    fun incomingTypeOrName() {
        assertTrue(acceptsIncoming(OPENBITE_MIME, null))
        assertTrue(acceptsIncoming("application/zip", "みなと.OPENBITE"))
        assertTrue(acceptsIncoming("application/octet-stream", "a.openbite"))
        assertTrue(acceptsIncoming(null, "a.openbite"))
        assertFalse(acceptsIncoming("application/zip", "photos.zip"))
        assertFalse(acceptsIncoming("image/jpeg", "a.openbite"))
    }

    private fun decodeError(bytes: ByteArray, limits: OpenBiteLimits = OpenBiteLimits()): ImportError =
        assertFailsWith<ImportException> { OpenBiteCodec.decode(bytes, limits) }.error

    @Test
    fun rejectsNonZipAndEmpty() {
        assertEquals(ImportError.NOT_ZIP, decodeError("hello".encodeToByteArray()))
        assertEquals(ImportError.MISSING_MANIFEST, decodeError(zip("photos/1.jpg" to jpeg)))
    }

    @Test
    fun rejectsZipSlipAndUnexpectedEntries() {
        assertEquals(ImportError.UNSAFE_PATH, decodeError(zip(manifestBytes(minimal), "../evil.jpg" to jpeg)))
        assertEquals(ImportError.UNSAFE_PATH, decodeError(zip(manifestBytes(minimal), "/data/x" to jpeg)))
        assertEquals(ImportError.UNSAFE_PATH, decodeError(zip(manifestBytes(minimal), "photos\\1.jpg" to jpeg)))
        assertEquals(ImportError.UNEXPECTED_ENTRY, decodeError(zip(manifestBytes(minimal), "script.sh" to jpeg)))
        assertEquals(ImportError.UNEXPECTED_ENTRY, decodeError(zip(manifestBytes(minimal), "photos/1.jpg" to jpeg)))
        assertEquals(ImportError.UNEXPECTED_ENTRY, decodeError(zip(manifestBytes(minimal), "photos/" to byteArrayOf())))
    }

    @Test
    fun enforcesSizeAndCountCaps() {
        val small = OpenBiteLimits(maxFileBytes = 50)
        assertEquals(ImportError.TOO_LARGE, decodeError(zip(manifestBytes(minimal)), small))
        // 高压缩率的大条目：按解出来的字节算。
        val bomb = zip(manifestBytes(minimal), "photos/1.jpg" to ByteArray(200_000))
        assertEquals(ImportError.TOO_LARGE, decodeError(bomb, OpenBiteLimits(maxPhotoBytes = 100_000)))
        val many = zip(manifestBytes(minimal), *(1..3).map { "photos/$it.jpg" to jpeg }.toTypedArray())
        assertEquals(ImportError.TOO_MANY_ENTRIES, decodeError(many, OpenBiteLimits(maxEntries = 2)))
    }

    @Test
    fun manifestMustParseAndHaveSupportedVersion() {
        assertEquals(ImportError.BAD_MANIFEST, decodeError(zip(manifestBytes("{not json"))))
        assertEquals(ImportError.BAD_MANIFEST, decodeError(zip(manifestBytes("""{"place":{"name":"a"}}"""))))
        assertEquals(ImportError.UNSUPPORTED_VERSION, decodeError(zip(manifestBytes("""{"formatVersion":2,"place":{"name":"a"}}"""))))
        assertEquals(ImportError.BAD_MANIFEST, decodeError(zip(OPENBITE_MANIFEST to byteArrayOf(0xC3.toByte(), 0x28))))
    }

    private fun fieldError(placeJson: String, dishesJson: String = "[]"): ImportError =
        decodeError(zip(manifestBytes("""{"formatVersion":1,"place":$placeJson,"dishes":$dishesJson}""")))

    @Test
    fun rejectsOutOfRangeFields() {
        val i = ImportError.INVALID_FIELD
        assertEquals(i, fieldError("""{"name":"  "}"""))
        assertEquals(i, fieldError("""{"name":"${"x".repeat(81)}"}"""))
        assertEquals(i, fieldError("""{"name":"a\u0007b"}"""))
        assertEquals(i, fieldError("""{"name":"a","lat":91,"lon":0}"""))
        assertEquals(i, fieldError("""{"name":"a","lat":35}"""))
        assertEquals(i, fieldError("""{"name":"a","tags":["${"x".repeat(13)}"]}"""))
        assertEquals(i, fieldError("""{"name":"a"}""", """[{"nameOriginal":"x","priceMinor":-1,"currency":"JPY"}]"""))
        assertEquals(i, fieldError("""{"name":"a"}""", """[{"nameOriginal":"x","priceMinor":100}]"""))
        assertEquals(i, fieldError("""{"name":"a"}""", """[{"nameOriginal":"x","priceMinor":100,"currency":"yen"}]"""))
        assertEquals(i, fieldError("""{"name":"a"}""", """[{"nameOriginal":"x","allergens":["Peanut!"]}]"""))
        assertEquals(i, fieldError("""{"name":"a"}""", """[{"nameOriginal":""}]"""))
    }

    @Test
    fun photosMustBeListedAndJpeg() {
        val listed = """{"formatVersion":1,"place":{"name":"a"},"photos":["photos/1.jpg"]}"""
        assertEquals(ImportError.BAD_PHOTO, decodeError(zip(manifestBytes(listed))))
        assertEquals(ImportError.BAD_PHOTO, decodeError(zip(manifestBytes(listed), "photos/1.jpg" to "GIF89a".encodeToByteArray())))
        val ok = OpenBiteCodec.decode(zip(manifestBytes(listed), "photos/1.jpg" to jpeg))
        assertEquals(1, ok.photos.size)
        val traversal = """{"formatVersion":1,"place":{"name":"a"},"photos":["../1.jpg"]}"""
        assertEquals(ImportError.UNEXPECTED_ENTRY, decodeError(zip(manifestBytes(traversal))))
    }

    @Test
    fun unknownKeysAreIgnored() {
        val pkg = OpenBiteCodec.decode(zip(manifestBytes("""{"formatVersion":1,"place":{"name":"a","extra":1},"future":true}""")))
        assertEquals("a", pkg.manifest.place.name)
    }

    @Test
    fun badPhotoOrDbFailureWritesNothing() = runTest {
        val pkg = OpenBitePackage(
            OpenBiteManifest(1, SharedPlace("a"), photos = listOf("photos/1.jpg")),
            listOf(SharedPhoto("photos/1.jpg", jpeg)),
        )
        val repo = FakeImport()
        val photos = ScanMenuUseCaseTest.FakePhotos()
        assertEquals(
            ImportError.BAD_PHOTO,
            assertFailsWith<ImportException> { importer(repo, photos, FakeImages(fail = true))(pkg) }.error,
        )
        assertNull(repo.place)
        assertTrue(photos.files.isEmpty())
        repo.fail = true
        assertEquals(ImportError.SAVE_FAILED, assertFailsWith<ImportException> { importer(repo, photos)(pkg) }.error)
        assertTrue(photos.files.isEmpty())
    }

    @Test
    fun openRejectsWrongTypeAndTooLarge() = runTest {
        val wrong = OpenSharedPlaceUseCase { _, _ -> IncomingFile("x.zip", "application/zip", byteArrayOf(), false) }
        assertEquals(ImportError.NOT_OPENBITE, assertFailsWith<ImportException> { wrong("content://x") }.error)
        val big = OpenSharedPlaceUseCase { _, _ -> IncomingFile("x.openbite", null, null, true) }
        assertEquals(ImportError.TOO_LARGE, assertFailsWith<ImportException> { big("content://x") }.error)
        val broken = OpenSharedPlaceUseCase { _, _ -> error("revoked") }
        assertEquals(ImportError.READ_FAILED, assertFailsWith<ImportException> { broken("content://x") }.error)
    }

    @Test
    fun mealCardHidesAmountAndHeadcount() {
        val log = DiningLog(
            id = "l", place = Place("p", "炭火焼鳥 とり松", null, null, "京都市中京区先斗町通"), visitedAt = 0, timezone = "Asia/Tokyo",
            localDate = LocalDate(2026, 11, 17), status = DiningStatus.DONE, milestoneId = null, note = "炭火味很足",
            source = LogSource.USER,
            items = (1..5).map { DiningLogItem("i$it", null, "原$it", Money(500, "JPY"), 1, 5, null, "菜$it") },
            companions = listOf(Person("w", "老婆")),
            rating = 5,
        )
        val shown = mealCard(log, showPlace = true, showAmount = true)
        assertEquals("京都", shown.city)
        assertEquals(listOf("菜1", "菜2", "菜3", "菜4"), shown.dishNames)
        assertEquals(1, shown.moreDishes)
        assertEquals(Money(2500, "JPY"), shown.amount)
        assertEquals(2, shown.headcount)
        assertEquals(5, shown.stars)
        val hidden = mealCard(log, showPlace = false, showAmount = false)
        assertNull(hidden.placeName)
        assertNull(hidden.city)
        assertNull(hidden.amount)
        assertNull(hidden.headcount)
        assertEquals("東京", cityOf("東京都渋谷区"))
        assertNull(cityOf("Shibuya"))
    }
}
