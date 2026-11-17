package com.openbite.core.testing

import com.openbite.core.data.DiningLogRepository
import com.openbite.core.data.DraftRepository
import com.openbite.core.data.MealRepository
import com.openbite.core.data.MenuScanRepository
import com.openbite.core.data.MilestoneRepository
import com.openbite.core.data.NewDraftLog
import com.openbite.core.data.NewMenuScan
import com.openbite.core.data.PlaceCounts
import com.openbite.core.data.PlaceImportRepository
import com.openbite.core.data.PlaceRepository
import com.openbite.core.data.SampleDataRepository
import com.openbite.core.data.platform.ImageProcessor
import com.openbite.core.data.platform.IncomingFile
import com.openbite.core.data.platform.IncomingFileSource
import com.openbite.core.data.platform.PhotoStore
import com.openbite.core.data.platform.PreferencesStore
import com.openbite.core.data.platform.ProcessedImage
import com.openbite.core.data.platform.ReminderScheduler
import com.openbite.core.data.platform.SecretKeys
import com.openbite.core.data.platform.SecretStore
import com.openbite.core.data.platform.TempFiles
import com.openbite.core.domain.ApplicationScope
import com.openbite.core.domain.IdGenerator
import com.openbite.core.model.DiningLog
import com.openbite.core.model.DiningStatus
import com.openbite.core.model.Dish
import com.openbite.core.model.MealEdit
import com.openbite.core.model.MenuScan
import com.openbite.core.model.Milestone
import com.openbite.core.model.MilestoneWithPeople
import com.openbite.core.model.Money
import com.openbite.core.model.NewPhoto
import com.openbite.core.model.Person
import com.openbite.core.model.Place
import com.openbite.core.model.ReminderSettings
import com.openbite.core.model.TasteProfile
import com.openbite.core.model.VisionModelChoice
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

// 各模块测试共用的 Fake。都是内存实现，状态公开，测试直接读写。
// 只读场景传 readOnly = true：写操作直接失败，防止测试误写。

/** id-1、id-2……；[prefix] 可改。 */
fun sequentialIds(prefix: String = "id-"): IdGenerator {
    var n = 0
    return IdGenerator { "$prefix${++n}" }
}

/** 应用级作用域：测试里传 TestScope（或 backgroundScope），删除照片之类的后台活跟着测试调度。 */
fun testAppScope(scope: CoroutineScope) = ApplicationScope(scope)

/** 不联网：任何请求都返回 401。 */
fun noNetworkClient(): HttpClient = HttpClient(MockEngine { respondError(HttpStatusCode.Unauthorized) })

class FakePreferences(initialModel: VisionModelChoice? = null) : PreferencesStore {
    val visionModel = MutableStateFlow(initialModel)
    val taste = MutableStateFlow(TasteProfile.EMPTY)
    val reminderSettings = MutableStateFlow(ReminderSettings())
    val sent = mutableSetOf<String>()
    val keepLocation = MutableStateFlow(true)
    var seedDone = false

    override fun observeVisionModel(): Flow<VisionModelChoice?> = visionModel
    override suspend fun setVisionModel(choice: VisionModelChoice) { visionModel.value = choice }
    override fun observeTasteProfile(): Flow<TasteProfile> = taste
    override suspend fun setTasteProfile(profile: TasteProfile) { taste.value = profile }
    override fun observeReminderSettings(): Flow<ReminderSettings> = reminderSettings
    override suspend fun setReminderSettings(settings: ReminderSettings) { reminderSettings.value = settings }
    override suspend fun sentMilestoneReminders(): Set<String> = sent.toSet()
    override suspend fun markMilestoneRemindersSent(keys: Set<String>) { sent += keys }
    override suspend fun clearMilestoneReminders(milestoneId: String) { sent.removeAll { it.startsWith("$milestoneId:") } }
    override fun observeKeepLocation(): Flow<Boolean> = keepLocation
    override suspend fun setKeepLocation(keep: Boolean) { keepLocation.value = keep }
    override suspend fun isSeedAutoImported() = seedDone
    override suspend fun markSeedAutoImported() { seedDone = true }
}

class FakeSecrets(apiKey: String? = null) : SecretStore {
    val values = MutableStateFlow(apiKey?.let { mapOf(SecretKeys.ANTHROPIC_API_KEY to it) } ?: emptyMap())
    override fun observe(key: String): Flow<String?> = values.map { it[key] }
    override suspend fun get(key: String) = values.value[key]
    override suspend fun put(key: String, value: String) { values.update { it + (key to value) } }
    override suspend fun remove(key: String) { values.update { it - key } }
}

class FakePhotoStore : PhotoStore {
    val files = mutableMapOf<String, ByteArray>()
    val deleted = mutableListOf<String>()
    override suspend fun save(id: String, jpegBytes: ByteArray) = "photos/$id.jpg".also { files[it] = jpegBytes }
    override suspend fun delete(relativePath: String) {
        deleted += relativePath
        files.remove(relativePath)
    }
    override suspend fun deleteAll() = files.clear()
    override suspend fun read(relativePath: String) = files[relativePath]
}

class FakeTempFiles : TempFiles {
    val discarded = mutableListOf<String>()
    var cleared = 0
    override suspend fun discardCapture(sourceUri: String) { discarded += sourceUri }
    override suspend fun clearAll() { cleared++ }
}

/**
 * 处理照片：返回固定的 JPEG 字节和 [gps]。[fail] 为 true 时读不出来。
 * [gate] 不为 null 时 process 会等它完成，用来卡住“正在处理照片”的时刻。
 */
class FakeImageProcessor(
    var gps: Pair<Double, Double>? = null,
    var fail: Boolean = false,
    var reencodeFails: Boolean = false,
) : ImageProcessor {
    var gate: CompletableDeferred<Unit>? = null
    val processed = mutableListOf<String>()

    override suspend fun process(sourceUri: String, maxLongEdgePx: Int): ProcessedImage {
        gate?.await()
        processed += sourceUri
        if (fail) error("unreadable")
        return ProcessedImage(JPEG, gps?.first, gps?.second)
    }

    override suspend fun reencode(jpegBytes: ByteArray, maxLongEdgePx: Int): ByteArray =
        if (reencodeFails) error("decode") else JPEG

    companion object {
        val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 9)
    }
}

class FakeReminders : ReminderScheduler {
    val meals = mutableListOf<Pair<String, Int>>()
    var milestoneChecks = 0
    override fun scheduleMealFollowUp(logId: String, delayMinutes: Int) { meals += logId to delayMinutes }
    override fun scheduleMilestoneCheck() { milestoneChecks++ }
}

class FakeDrafts : DraftRepository {
    val created = mutableListOf<Pair<Place?, NewDraftLog>>()
    val place: Place? get() = created.lastOrNull()?.first
    val log: NewDraftLog? get() = created.lastOrNull()?.second
    var fail = false
    override suspend fun createDraft(newPlace: Place?, log: NewDraftLog) {
        if (fail) error("disk full")
        created += newPlace to log
    }
}

class FakeSampleData(var hasData: Boolean = false) : SampleDataRepository {
    var loads = 0
    var clears = 0
    var fail = false
    override suspend fun loadSampleIfEmpty(): Boolean {
        if (fail) error("seed broken")
        loads++
        return (!hasData).also { hasData = true }
    }
    override suspend fun clearAll() {
        clears++
        hasData = false
    }
}

class FakeLogs(val logs: MutableStateFlow<List<DiningLog>> = MutableStateFlow(emptyList())) : DiningLogRepository {
    constructor(initial: List<DiningLog>) : this(MutableStateFlow(initial))

    override fun observeLogs(): Flow<List<DiningLog>> = logs
    override suspend fun searchIds(pattern: String, limit: Int) = emptyList<String>()
    override suspend fun logsOfMilestone(milestoneId: String) = logs.value.filter { it.milestoneId == milestoneId }
}

class FakePlaces(
    places: List<Place> = emptyList(),
    menus: Map<String, List<Dish>> = emptyMap(),
    scans: Map<String, List<MenuScan>> = emptyMap(),
    private val readOnly: Boolean = false,
) : PlaceRepository {
    val places = MutableStateFlow(places)
    val menus = MutableStateFlow(menus)
    val scans = MutableStateFlow(scans)

    private fun write(block: () -> Unit) {
        check(!readOnly) { "read only" }
        block()
    }

    private fun updatePlace(placeId: String, transform: (Place) -> Place) =
        places.update { all -> all.map { if (it.id == placeId) transform(it) else it } }

    override fun observePlaces(): Flow<List<Place>> = places
    override fun observePlace(placeId: String): Flow<Place?> = places.map { all -> all.firstOrNull { it.id == placeId } }
    override fun observeMenu(placeId: String): Flow<List<Dish>> = menus.map { it[placeId].orEmpty() }
    override fun observeScans(placeId: String): Flow<List<MenuScan>> = scans.map { it[placeId].orEmpty() }
    override fun observeCounts(): Flow<Map<String, PlaceCounts>> =
        places.map { all -> all.associate { it.id to PlaceCounts(menus.value[it.id].orEmpty().size, scans.value[it.id].orEmpty().size) } }
    override suspend fun rename(placeId: String, name: String) = write { updatePlace(placeId) { it.copy(name = name) } }
    override suspend fun setLocation(placeId: String, lat: Double, lon: Double) = write { updatePlace(placeId) { it.copy(lat = lat, lon = lon) } }
    override suspend fun updateDish(dishId: String, nameTranslated: String, category: String, price: Money?) = write {
        menus.update { m -> m.mapValues { (_, dishes) -> dishes.map { if (it.id == dishId) it.copy(nameTranslated = nameTranslated, category = category, price = price) else it } } }
    }
    override suspend fun searchIds(pattern: String, limit: Int) = emptyList<String>()
    override suspend fun addTag(placeId: String, tag: String) = write { updatePlace(placeId) { it.copy(tags = (it.tags + tag).distinct()) } }
    override suspend fun removeTag(placeId: String, tag: String) = write { updatePlace(placeId) { it.copy(tags = it.tags - tag) } }
    override fun observeAllTags(): Flow<List<String>> = places.map { all -> all.flatMap { it.tags }.distinct() }
}

class FakeMenuScans(
    val places: MutableMap<String, Place> = mutableMapOf(),
    val menus: Map<String, List<Dish>> = emptyMap(),
) : MenuScanRepository {
    var saved: Triple<Place?, NewMenuScan, List<Dish>>? = null
    var fail = false
    override suspend fun place(placeId: String) = places[placeId]
    override suspend fun menuOf(placeId: String) = menus[placeId].orEmpty()
    override suspend fun save(newPlace: Place?, scan: NewMenuScan, dishes: List<Dish>) {
        if (fail) error("disk full")
        saved = Triple(newPlace, scan, dishes)
    }
}

class FakeMilestones(
    initial: List<MilestoneWithPeople> = emptyList(),
    private val readOnly: Boolean = false,
) : MilestoneRepository {
    val rows = MutableStateFlow(initial.associateBy { it.milestone.id })
    val tagged = mutableMapOf<String, Int>()

    override fun observeMilestones(): Flow<List<Milestone>> = rows.map { m -> m.values.map { it.milestone } }
    override suspend fun milestone(id: String) = rows.value[id]
    override suspend fun save(milestone: Milestone, personIds: List<String>) {
        check(!readOnly) { "read only" }
        rows.update { it + (milestone.id to MilestoneWithPeople(milestone, personIds)) }
    }
    override suspend fun delete(id: String) {
        check(!readOnly) { "read only" }
        rows.update { it - id }
        tagged.remove(id)
    }
    override suspend fun taggedMealCount(id: String) = tagged[id] ?: 0
}

/**
 * 补记忆和小票存档用的记录。[saveGate] 不为 null 时 save 会等它完成，用来卡住“正在保存”的时刻；
 * [failSave] 为 true 时保存失败。
 */
class FakeMeals(
    logs: List<DiningLog> = emptyList(),
    persons: List<Person> = emptyList(),
    private val readOnly: Boolean = false,
) : MealRepository {
    val logs = MutableStateFlow(logs.associateBy { it.id })
    val persons = MutableStateFlow(persons)
    val saved = mutableListOf<MealEdit>()
    val receipts = mutableListOf<Triple<String, NewPhoto?, Money?>>()
    var saveGate: CompletableDeferred<Unit>? = null
    var failSave = false

    override fun observeMeal(logId: String): Flow<DiningLog?> = logs.map { it[logId] }
    override suspend fun meal(logId: String): DiningLog? = logs.value[logId]
    override suspend fun statusOf(logId: String): DiningStatus? = logs.value[logId]?.status
    override fun observePersons(): Flow<List<Person>> = persons
    override suspend fun addPerson(person: Person) {
        check(!readOnly) { "read only" }
        persons.update { it + person }
    }
    override suspend fun save(edit: MealEdit) {
        check(!readOnly) { "read only" }
        saveGate?.await()
        if (failSave) error("disk full")
        saved += edit
    }
    override suspend fun receiptPhotoPaths(logId: String): List<String> =
        logs.value[logId]?.photos.orEmpty().filter { it.kind == com.openbite.core.model.PhotoKind.RECEIPT }.map { it.path }
    override suspend fun saveReceipt(logId: String, photo: NewPhoto?, total: Money?, updatedAt: Long) {
        check(!readOnly) { "read only" }
        if (failSave) error("disk full")
        receipts += Triple(logId, photo, total)
    }
}

class FakeImport : PlaceImportRepository {
    var place: Place? = null
    var scans: List<NewMenuScan> = emptyList()
    var dishes: List<Dish> = emptyList()
    var imports = 0
    var fail = false
    override suspend fun importPlace(place: Place, scans: List<NewMenuScan>, dishes: List<Dish>, createdAt: Long) {
        if (fail) error("disk full")
        imports++
        this.place = place
        this.scans = scans
        this.dishes = dishes
    }
}

/** 收到的文件：按 uri 返回 [files] 里的内容，没有就读失败。 */
class FakeIncomingFiles(val files: MutableMap<String, IncomingFile> = mutableMapOf()) : IncomingFileSource {
    override suspend fun read(uri: String, maxBytes: Long): IncomingFile = files[uri] ?: error("revoked")
}
