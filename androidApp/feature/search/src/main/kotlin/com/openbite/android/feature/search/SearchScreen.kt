package com.openbite.android.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.WindowInsetsRulers
import androidx.compose.foundation.layout.fitInside
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openbite.android.core.designsystem.component.GlyphAvatar
import com.openbite.android.core.designsystem.component.MemoryListItem
import com.openbite.android.core.designsystem.component.SegmentedListItem
import com.openbite.android.core.designsystem.component.segmentShape
import com.openbite.android.core.designsystem.icon.OpenBiteIcons
import com.openbite.android.core.designsystem.theme.Spacing
import com.openbite.feature.search.SearchKind
import com.openbite.feature.search.SearchResults
import com.openbite.feature.search.SearchUiState
import com.openbite.feature.search.SearchViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** 搜索页：记忆 Tab 搜一顿饭，店铺 Tab 搜一家店。点结果交给 app 打开。 */
@Composable
fun SearchRoute(
    places: Boolean,
    onBack: () -> Unit,
    onOpenMemory: (logId: String, isDraft: Boolean) -> Unit,
    onOpenPlace: (placeId: String) -> Unit,
    viewModel: SearchViewModel = koinViewModel(parameters = { parametersOf(if (places) SearchKind.PLACES else SearchKind.MEMORIES) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SearchScreen(state, viewModel::setQuery, onBack, onOpenMemory, onOpenPlace)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onOpenMemory: (String, Boolean) -> Unit,
    onOpenPlace: (String) -> Unit,
) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(OpenBiteIcons.ArrowBack, contentDescription = stringResource(R.string.search_back)) } },
                title = {
                    Box(Modifier.fillMaxWidth()) {
                        if (state.query.isEmpty()) {
                            Text(
                                stringResource(if (state.kind == SearchKind.MEMORIES) R.string.search_memories_hint else R.string.search_places_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        BasicTextField(
                            value = state.query,
                            onValueChange = onQuery,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                            cursorBrush = SolidColor(scheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                },
                actions = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) { Icon(OpenBiteIcons.Close, contentDescription = stringResource(R.string.search_clear)) }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).fitInside(WindowInsetsRulers.Ime.current), contentPadding = PaddingValues(bottom = Spacing.xxl)) {
            when (val r = state.results) {
                SearchResults.Idle -> item {
                    Hint(stringResource(if (state.kind == SearchKind.MEMORIES) R.string.search_memories_idle else R.string.search_places_idle))
                }
                SearchResults.Searching -> item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = Spacing.l)) }
                is SearchResults.Memories -> {
                    if (r.items.isEmpty()) item { Hint(stringResource(R.string.search_no_result, state.query.trim())) }
                    itemsIndexed(r.items, key = { _, it -> it.logId }) { index, memory ->
                        MemoryListItem(
                            memory = memory,
                            currentYear = state.currentYear,
                            shape = segmentShape(index, r.items.size),
                            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xxs / 2),
                            onClick = { onOpenMemory(memory.logId, memory.isDraft) },
                        )
                    }
                }
                is SearchResults.Places -> {
                    if (r.items.isEmpty()) item { Hint(stringResource(R.string.search_no_result, state.query.trim())) }
                    itemsIndexed(r.items, key = { _, it -> it.place.id }) { index, summary ->
                        SegmentedListItem(
                            headline = summary.place.name,
                            supporting = stringResource(R.string.search_place_meta, summary.dishCount, summary.visitCount),
                            shape = segmentShape(index, r.items.size),
                            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xxs / 2),
                            leading = { GlyphAvatar(summary.glyph) },
                            onClick = { onOpenPlace(summary.place.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = Spacing.xxl, vertical = Spacing.l),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
