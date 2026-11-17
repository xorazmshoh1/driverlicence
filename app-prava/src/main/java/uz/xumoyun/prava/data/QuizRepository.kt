package uz.xumoyun.prava.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import uz.xumoyun.core.common.TimeSource
import uz.xumoyun.prava.data.db.AnswerEntity
import uz.xumoyun.prava.data.db.AttemptAnswerEntity
import uz.xumoyun.prava.data.db.AttemptEntity
import uz.xumoyun.prava.data.db.QuestionEntity
import uz.xumoyun.prava.data.db.QuestionStatEntity
import uz.xumoyun.prava.data.db.QuizDao
import uz.xumoyun.prava.domain.ExamRules
import uz.xumoyun.prava.domain.Leitner
import uz.xumoyun.prava.domain.QuizMode
import uz.xumoyun.prava.domain.Readiness
import javax.inject.Inject
import javax.inject.Singleton

/** Savol va uning javoblari (tartiblangan). */
data class QuizQuestion(val question: QuestionEntity, val answers: List<AnswerEntity>, val isFavorite: Boolean) {
    val correctAnswerId: Int get() = answers.first { it.isCorrect }.id
}

data class UserAnswer(val questionId: Int, val answerId: Int?, val isCorrect: Boolean)

data class OverallStats(val seen: Int, val total: Int, val correctPercent: Int, val readiness: Int?)

@Singleton
class QuizRepository @Inject constructor(
    private val dao: QuizDao,
    private val time: TimeSource,
) {
    val topics = dao.observeTopics()
    val tickets = dao.observeTickets()
    val ticketResults = dao.observeTicketResults()
    val topicProgress = dao.observeTopicProgress()
    val mistakeIds = dao.observeMistakeIds()
    val favoriteIds = dao.observeFavoriteIds()

    val readiness: Flow<Int?> = combine(dao.observeLastExamScores(), dao.observeTickets()) { scores, _ -> Readiness.percent(scores) }

    fun overall(totalQuestions: Flow<List<Int>> = dao.observeTickets()): Flow<OverallStats> =
        combine(dao.observeSeenCount(), dao.observeCorrectPercent(), readiness, totalQuestions) { seen, pct, ready, tickets ->
            OverallStats(seen, tickets.size * ExamRules.QUESTIONS, pct, ready)
        }

    fun dailyStats(days: Int = 14) = dao.observeDailyStats(time.now().toEpochMilli() - days * 24 * 3600 * 1000L)

    suspend fun load(mode: QuizMode, ticketNo: Int? = null, topicId: Int? = null): List<QuizQuestion> {
        val questions = when (mode) {
            QuizMode.TICKET -> dao.byTicket(requireNotNull(ticketNo))
            QuizMode.TOPIC -> dao.byTopic(requireNotNull(topicId))
            QuizMode.EXAM -> dao.random(ExamRules.QUESTIONS)
            QuizMode.MISTAKES -> dao.byIds(mistakeIds.first()).let { qs ->
                val order = mistakeIds.first()
                qs.sortedBy { order.indexOf(it.id) }
            }
            QuizMode.FAVORITES -> dao.byIds(favoriteIds.first())
        }
        return attach(questions)
    }

    suspend fun loadByIds(ids: List<Int>): List<QuizQuestion> {
        val qs = dao.byIds(ids)
        return attach(ids.mapNotNull { id -> qs.firstOrNull { it.id == id } })
    }

    private suspend fun attach(questions: List<QuestionEntity>): List<QuizQuestion> {
        if (questions.isEmpty()) return emptyList()
        val answers = dao.answersFor(questions.map { it.id }).groupBy { it.questionId }
        val favorites = favoriteIds.first().toSet()
        return questions.map { QuizQuestion(it, answers[it.id].orEmpty().sortedBy { a -> a.order }, it.id in favorites) }
    }

    /** Har bir javobdan keyin: statistika va Leitner qutisi yangilanadi. */
    suspend fun recordAnswer(questionId: Int, correct: Boolean) {
        val now = time.now().toEpochMilli()
        val s = dao.stat(questionId) ?: QuestionStatEntity(questionId)
        val next = Leitner.next(s.box, correct, now)
        dao.upsertStat(
            s.copy(
                timesSeen = s.timesSeen + 1,
                timesWrong = s.timesWrong + if (correct) 0 else 1,
                box = next.box,
                nextReviewAt = next.nextReviewAt,
            ),
        )
    }

    suspend fun toggleFavorite(questionId: Int): Boolean {
        val s = dao.stat(questionId) ?: QuestionStatEntity(questionId)
        val updated = s.copy(isFavorite = !s.isFavorite)
        dao.upsertStat(updated)
        return updated.isFavorite
    }

    suspend fun saveAttempt(
        mode: QuizMode,
        ticketNo: Int?,
        topicId: Int?,
        startedAt: Long,
        answers: List<UserAnswer>,
        passed: Boolean,
    ): Long {
        val correct = answers.count { it.isCorrect }
        val id = dao.insertAttempt(
            AttemptEntity(
                mode = mode, ticketNo = ticketNo, topicId = topicId, startedAt = startedAt,
                finishedAt = time.now().toEpochMilli(), correct = correct, wrong = answers.size - correct, passed = passed,
            ),
        )
        dao.insertAttemptAnswers(answers.map { AttemptAnswerEntity(id, it.questionId, it.answerId, it.isCorrect) })
        return id
    }

    suspend fun attempt(id: Long) = dao.attempt(id)
    suspend fun attemptAnswers(id: Long) = dao.attemptAnswers(id)
}
