package uz.xumoyun.prava.ui.result

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.xumoyun.core.ui.components.AnswerOption
import uz.xumoyun.core.ui.components.EmptyState
import uz.xumoyun.core.ui.components.OptionState
import uz.xumoyun.core.ui.components.StatTile
import uz.xumoyun.core.ui.theme.LocalExtraColors
import uz.xumoyun.prava.R
import uz.xumoyun.prava.data.QuizQuestion
import uz.xumoyun.prava.data.QuizRepository
import uz.xumoyun.prava.data.db.AttemptEntity
import uz.xumoyun.prava.domain.QuizMode
import uz.xumoyun.prava.ui.LocalAds
import uz.xumoyun.prava.ui.PravaPreview
import uz.xumoyun.prava.ui.loc
import uz.xumoyun.prava.ui.quiz.QuestionImage
import javax.inject.Inject

data class ReviewItem(val question: QuizQuestion, val userAnswerId: Int?)

data class ResultState(
    val attempt: AttemptEntity? = null,
    val mistakes: List<ReviewItem> = emptyList(),
    val unlocked: Set<Int> = emptySet(),
)

@HiltViewModel
class ResultViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: QuizRepository,
) : ViewModel() {
    private val attemptId: Long = savedState.get<Long>(ARG_ATTEMPT) ?: 0L
    private val _state = MutableStateFlow(ResultState())
    val state: StateFlow<ResultState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val attempt = repository.attempt(attemptId)
            val wrong = repository.attemptAnswers(attemptId).filter { !it.isCorrect }
            val questions = repository.loadByIds(wrong.map { it.questionId })
            _state.value = ResultState(
                attempt = attempt,
                mistakes = questions.map { q -> ReviewItem(q, wrong.first { it.questionId == q.question.id }.answerId) },
            )
        }
    }

    /** Batafsil izoh mukofotli reklamadan keyin ochiladi. */
    fun unlock(questionId: Int) = _state.update { it.copy(unlocked = it.unlocked + questionId) }

    companion object {
        const val ARG_ATTEMPT = "attempt"
    }
}

@Composable
fun ResultRoute(viewModel: ResultViewModel, onReview: () -> Unit, onHome: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    val ads = LocalAds.current
    // Interstitial — tabiiy tanaffusda, natija ekraniga kirishda (chastota AdsManager'da cheklangan).
    LaunchedEffect(Unit) { if (activity != null) ads?.showInterstitial(activity) {} }
    state.attempt?.let { ResultContent(it, state.mistakes.size, onReview, onHome) }
}

@Composable
fun ResultContent(attempt: AttemptEntity, mistakes: Int, onReview: () -> Unit, onHome: () -> Unit) {
    val extra = LocalExtraColors.current
    val graded = attempt.mode == QuizMode.EXAM || attempt.mode == QuizMode.TICKET
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(
            if (attempt.passed) Icons.Rounded.Verified else Icons.Rounded.SentimentDissatisfied,
            null,
            tint = if (attempt.passed) extra.success else extra.failure,
            modifier = Modifier.size(72.dp),
        )
        Text(
            stringResource(
                when {
                    !graded -> R.string.practice_done
                    attempt.passed -> R.string.passed
                    else -> R.string.failed
                },
            ),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("${attempt.correct}", stringResource(R.string.correct_answers), Modifier.weight(1f), extra.success)
            StatTile("${attempt.wrong}", stringResource(R.string.wrong_answers), Modifier.weight(1f), extra.failure)
        }
        if (mistakes > 0) {
            Button(onClick = onReview, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.review_mistakes)) }
        }
        OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.to_home)) }
    }
}

@Composable
fun ReviewRoute(viewModel: ResultViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    val ads = LocalAds.current
    ReviewContent(state.mistakes, state.unlocked) { id ->
        if (activity != null && ads != null) ads.showRewarded(activity) { viewModel.unlock(id) } else viewModel.unlock(id)
    }
}

@Composable
fun ReviewContent(items: List<ReviewItem>, unlocked: Set<Int>, onUnlock: (Int) -> Unit) {
    if (items.isEmpty()) {
        EmptyState(Icons.Rounded.CheckCircle, stringResource(R.string.no_mistakes))
        return
    }
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(items, key = { it.question.question.id }) { item ->
            val q = item.question
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    q.question.imageRes?.let { QuestionImage(it) }
                    Text(loc(q.question.textUz, q.question.textRu), style = MaterialTheme.typography.titleMedium)
                    q.answers.forEachIndexed { i, a ->
                        val state = when {
                            a.isCorrect -> OptionState.CORRECT
                            a.id == item.userAnswerId -> OptionState.WRONG
                            else -> null
                        }
                        if (state != null) AnswerOption("F${i + 1}", loc(a.textUz, a.textRu), state, enabled = false, onClick = {})
                    }
                    if (item.userAnswerId == null) {
                        Text(stringResource(R.string.not_answered), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (q.question.id in unlocked) {
                        Text(loc(q.question.explanationUz, q.question.explanationRu), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.source, q.question.source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        TextButton(onClick = { onUnlock(q.question.id) }) {
                            Icon(Icons.Rounded.Lock, null)
                            Text(" " + stringResource(R.string.unlock_explanation))
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ResultPreview() {
    PravaPreview {
        ResultContent(AttemptEntity(1, QuizMode.EXAM, null, null, 0, 0, 18, 2, true), 2, {}, {})
    }
}
