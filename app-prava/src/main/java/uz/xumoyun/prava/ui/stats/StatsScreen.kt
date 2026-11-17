package uz.xumoyun.prava.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
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
import uz.xumoyun.core.ui.components.EmptyState
import uz.xumoyun.core.ui.components.StatTile
import uz.xumoyun.core.ui.theme.LocalExtraColors
import uz.xumoyun.prava.R
import uz.xumoyun.prava.data.OverallStats
import uz.xumoyun.prava.data.QuizRepository
import uz.xumoyun.prava.data.db.DayStat
import uz.xumoyun.prava.ui.PravaPreview
import uz.xumoyun.prava.ui.loc
import javax.inject.Inject

data class WeakTopic(val nameUz: String, val nameRu: String, val percentWrong: Int)

data class StatsState(
    val overall: OverallStats = OverallStats(0, 0, 0, null),
    val days: List<DayStat> = emptyList(),
    val weak: List<WeakTopic> = emptyList(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(repository: QuizRepository) : ViewModel() {
    val state: StateFlow<StatsState> = combine(
        repository.overall(),
        repository.dailyStats(),
        combine(repository.topics, repository.topicProgress) { topics, progress ->
            progress.filter { it.seen > 0 }.mapNotNull { p ->
                val t = topics.firstOrNull { it.id == p.topicId } ?: return@mapNotNull null
                WeakTopic(t.nameUz, t.nameRu, p.wrongNow * 100 / p.seen)
            }.filter { it.percentWrong > 0 }.sortedByDescending { it.percentWrong }
        },
    ) { o, d, w -> StatsState(o, d, w) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsState())
}

@Composable
fun StatsRoute(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StatsContent(state)
}

@Composable
fun StatsContent(state: StatsState) {
    if (state.overall.seen == 0) {
        EmptyState(Icons.Rounded.BarChart, stringResource(R.string.stats_empty))
        return
    }
    val extra = LocalExtraColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("${state.overall.seen}/${state.overall.total}", stringResource(R.string.solved), Modifier.weight(1f))
            StatTile("${state.overall.correctPercent}%", stringResource(R.string.correct_percent), Modifier.weight(1f), extra.success)
            StatTile(state.overall.readiness?.let { "$it%" } ?: "—", stringResource(R.string.readiness), Modifier.weight(1f), extra.accent)
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.by_days), style = MaterialTheme.typography.titleMedium)
                DayChart(state.days)
            }
        }
        if (state.weak.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.weak_topics), style = MaterialTheme.typography.titleMedium)
                    state.weak.forEach { w ->
                        Text(stringResource(R.string.topic_wrong_percent, loc(w.nameUz, w.nameRu), w.percentWrong), style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(progress = { w.percentWrong / 100f }, color = extra.failure, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** Kunlar bo'yicha ustunli grafik: umumiy (och) va to'g'ri (to'q) javoblar. */
@Composable
private fun DayChart(days: List<DayStat>) {
    val total = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val correct = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        if (days.isEmpty()) return@Canvas
        val max = days.maxOf { it.total }.coerceAtLeast(1)
        val slot = size.width / days.size
        val barW = slot * 0.6f
        days.forEachIndexed { i, d ->
            val x = i * slot + (slot - barW) / 2
            val hTotal = size.height * d.total / max
            val hCorrect = size.height * d.correct / max
            drawRoundRect(total, Offset(x, size.height - hTotal), Size(barW, hTotal), CornerRadius(6f, 6f))
            drawRoundRect(correct, Offset(x, size.height - hCorrect), Size(barW, hCorrect), CornerRadius(6f, 6f))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StatsPreview() {
    PravaPreview {
        StatsContent(
            StatsState(
                OverallStats(120, 400, 81, 76),
                listOf(DayStat("2026-11-15", 15, 20), DayStat("2026-11-16", 30, 40), DayStat("2026-11-17", 18, 20)),
                listOf(WeakTopic("Chorrahalar", "Перекрёстки", 30)),
            ),
        )
    }
}
