package uz.xumoyun.prava.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import uz.xumoyun.core.ui.components.EmptyState
import uz.xumoyun.prava.R
import uz.xumoyun.prava.data.QuizQuestion
import uz.xumoyun.prava.data.QuizRepository
import uz.xumoyun.prava.domain.QuizMode
import uz.xumoyun.prava.ui.loc
import javax.inject.Inject

/** "Xatolarim" va "Saralanganlar" ro'yxati. */
@HiltViewModel
class QuestionListViewModel @Inject constructor(savedState: SavedStateHandle, repository: QuizRepository) : ViewModel() {
    val mode: QuizMode = QuizMode.valueOf(savedState.get<String>("mode") ?: QuizMode.MISTAKES.name)

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<QuizQuestion>?> =
        (if (mode == QuizMode.FAVORITES) repository.favoriteIds else repository.mistakeIds)
            .mapLatest { ids -> repository.loadByIds(ids) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@Composable
fun QuestionListRoute(onPractice: (QuizMode) -> Unit, viewModel: QuestionListViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val list = items ?: return
    if (list.isEmpty()) {
        if (viewModel.mode == QuizMode.FAVORITES) {
            EmptyState(Icons.Rounded.StarBorder, stringResource(R.string.no_favorites), message = stringResource(R.string.no_favorites_hint))
        } else {
            EmptyState(Icons.Rounded.CheckCircle, stringResource(R.string.no_mistakes), message = stringResource(R.string.no_mistakes_hint))
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.question.id }) { q ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(loc(q.question.textUz, q.question.textRu), style = MaterialTheme.typography.bodyLarge)
                        val correct = q.answers.first { it.isCorrect }
                        Text(
                            stringResource(R.string.correct_answer_is, loc(correct.textUz, correct.textRu)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Button(
            onClick = { onPractice(viewModel.mode) },
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).heightIn(min = 52.dp),
        ) { Text(stringResource(R.string.practice_these)) }
    }
}
