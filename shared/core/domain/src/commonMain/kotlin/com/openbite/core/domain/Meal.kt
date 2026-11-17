package com.openbite.core.domain

import com.openbite.core.model.MealEdit
import com.openbite.core.data.MealRepository
import com.openbite.core.data.MilestoneRepository
import com.openbite.core.model.NewPhoto
import com.openbite.core.data.platform.ImageProcessor
import com.openbite.core.data.platform.PhotoStore
import com.openbite.core.data.platform.PreferencesStore
import com.openbite.core.data.platform.ReminderScheduler
import com.openbite.core.model.ReminderSettings
import com.openbite.core.data.platform.SecretKeys
import com.openbite.core.data.platform.SecretStore
import com.openbite.core.data.platform.TempFiles
import com.openbite.core.model.DiningLog
import com.openbite.core.model.DiningStatus
import com.openbite.core.model.Milestone
import com.openbite.core.model.MilestoneHighlight
import com.openbite.core.model.Money
import com.openbite.core.model.Person
import com.openbite.core.model.PhotoKind
import com.openbite.core.model.ScanError
import com.openbite.core.model.ScanException
import com.openbite.core.model.ModelUse
import com.openbite.core.network.ReceiptReadRequest
import com.openbite.core.model.ScannedReceipt
import com.openbite.core.network.VisionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 一餐的详情，加上编辑时要选的纪念日和同行人。 */
data class MealDetail(
    val log: DiningLog,
    val milestone: Milestone?,
    /** 那天是第几周年（首次那年为 0）。 */
    val milestoneYears: Int,
    val allMilestones: List<Milestone>,
    val allPersons: List<Person>,
)

/** 某一餐；记录被删掉时发 null。 */
class ObserveMealUseCase(private val meals: MealRepository, private val milestones: MilestoneRepository) {
    operator fun invoke(logId: String): Flow<MealDetail?> =
        combine(meals.observeMeal(logId), milestones.observeMilestones(), meals.observePersons()) { log, all, persons ->
            log?.let {
                val milestone = it.milestoneId?.let { id -> all.firstOrNull { m -> m.id == id } }
                MealDetail(it, milestone, milestone?.let { m -> it.localDate.year - m.date.year } ?: 0, all, persons)
            }
        }
}

/** 补记忆：存回去。[MealEdit.done] 为 true 时变成正式记忆。删掉的照片在数据库提交后再删文件。 */
class SaveMealUseCase(
    private val meals: MealRepository,
    private val photos: PhotoStore,
    private val now: NowProvider,
) {
    suspend operator fun invoke(edit: MealEdit, removedPhotoPaths: List<String>) {
        meals.save(edit.copy(updatedAt = now.nowMillis()))
        removedPhotoPaths.forEach { runCatching { photos.delete(it) } }
    }
}

class AddPersonUseCase(private val meals: MealRepository, private val ids: IdGenerator) {
    /** 空名字返回 null。 */
    suspend operator fun invoke(name: String): Person? {
        val trimmed = name.trim().takeIf { it.isNotEmpty() } ?: return null
        return Person(ids.newId(), trimmed).also { meals.addPerson(it) }
    }
}

/** 照片：去 EXIF、压缩、存到 photos/<id>.jpg。还没写进数据库，补记忆保存时才写。 */
class AddMealPhotoUseCase(
    private val images: ImageProcessor,
    private val photos: PhotoStore,
    private val ids: IdGenerator,
    private val now: NowProvider,
    private val tempFiles: TempFiles,
) {
    /** 存好的照片 + 处理后的 JPEG（小票要拿它去识别）。 */
    class Stored(val photo: NewPhoto, val jpegBytes: ByteArray)

    suspend operator fun invoke(sourceUri: String, kind: PhotoKind = PhotoKind.MEAL): Stored {
        val image = try {
            images.process(sourceUri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ScanException(ScanError.IMAGE_UNREADABLE, e)
        } finally {
            // 系统相机写的原图（带 EXIF/GPS）读完就删；要再加照片会重新拍。
            withContext(NonCancellable) { runCatching { tempFiles.discardCapture(sourceUri) } }
        }
        val id = ids.newId()
        return Stored(NewPhoto(id, photos.save(id, image.jpegBytes), kind, now.nowMillis()), image.jpegBytes)
    }
}

/** 放弃编辑时，把还没存进数据库的照片文件删掉。 */
class DiscardPhotosUseCase(private val photos: PhotoStore, private val appScope: ApplicationScope) {
    /** 删除不受调用方取消影响：页面刚好关掉时也会删完。 */
    suspend operator fun invoke(paths: List<String>) = withContext(NonCancellable) {
        paths.forEach { runCatching { photos.delete(it) } }
    }

    /** 不跟调用方的生命周期走，在应用级作用域里删；给 ViewModel.onCleared 这类马上要被取消的地方用。 */
    fun inBackground(paths: List<String>) {
        if (paths.isNotEmpty()) appScope.launch { invoke(paths) }
    }
}

/** 识别页不用了（离开、重拍）：删掉系统相机写的原图。在应用级作用域里删，不受页面取消影响。 */
class DiscardCaptureUseCase(private val tempFiles: TempFiles, private val appScope: ApplicationScope) {
    fun inBackground(sourceUri: String) {
        appScope.launch { runCatching { tempFiles.discardCapture(sourceUri) } }
    }
}

/** 读小票照片。演示模式用示例小票；没选模型抛 [ScanError.NO_MODEL]。返回用的模型，界面显示“由谁读的”。 */
class ReadReceiptUseCase(private val activeModel: ActiveVisionModel) {
    data class Result(val receipt: ScannedReceipt, val model: ModelUse)

    suspend operator fun invoke(jpegBytes: ByteArray, targetLang: String): Result {
        val active = activeModel.resolve()
        val receipt = try {
            active.model.readReceipt(ReceiptReadRequest(jpegBytes, targetLang, active.apiKey))
        } catch (e: VisionException) {
            throw ScanException(e.toScanError(), e)
        }
        return Result(receipt, active.use)
    }
}

/** 小票存档：换上新的小票照片（可以没有），记下实付金额。旧的小票照片文件一起删掉。 */
class SaveReceiptUseCase(
    private val meals: MealRepository,
    private val photos: PhotoStore,
    private val now: NowProvider,
) {
    suspend operator fun invoke(logId: String, photo: NewPhoto?, total: Money?) {
        val old = if (photo != null) meals.receiptPhotoPaths(logId) else emptyList()
        meals.saveReceipt(logId, photo, total, now.nowMillis())
        old.filter { it != photo?.path }.forEach { runCatching { photos.delete(it) } }
    }
}

/** 小票金额比点单多多少（税、服务费）。币种不同或缺金额时为 null。 */
fun receiptExtra(paid: Money?, ordered: Money?): Money? {
    if (paid == null || ordered == null || paid.currency != ordered.currency) return null
    return Money(paid.amountMinor - ordered.amountMinor, paid.currency).takeIf { it.amountMinor != 0L }
}

class ObserveKeepLocationUseCase(private val preferences: PreferencesStore) {
    operator fun invoke(): Flow<Boolean> = preferences.observeKeepLocation()
}

class SetKeepLocationUseCase(private val preferences: PreferencesStore) {
    suspend operator fun invoke(keep: Boolean) = preferences.setKeepLocation(keep)
}

class ObserveReminderSettingsUseCase(private val preferences: PreferencesStore) {
    operator fun invoke(): Flow<ReminderSettings> = preferences.observeReminderSettings()
}

class SetReminderSettingsUseCase(private val preferences: PreferencesStore, private val scheduler: ReminderScheduler) {
    suspend operator fun invoke(settings: ReminderSettings) {
        preferences.setReminderSettings(settings)
        if (settings.milestones) scheduler.scheduleMilestoneCheck()
    }
}

/** 饭后提醒到点时：记录还在、还是草稿、开关开着才提醒，返回那条记录；否则 null。 */
class MealFollowUpUseCase(private val meals: MealRepository, private val preferences: PreferencesStore) {
    suspend operator fun invoke(logId: String): DiningLog? {
        if (!preferences.observeReminderSettings().first().mealFollowUp) return null
        val log = meals.meal(logId) ?: return null
        return log.takeIf { it.status == DiningStatus.DRAFT }
    }
}

/** 纪念日提醒的去重键：每个纪念日每年一次。 */
fun milestoneReminderKey(highlight: MilestoneHighlight): String =
    "${highlight.upcoming.milestone.id}:${highlight.upcoming.nextDate.year}"

/** 该提醒的：进入提前提醒的天数范围（含当天），而且今年还没提醒过。 */
fun dueMilestoneReminders(highlights: List<MilestoneHighlight>, sent: Set<String>): List<MilestoneHighlight> =
    highlights.filter {
        it.upcoming.daysUntil in 0..it.upcoming.milestone.remindDaysBefore.coerceAtLeast(0) &&
            milestoneReminderKey(it) !in sent
    }

/** 每天的纪念日检查。先 [due] 拿到要发的，发完再 [markSent]。 */
class MilestoneRemindersUseCase(
    private val highlights: ObserveMilestoneHighlightsUseCase,
    private val preferences: PreferencesStore,
) {
    suspend fun due(): List<MilestoneHighlight> {
        if (!preferences.observeReminderSettings().first().milestones) return emptyList()
        // 提前提醒最多 60 天，查 60 天就够。
        return dueMilestoneReminders(highlights(daysAhead = 60).first(), preferences.sentMilestoneReminders())
    }

    suspend fun markSent(sent: List<MilestoneHighlight>) {
        if (sent.isNotEmpty()) preferences.markMilestoneRemindersSent(sent.map(::milestoneReminderKey).toSet())
    }
}
