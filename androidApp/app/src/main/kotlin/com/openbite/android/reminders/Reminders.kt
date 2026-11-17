package com.openbite.android.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.openbite.android.MainActivity
import com.openbite.android.R
import com.openbite.android.core.designsystem.text.PlainText
import com.openbite.core.data.platform.ReminderScheduler
import com.openbite.core.domain.MealFollowUpUseCase
import com.openbite.core.domain.MilestoneRemindersUseCase
import com.openbite.core.model.MilestoneHighlight
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** 用 WorkManager 排提醒。进程被杀、重启手机后 WorkManager 会接着跑。 */
class WorkManagerReminderScheduler(private val context: Context) : ReminderScheduler {
    override fun scheduleMealFollowUp(logId: String, delayMinutes: Int) {
        val request = OneTimeWorkRequestBuilder<MealFollowUpWorker>()
            .setInitialDelay(delayMinutes.toLong(), TimeUnit.MINUTES)
            .setInputData(workDataOf(MealFollowUpWorker.KEY_LOG_ID to logId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(mealWorkName(logId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancelMealFollowUp(logId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(mealWorkName(logId))
        NotificationManagerCompat.from(context).cancel(Notifications.mealNotificationId(logId))
    }

    override fun scheduleMilestoneCheck() {
        val request = PeriodicWorkRequestBuilder<MilestoneCheckWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(MILESTONE_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        const val MILESTONE_WORK = "milestone-check"
        fun mealWorkName(logId: String) = "meal-follow-up-$logId"
    }
}

/** 通知渠道、发通知、点通知回到 App 的 Intent。 */
object Notifications {
    const val CHANNEL_MEAL = "meal_follow_up"
    const val CHANNEL_MILESTONE = "milestones"

    const val EXTRA_EDIT_LOG = "openbite.edit_log"
    const val EXTRA_VIEW_LOG = "openbite.view_log"
    const val EXTRA_MILESTONES = "openbite.milestones"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_MEAL, context.getString(R.string.channel_meal), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.channel_meal_description) },
                NotificationChannel(CHANNEL_MILESTONE, context.getString(R.string.channel_milestone), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.channel_milestone_description) },
            ),
        )
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun openApp(context: Context, requestCode: Int, fill: Intent.() -> Unit): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            fill()
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun mealNotificationId(logId: String) = "meal:$logId".hashCode()

    @OptIn(ExperimentalTime::class)
    @Suppress("MissingPermission")
    fun postMealFollowUp(context: Context, logId: String, placeName: String, dishCount: Int, visitedAt: Long, timezone: String) {
        if (!canPost(context)) return
        val hour = Instant.fromEpochMilliseconds(visitedAt).toLocalDateTime(runCatching { TimeZone.of(timezone) }.getOrDefault(TimeZone.currentSystemDefault())).hour
        val title = context.getString(if (hour >= 17) R.string.notify_meal_title_tonight else R.string.notify_meal_title_today, placeName)
        val id = mealNotificationId(logId)
        val open = openApp(context, id) { putExtra(EXTRA_EDIT_LOG, logId) }
        val snooze = PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, SnoozeReceiver::class.java).putExtra(SnoozeReceiver.KEY_LOG_ID, logId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_MEAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notify_meal_text, dishCount))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notify_meal_text, dishCount)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.notify_meal_open), open)
            .addAction(0, context.getString(R.string.notify_meal_snooze), snooze)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    @Suppress("MissingPermission")
    fun postMilestone(context: Context, highlight: MilestoneHighlight) {
        if (!canPost(context)) return
        val res = context.resources
        val upcoming = highlight.upcoming
        val name = PlainText.milestoneTitle(res, upcoming.milestone, upcoming.yearsSince)
        val title = when (upcoming.daysUntil) {
            0 -> res.getString(R.string.notify_milestone_today, name)
            1 -> res.getString(R.string.notify_milestone_tomorrow, name)
            else -> res.getString(R.string.notify_milestone_days, upcoming.daysUntil, PlainText.monthDayWeekday(res, upcoming.nextDate), name)
        }
        val previous = highlight.previous
        val text = when {
            previous == null -> res.getString(R.string.notify_milestone_text_first)
            previous.note != null -> res.getString(R.string.notify_milestone_text_note, previous.placeName, previous.note)
            else -> res.getString(R.string.notify_milestone_text, previous.placeName)
        }
        val id = "milestone:${upcoming.milestone.id}".hashCode()
        val open = openApp(context, id) {
            putExtra(EXTRA_MILESTONES, true)
            previous?.let { putExtra(EXTRA_VIEW_LOG, it.logId) }
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_MILESTONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
        if (previous != null) builder.addAction(0, res.getString(R.string.notify_milestone_open), open)
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }
}

/** 下单 90 分钟后：还是草稿就提醒补记忆。 */
class MealFollowUpWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val followUp: MealFollowUpUseCase by inject()

    override suspend fun doWork(): Result {
        val logId = inputData.getString(KEY_LOG_ID) ?: return Result.success()
        val log = followUp(logId) ?: return Result.success()
        Notifications.postMealFollowUp(
            applicationContext,
            logId,
            log.place.name,
            log.items.sumOf { it.qty },
            log.visitedAt,
            log.timezone,
        )
        return Result.success()
    }

    companion object {
        const val KEY_LOG_ID = "logId"
    }
}

/** 每天一次：到了提前提醒的日子就发纪念日提醒，每个纪念日每年一次。 */
class MilestoneCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val reminders: MilestoneRemindersUseCase by inject()

    override suspend fun doWork(): Result {
        // 没有通知权限时不标记“已发”，等用户开了权限，第二天还能收到。
        if (!Notifications.canPost(applicationContext)) return Result.success()
        val due = reminders.due()
        due.forEach { Notifications.postMilestone(applicationContext, it) }
        reminders.markSent(due)
        return Result.success()
    }
}

/** 通知上的“一小时后提醒”。 */
class SnoozeReceiver : BroadcastReceiver(), KoinComponent {
    private val scheduler: ReminderScheduler by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val logId = intent.getStringExtra(KEY_LOG_ID) ?: return
        NotificationManagerCompat.from(context).cancel(Notifications.mealNotificationId(logId))
        scheduler.scheduleMealFollowUp(logId, SNOOZE_MINUTES)
    }

    companion object {
        const val KEY_LOG_ID = "logId"
        const val SNOOZE_MINUTES = 60
    }
}
