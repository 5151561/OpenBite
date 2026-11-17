package com.openbite.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openbite.android.core.designsystem.component.SectionHeader
import com.openbite.android.core.designsystem.component.SegmentedList
import com.openbite.android.core.designsystem.component.SegmentedListItem
import com.openbite.android.core.designsystem.component.TasteEditor
import com.openbite.android.core.designsystem.component.segmentShape
import com.openbite.android.core.designsystem.icon.OpenBiteIcons
import com.openbite.android.core.designsystem.theme.OpenBiteTheme
import com.openbite.android.core.designsystem.theme.Spacing
import com.openbite.feature.settings.tastePreview
import com.openbite.core.domain.TasteNote
import com.openbite.core.model.Allergen
import com.openbite.core.model.AvoidItem
import com.openbite.core.model.SpiceLevel
import com.openbite.core.model.TasteProfile
import com.openbite.feature.settings.TasteUiState
import com.openbite.feature.settings.TasteViewModel
import org.koin.compose.viewmodel.koinViewModel

/** 我的口味（设计稿 15）。改了立刻保存。 */
@Composable
fun TasteRoute(onBack: () -> Unit, viewModel: TasteViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TasteScreen(
        state = state,
        onBack = onBack,
        onToggleAllergen = viewModel::toggle,
        onToggleAvoid = viewModel::toggle,
        onSpice = viewModel::setSpice,
        onAutoApply = viewModel::setAutoApply,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasteScreen(
    state: TasteUiState,
    onBack: () -> Unit = {},
    onToggleAllergen: (Allergen) -> Unit = {},
    onToggleAvoid: (AvoidItem) -> Unit = {},
    onSpice: (SpiceLevel) -> Unit = {},
    onAutoApply: (Boolean) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.taste_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(OpenBiteIcons.ArrowBack, contentDescription = stringResource(R.string.taste_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.xxl),
        ) {
            TasteEditor(state.profile, onToggleAllergen, onToggleAvoid, onSpice)
            SegmentedList {
                SegmentedListItem(
                    headline = stringResource(R.string.taste_auto_apply),
                    shape = segmentShape(0, 1),
                    trailing = { Switch(checked = state.profile.autoApply, onCheckedChange = onAutoApply) },
                    onClick = { onAutoApply(!state.profile.autoApply) },
                )
            }
            SectionHeader(stringResource(R.string.taste_preview), color = MaterialTheme.colorScheme.primary)
            ReceiptPreview(state.preview)
            Text(
                stringResource(R.string.taste_note),
                modifier = Modifier.padding(horizontal = Spacing.xxl, vertical = Spacing.s),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 小票上口味那一块的预览：过敏用 error-container 醒目显示。 */
@Composable
private fun ReceiptPreview(notes: List<TasteNote>) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .padding(horizontal = Spacing.l, vertical = Spacing.s)
            .fillMaxWidth()
            .background(scheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        if (notes.isEmpty()) {
            Text(stringResource(R.string.taste_preview_empty), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        notes.forEach { note ->
            val container = if (note.isAllergy) scheme.errorContainer else scheme.surfaceContainer
            val content = if (note.isAllergy) scheme.onErrorContainer else scheme.onSurface
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(container, MaterialTheme.shapes.medium)
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
            ) {
                Text(note.phrase.primary, style = MaterialTheme.typography.titleMedium, color = content)
                note.phrase.secondary?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = if (note.isAllergy) content else scheme.onSurfaceVariant)
                }
            }
        }
    }
}

private val designProfile = TasteProfile(
    allergens = listOf(Allergen.PEANUT),
    avoids = listOf(AvoidItem.GREEN_ONION, AvoidItem.CILANTRO),
    spice = SpiceLevel.MILD,
)

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun TasteScreenPreview() {
    OpenBiteTheme(darkTheme = false) {
        TasteScreen(TasteUiState(false, designProfile, tastePreview(designProfile, "zh-CN")))
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun TasteScreenDarkPreview() {
    OpenBiteTheme(darkTheme = true) {
        TasteScreen(TasteUiState(false, designProfile, tastePreview(designProfile, "zh-CN")))
    }
}
