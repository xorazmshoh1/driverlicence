package uz.xumoyun.prava.ui.quiz

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.xumoyun.core.common.TimeSource
import uz.xumoyun.prava.data.PravaSettings
import uz.xumoyun.prava.data.QuizQuestion
import uz.xumoyun.prava.data.QuizRepository
import uz.xumoyun.prava.data.UserAnswer
import uz.xumoyun.prava.domain.ExamRules
import uz.xumoyun.prava.domain.QuizMode
import javax.inject.Inject

data class QuizState(
    val loading: Boolean = true,
    val mode: QuizMode = QuizMode.TICKET,
    val questions: List<QuizQuestion> = emptyList(),
    val index: Int = 0,
    /** Savol id → tanlangan javob id. */
    val selected: Map<Int, Int> = emptyMap(),
    /** Javobi tasdiqlangan savollar (mashqda — tanlangan zahoti, imtihonda — "Keyingi" bosilganda). */
    val confirmed: Set<Int> = emptySet(),
    val secondsLeft: Int? = null,
    val rules: ExamRules = ExamRules(uz.xumoyun.prava.domain.Category.B),
    val failedEarly: Boolean = false,
    val finishedAttemptId: Long? = null,
) {
    val current: QuizQuestion? get() = questions.getOrNull(index)
    val isExam: Boolean get() = mode == QuizMode.EXAM
    val wrongCount: Int get() = questions.count { q -> q.question.id in confirmed && selected[q.question.id] != q.correctAnswerId }
    val correctCount: Int get() = questions.count { q -> q.question.id in confirmed && selected[q.question.id] == q.correctAnswerId }
    val isLast: Boolean get() = index == questions.lastIndex
    val currentRevealed: Boolean get() = current?.let { !isExam && it.question.id in confirmed } ?: false
    val canGoNext: Boolean get() = current?.let { selected.containsKey(it.question.id) } ?: false
}

@HiltViewModel
class QuizViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: QuizRepository,
    private val settings: PravaSettings,
    private val time: TimeSource,
) : ViewModel() {

    private val mode: QuizMode = QuizMode.valueOf(savedState.get<String>(ARG_MODE) ?: QuizMode.TICKET.name)
    private val ticketNo: Int? = savedState.get<Int>(ARG_TICKET)?.takeIf { it > 0 }
    private val topicId: Int? = savedState.get<Int>(ARG_TOPIC)?.takeIf { it > 0 }
    private val startedAt = time.now().toEpochMilli()
    private var timerJob: Job? = null

    private val _state = MutableStateFlow(QuizState(mode = mode))
    val state: StateFlow<QuizState> = _state.asStateFlow()

    /** Ovoz/tebranish uchun: true — to'g'ri, false — xato. */
    val feedback = MutableStateFlow<Boolean?>(null)

    init {
        viewModelScope.launch {
            val rules = ExamRules(settings.currentCategory())
            val questions = repository.load(mode, ticketNo, topicId)
            _state.update {
                it.copy(
                    loading = false,
                    questions = questions,
                    rules = rules,
                    secondsLeft = if (mode == QuizMode.EXAM) rules.durationSeconds else null,
                )
            }
            if (mode == QuizMode.EXAM && questions.isNotEmpty()) startTimer()
        }
    }

    private fun startTimer() {
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val left = (_state.value.secondsLeft ?: return@launch) - 1
                _state.update { it.copy(secondsLeft = left.coerceAtLeast(0)) }
                if (left <= 0) {
                    finish()
                    return@launch
                }
            }
        }
    }

    fun select(answerId: Int) {
        val s = _state.value
        val q = s.current ?: return
        if (q.question.id in s.confirmed || s.finishedAttemptId != null) return
        if (s.isExam) {
            // Imtihonda faqat belgilanadi, "Keyingi" bosilguncha o'zgartirish mumkin.
            _state.update { it.copy(selected = it.selected + (q.question.id to answerId)) }
        } else {
            _state.update { it.copy(selected = it.selected + (q.question.id to answerId), confirmed = it.confirmed + q.question.id) }
            record(q, answerId)
        }
    }

    fun next() {
        val s = _state.value
        val q = s.current ?: return
        if (s.isExam) {
            val answerId = s.selected[q.question.id] ?: return
            if (q.question.id !in s.confirmed) {
                _state.update { it.copy(confirmed = it.confirmed + q.question.id) }
                record(q, answerId)
            }
            val updated = _state.value
            if (updated.rules.isFailedEarly(updated.wrongCount)) {
                _state.update { it.copy(failedEarly = true) }
                timerJob?.cancel()
                return
            }
        }
        if (s.isLast) finish() else _state.update { it.copy(index = it.index + 1) }
    }

    fun goTo(index: Int) {
        if (_state.value.isExam) return
        _state.update { it.copy(index = index.coerceIn(0, it.questions.lastIndex)) }
    }

    fun toggleFavorite() = viewModelScope.launch {
        val q = _state.value.current ?: return@launch
        val fav = repository.toggleFavorite(q.question.id)
        _state.update { s ->
            s.copy(questions = s.questions.map { if (it.question.id == q.question.id) it.copy(isFavorite = fav) else it })
        }
    }

    private fun record(q: QuizQuestion, answerId: Int) {
        val correct = answerId == q.correctAnswerId
        feedback.value = correct
        viewModelScope.launch { repository.recordAnswer(q.question.id, correct) }
    }

    fun finish() {
        val s = _state.value
        if (s.finishedAttemptId != null || s.questions.isEmpty()) return
        timerJob?.cancel()
        viewModelScope.launch {
            val answers = s.questions.mapNotNull { q ->
                val id = q.question.id
                when {
                    id in s.confirmed -> UserAnswer(id, s.selected[id], s.selected[id] == q.correctAnswerId)
                    s.isExam -> UserAnswer(id, null, false) // javob berilmagan — xato
                    else -> null
                }
            }
            val correct = answers.count { it.isCorrect }
            val passed = when (s.mode) {
                QuizMode.EXAM, QuizMode.TICKET -> !s.failedEarly && s.rules.isPassed(correct)
                else -> answers.isNotEmpty() && correct * 10 >= answers.size * 9
            }
            val id = repository.saveAttempt(s.mode, ticketNo, topicId, startedAt, answers, passed)
            _state.update { it.copy(finishedAttemptId = id) }
        }
    }

    companion object {
        const val ARG_MODE = "mode"
        const val ARG_TICKET = "ticket"
        const val ARG_TOPIC = "topic"
    }
}
