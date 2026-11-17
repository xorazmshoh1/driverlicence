package uz.xumoyun.prava.ui.quiz

import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.xumoyun.core.common.DurationFormat
import uz.xumoyun.core.ui.components.AnswerOption
import uz.xumoyun.core.ui.components.EmptyState
import uz.xumoyun.core.ui.components.OptionState
import uz.xumoyun.core.ui.components.QuizHeader
import uz.xumoyun.prava.R
import uz.xumoyun.prava.data.QuizQuestion
import uz.xumoyun.prava.data.db.AnswerEntity
import uz.xumoyun.prava.data.db.QuestionEntity
import uz.xumoyun.prava.domain.QuizMode
import uz.xumoyun.prava.ui.PravaPreview
import uz.xumoyun.prava.ui.loc

@Composable
fun QuizRoute(onFinished: (Long) -> Unit, soundOn: Boolean, viewModel: QuizViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { tone?.release() } }
    LaunchedEffect(feedback) {
        val f = feedback ?: return@LaunchedEffect
        if (soundOn && !state.isExam) tone?.startTone(if (f) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK, 150)
        viewModel.feedback.value = null
    }
    LaunchedEffect(state.finishedAttemptId) { state.finishedAttemptId?.let(onFinished) }

    QuizContent(
        state,
        onSelect = viewModel::select,
        onNext = viewModel::next,
        onFavorite = { viewModel.toggleFavorite() },
        onFinishNow = viewModel::finish,
    )
}

@Composable
fun QuizContent(
    state: QuizState,
    onSelect: (Int) -> Unit,
    onNext: () -> Unit,
    onFavorite: () -> Unit,
    onFinishNow: () -> Unit,
) {
    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val q = state.current
    if (q == null) {
        EmptyState(
            icon = Icons.Rounded.StarBorder,
            title = stringResource(R.string.empty_quiz_title),
            message = stringResource(R.string.empty_quiz_message),
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        QuizHeader(
            progressText = stringResource(R.string.question_progress, state.index + 1, state.questions.size),
            timerText = state.secondsLeft?.let { DurationFormat.ms(it.toLong()) },
            timerWarning = (state.secondsLeft ?: Int.MAX_VALUE) <= 120,
            trailing = {
                if (state.isExam) {
                    Text(
                        stringResource(R.string.mistakes_count, state.wrongCount, state.rules.maxErrors),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (q.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = stringResource(R.string.favorite),
                        tint = if (q.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        LinearProgressIndicator(progress = { (state.index + 1f) / state.questions.size }, modifier = Modifier.fillMaxWidth())
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            q.question.imageRes?.let { QuestionImage(it) }
            Text(loc(q.question.textUz, q.question.textRu), style = MaterialTheme.typography.titleMedium)
            q.answers.forEachIndexed { i, a ->
                val selected = state.selected[q.question.id] == a.id
                val optionState = when {
                    state.currentRevealed && a.isCorrect -> OptionState.CORRECT
                    state.currentRevealed && selected -> OptionState.WRONG
                    selected -> OptionState.SELECTED
                    else -> OptionState.IDLE
                }
                AnswerOption(
                    label = "F${i + 1}",
                    text = loc(a.textUz, a.textRu),
                    state = optionState,
                    enabled = !state.currentRevealed,
                    onClick = { onSelect(a.id) },
                )
            }
        }
        Button(
            onClick = onNext,
            enabled = state.canGoNext,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).heightIn(min = 52.dp),
        ) {
            Text(stringResource(if (state.isLast) R.string.finish else R.string.next))
        }
    }

    if (state.failedEarly) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.exam_failed_title)) },
            text = { Text(stringResource(R.string.exam_failed_message, state.wrongCount)) },
            confirmButton = { TextButton(onClick = onFinishNow) { Text(stringResource(R.string.see_result)) } },
        )
    }
}

/** Yo'l belgisi rasmi: avval drawable (vektor), keyin assets/images/<nom>.webp. */
@Composable
fun QuestionImage(name: String) {
    val modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    val drawable = SignImages[name]
    if (drawable != null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Image(painterResource(drawable), contentDescription = null, modifier = Modifier.fillMaxSize().padding(16.dp), contentScale = ContentScale.Fit)
        }
        return
    }
    val context = LocalContext.current
    val bitmap = remember(name) {
        runCatching { context.assets.open("images/$name.webp").use { BitmapFactory.decodeStream(it) } }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
    }
}

val SignImages: Map<String, Int> = mapOf(
    "sign_stop" to R.drawable.sign_stop,
    "sign_give_way" to R.drawable.sign_give_way,
    "sign_no_entry" to R.drawable.sign_no_entry,
    "sign_main_road" to R.drawable.sign_main_road,
    "sign_no_overtaking" to R.drawable.sign_no_overtaking,
    "sign_no_stopping" to R.drawable.sign_no_stopping,
    "sign_no_parking" to R.drawable.sign_no_parking,
    "sign_pedestrian_crossing" to R.drawable.sign_pedestrian_crossing,
)

@Preview(showBackground = true)
@Composable
private fun QuizPreview() {
    val q = QuizQuestion(
        QuestionEntity(1, 1, 1, "Bu belgi nimani anglatadi?", "", "sign_stop", "", "", ""),
        listOf(
            AnswerEntity(10, 1, "To'xtamasdan harakatlanish taqiqlanadi", "", true, 0),
            AnswerEntity(11, 1, "To'xtash taqiqlanadi", "", false, 1),
            AnswerEntity(12, 1, "Yo'l bering", "", false, 2),
        ),
        isFavorite = false,
    )
    PravaPreview {
        QuizContent(
            QuizState(loading = false, mode = QuizMode.EXAM, questions = listOf(q), selected = mapOf(1 to 11), secondsLeft = 100),
            {}, {}, {}, {},
        )
    }
}
