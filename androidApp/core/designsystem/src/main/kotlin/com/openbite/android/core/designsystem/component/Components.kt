package com.openbite.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.openbite.android.core.designsystem.R
import com.openbite.android.core.designsystem.icon.OpenBiteIcons
import com.openbite.android.core.designsystem.text.memorySupportingText
import com.openbite.android.core.designsystem.theme.FixedColors
import com.openbite.android.core.designsystem.theme.Spacing
import com.openbite.core.model.Glyph
import com.openbite.core.model.GlyphTone
import com.openbite.core.model.MemorySummary
import com.openbite.core.model.formatRating

/** 头像色调 -> （容器色，文字色），对应设计稿 tile(ch, bg, fg)。 */
@Composable
@ReadOnlyComposable
fun GlyphTone.colors(): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        GlyphTone.PRIMARY -> scheme.primaryContainer to scheme.onPrimaryContainer
        GlyphTone.SECONDARY -> scheme.secondaryContainer to scheme.onSecondaryContainer
        GlyphTone.TERTIARY -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        GlyphTone.PRIMARY_FIXED -> FixedColors.primaryFixedDim to FixedColors.onPrimaryFixed
        GlyphTone.NEUTRAL -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
    }
}

/** 单字头像：店铺用圆角方块，纪念日用圆形（[shape] 传 CircleShape）。 */
@Composable
fun GlyphAvatar(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    val (container, content) = glyph.tone.colors()
    Box(
        modifier = modifier
            .size(size)
            .background(container, shape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph.char, style = MaterialTheme.typography.titleMedium, color = content)
    }
}

/** 列表上方的小标题（title-small）。设置页用 primary 色，其余用 on-surface-variant。 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(start = Spacing.xxl, end = Spacing.s, top = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = color, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** 分段列表的圆角：首尾 16dp，中间 4dp（设计稿 .m3-list--segmented）。 */
fun segmentShape(index: Int, count: Int): Shape {
    val outer = 16.dp
    val inner = 4.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

/** 分段列表容器：条目之间 2dp 间隔，左右 16dp。 */
@Composable
fun SegmentedList(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        content = content,
    )
}

/** 分段列表里的一行（设计稿 M3.ListItem）。 */
@Composable
fun SegmentedListItem(
    headline: String,
    shape: Shape,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    /** 紧跟在标题后面的小标签（如店铺列表的“纪念日”）。 */
    headlineTrailing: (@Composable () -> Unit)? = null,
    headlineStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (supporting == null) 56.dp else 72.dp)
            .background(containerColor, shape)
            .let { if (onClick != null) it.clip(shape).clickable(onClick = onClick) else it }
            .padding(PaddingValues(horizontal = Spacing.l, vertical = Spacing.s)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(
                    headline,
                    modifier = Modifier.weight(1f, fill = false),
                    style = headlineStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                headlineTrailing?.invoke()
            }
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/** 行尾的小字，如“草稿”“111 天”“7 天”。 */
@Composable
fun TrailingText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 评分：“4.5 ★”。 */
@Composable
fun RatingDisplay(
    rating: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    starColor: Color = MaterialTheme.colorScheme.primary,
) {
    val description = stringResource(R.string.ds_rating_description, rating)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rating, style = MaterialTheme.typography.labelLarge, color = color)
        Box(Modifier.width(Spacing.xs))
        Icon(OpenBiteIcons.Star, contentDescription = null, tint = starColor, modifier = Modifier.size(18.dp))
    }
}

/** 一条用餐记忆：头像、店名、副标题、草稿或评分。首页和记忆列表共用。 */
@Composable
fun MemoryListItem(
    memory: MemorySummary,
    currentYear: Int,
    shape: Shape,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    SegmentedListItem(
        headline = memory.placeName,
        supporting = memorySupportingText(memory, currentYear),
        shape = shape,
        modifier = modifier,
        leading = { GlyphAvatar(memory.glyph) },
        trailing = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (memory.byAssistant) AssistantMark()
                if (memory.isDraft) {
                    TrailingText(stringResource(R.string.ds_memory_draft))
                } else {
                    memory.rating?.let { RatingDisplay(formatRating(it)) }
                }
            }
        },
        onClick = onClick,
    )
}

/** 小标记“助手记录”：这条记录是手机助手通过 AppFunctions 写的。列表和这一餐页面共用。 */
@Composable
fun AssistantMark(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.ds_assistant_mark),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 1,
    )
}

/** 纪念日头像是圆的。 */
val MilestoneAvatarShape: Shape = CircleShape
