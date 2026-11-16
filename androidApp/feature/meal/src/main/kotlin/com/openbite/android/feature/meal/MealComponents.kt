package com.openbite.android.feature.meal

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.openbite.android.core.designsystem.component.PhotoLoad
import com.openbite.android.core.designsystem.component.rememberLocalPhoto
import com.openbite.android.core.designsystem.icon.OpenBiteIcons
import com.openbite.android.core.designsystem.text.hhmm
import com.openbite.android.core.designsystem.text.monthDay
import com.openbite.core.model.DiningLog
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import com.openbite.android.core.designsystem.R as DsR

/** 1–5 颗星。[value] 为 null 是没打分；点已选中的那颗会取消（逻辑在编辑器里）。 */
@Composable
internal fun StarRow(value: Int?, onPick: ((Int) -> Unit)?, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.edit_meal_rating)
    Row(modifier.semantics { contentDescription = label }, verticalAlignment = Alignment.CenterVertically) {
        (1..5).forEach { n ->
            val on = value != null && n <= value
            val tint = if (on) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
            val icon = if (on) OpenBiteIcons.Star else OpenBiteIcons.StarBorder
            if (onPick != null) {
                IconButton(onClick = { onPick(n) }, modifier = Modifier.size(size + 8.dp)) {
                    Icon(icon, contentDescription = stringResource(R.string.edit_star, n), tint = tint, modifier = Modifier.size(size))
                }
            } else {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
            }
        }
    }
}

/** 一张本地照片的方形缩略图。 */
@Composable
internal fun PhotoThumb(path: String, modifier: Modifier = Modifier, size: Dp = 80.dp, description: String? = null) {
    val load by rememberLocalPhoto(path)
    Box(
        modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        when (val current = load) {
            PhotoLoad.Loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            PhotoLoad.Failed -> Icon(OpenBiteIcons.Close, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            is PhotoLoad.Loaded -> Image(current.bitmap, contentDescription = description, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

/** “今晚 19:42”“今天 12:10”，或“11月15日 15:20”。 */
@OptIn(ExperimentalTime::class)
@Composable
internal fun mealTimeLabel(log: DiningLog, currentYear: Int): String {
    val zone = runCatching { TimeZone.of(log.timezone) }.getOrDefault(TimeZone.currentSystemDefault())
    val time = Instant.fromEpochMilliseconds(log.visitedAt).toLocalDateTime(zone).time
    val isToday = log.localDate == Clock.System.todayIn(zone)
    return when {
        isToday && time.hour >= 17 -> stringResource(DsR.string.ds_tonight_time, time.hhmm())
        isToday -> stringResource(DsR.string.ds_today_time, time.hhmm())
        else -> monthDay(log.localDate, currentYear) + " " + time.hhmm()
    }
}
