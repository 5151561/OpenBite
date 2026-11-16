package com.openbite.android.feature.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openbite.core.model.DiningLog
import com.openbite.core.model.UpcomingMilestone
import com.openbite.feature.journal.JournalUiState
import com.openbite.feature.journal.JournalViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun JournalRoute(viewModel: JournalViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    JournalScreen(state)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(state: JournalUiState) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.journal_title)) }) },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.upcoming, key = { "m-" + it.milestone.id }) { UpcomingCard(it) }
            if (state.logs.isEmpty()) {
                item { Text(stringResource(R.string.journal_empty)) }
            }
            items(state.logs, key = { it.id }) { LogCard(it) }
        }
    }
}

@Composable
private fun UpcomingCard(item: UpcomingMilestone) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(item.milestone.title, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.journal_upcoming, item.daysUntil, item.yearsSince))
        }
    }
}

@Composable
private fun LogCard(log: DiningLog) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(log.place.name, style = MaterialTheme.typography.titleMedium)
            Text(log.localDate.toString(), style = MaterialTheme.typography.bodySmall)
            log.items.forEach { item ->
                val stars = item.rating?.let { " " + "★".repeat(it) } ?: ""
                Text("· ${item.name} ×${item.qty}$stars")
            }
            log.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
