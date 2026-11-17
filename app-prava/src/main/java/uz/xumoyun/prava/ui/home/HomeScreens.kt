package uz.xumoyun.prava.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import uz.xumoyun.core.ui.components.ActionTile
import uz.xumoyun.core.ui.components.ProgressRing
import uz.xumoyun.core.ui.components.SettingsRow
import uz.xumoyun.core.ui.theme.LocalExtraColors
import uz.xumoyun.core.ui.theme.TabularNumbers
import uz.xumoyun.prava.R
import uz.xumoyun.prava.data.PravaSettings
import uz.xumoyun.prava.data.QuizRepository
import uz.xumoyun.prava.data.db.TopicEntity
import uz.xumoyun.prava.data.db.TopicProgress
import uz.xumoyun.prava.domain.Category
import uz.xumoyun.prava.ui.PravaPreview
import uz.xumoyun.prava.ui.loc
import javax.inject.Inject

enum class TicketStatus { NEW, PASSED, FAILED }

data class HomeState(
    val readiness: Int? = null,
    val mistakes: Int = 0,
    val favorites: Int = 0,
    val category: Category = Category.B,
    val tickets: List<Pair<Int, TicketStatus>> = emptyList(),
    val topics: List<Pair<TopicEntity, TopicProgress?>> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(repository: QuizRepository, settings: PravaSettings) : ViewModel() {
    val state: StateFlow<HomeState> = combine(
        combine(repository.readiness, repository.mistakeIds, repository.favoriteIds, settings.category) { r, m, f, c -> HomeState(r, m.size, f.size, c) },
        combine(repository.tickets, repository.ticketResults) { t, results ->
            t.map { no -> no to (results.firstOrNull { it.ticketNo == no }?.let { if (it.passed) TicketStatus.PASSED else TicketStatus.FAILED } ?: TicketStatus.NEW) }
        },
        combine(repository.topics, repository.topicProgress) { topics, progress -> topics.map { t -> t to progress.firstOrNull { it.topicId == t.id } } },
    ) { base, tickets, topics -> base.copy(tickets = tickets, topics = topics) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())
}

@Composable
fun HomeRoute(
    onTickets: () -> Unit,
    onTopics: () -> Unit,
    onExam: () -> Unit,
    onMistakes: () -> Unit,
    onFavorites: () -> Unit,
    onStats: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(state, onTickets, onTopics, onExam, onMistakes, onFavorites, onStats)
}

@Composable
fun HomeContent(
    state: HomeState,
    onTickets: () -> Unit,
    onTopics: () -> Unit,
    onExam: () -> Unit,
    onMistakes: () -> Unit,
    onFavorites: () -> Unit,
    onStats: () -> Unit,
) {
    val extra = LocalExtraColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ProgressRing(progress = (state.readiness ?: 0) / 100f, size = 180.dp, color = MaterialTheme.colorScheme.primary) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.readiness), style = MaterialTheme.typography.bodySmall)
                Text(
                    state.readiness?.let { "$it%" } ?: "—",
                    style = MaterialTheme.typography.headlineLarge.merge(TabularNumbers),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            if (state.readiness == null) stringResource(R.string.readiness_hint_empty) else stringResource(R.string.readiness_hint, state.readiness),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile(Icons.AutoMirrored.Rounded.MenuBook, stringResource(R.string.tickets), Modifier.weight(1f), subtitle = stringResource(R.string.tickets_count, state.tickets.size), onClick = onTickets)
            ActionTile(Icons.Rounded.Checklist, stringResource(R.string.topics), Modifier.weight(1f), subtitle = stringResource(R.string.topics_count, state.topics.size), onClick = onTopics)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile(Icons.Rounded.Timer, stringResource(R.string.exam), Modifier.weight(1f), subtitle = stringResource(R.string.exam_subtitle), tint = extra.accent, onClick = onExam)
            ActionTile(Icons.Rounded.ErrorOutline, stringResource(R.string.my_mistakes), Modifier.weight(1f), subtitle = stringResource(R.string.count_questions, state.mistakes), tint = extra.failure, onClick = onMistakes)
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            SettingsRow(stringResource(R.string.favorites), value = stringResource(R.string.count_questions, state.favorites), icon = Icons.Rounded.Star, onClick = onFavorites)
            SettingsRow(stringResource(R.string.statistics), icon = Icons.Rounded.BarChart, onClick = onStats)
        }
    }
}

@Composable
fun TicketsRoute(onOpen: (Int) -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TicketsContent(state.tickets, onOpen)
}

@Composable
fun TicketsContent(tickets: List<Pair<Int, TicketStatus>>, onOpen: (Int) -> Unit) {
    val extra = LocalExtraColors.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(tickets, key = { it.first }) { (no, status) ->
            val color = when (status) {
                TicketStatus.NEW -> MaterialTheme.colorScheme.outlineVariant
                TicketStatus.PASSED -> extra.success
                TicketStatus.FAILED -> extra.failure
            }
            Card(
                onClick = { onOpen(no) },
                border = BorderStroke(2.dp, color),
                colors = CardDefaults.cardColors(containerColor = if (status == TicketStatus.NEW) MaterialTheme.colorScheme.surfaceContainerLow else color.copy(alpha = 0.10f)),
                modifier = Modifier.heightIn(min = 88.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.ticket_n, no), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            when (status) {
                                TicketStatus.NEW -> R.string.status_new
                                TicketStatus.PASSED -> R.string.status_passed
                                TicketStatus.FAILED -> R.string.status_failed
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status == TicketStatus.NEW) MaterialTheme.colorScheme.onSurfaceVariant else color,
                    )
                }
            }
        }
    }
}

@Composable
fun TopicsRoute(onOpen: (Int) -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TopicsContent(state.topics, onOpen)
}

@Composable
fun TopicsContent(topics: List<Pair<TopicEntity, TopicProgress?>>, onOpen: (Int) -> Unit) {
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(topics, key = { it.first.id }) { (topic, progress) ->
            Card(onClick = { onOpen(topic.id) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(loc(topic.nameUz, topic.nameRu), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.count_questions, progress?.total ?: 0), style = MaterialTheme.typography.bodySmall)
                    }
                    val total = progress?.total ?: 0
                    LinearProgressIndicator(progress = { if (total == 0) 0f else (progress?.seen ?: 0).toFloat() / total }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    PravaPreview { HomeContent(HomeState(readiness = 72, mistakes = 5, favorites = 2, tickets = listOf(1 to TicketStatus.PASSED)), {}, {}, {}, {}, {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun TicketsPreview() {
    PravaPreview { TicketsContent(listOf(1 to TicketStatus.PASSED, 2 to TicketStatus.FAILED, 3 to TicketStatus.NEW), {}) }
}
