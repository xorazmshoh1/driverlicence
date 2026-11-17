package uz.xumoyun.prava

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uz.xumoyun.core.common.FixedTimeSource
import uz.xumoyun.prava.data.PravaSettings
import uz.xumoyun.prava.data.QuizRepository
import uz.xumoyun.prava.data.db.AnswerEntity
import uz.xumoyun.prava.data.db.AttemptAnswerEntity
import uz.xumoyun.prava.data.db.AttemptEntity
import uz.xumoyun.prava.data.db.DayStat
import uz.xumoyun.prava.data.db.QuestionEntity
import uz.xumoyun.prava.data.db.QuestionStatEntity
import uz.xumoyun.prava.data.db.QuizDao
import uz.xumoyun.prava.data.db.TicketResult
import uz.xumoyun.prava.data.db.TopicEntity
import uz.xumoyun.prava.data.db.TopicProgress
import uz.xumoyun.prava.domain.QuizMode
import uz.xumoyun.prava.ui.quiz.QuizViewModel
import java.io.File
import java.nio.file.Files
import java.time.Instant

/** Xotiradagi soxta DAO: 1 ta bilet, 20 ta savol, har birida 3 javob (to'g'risi — id*10). */
class FakeQuizDao : QuizDao {
    val questions = (1..20).map { QuestionEntity(it, 1, 1, "q$it", "q$it", null, "", "", "test") }
    val answers = questions.flatMap { q -> (0..2).map { AnswerEntity(q.id * 10 + it, q.id, "a$it", "a$it", it == 0, it) } }
    val stats = MutableStateFlow<Map<Int, QuestionStatEntity>>(emptyMap())
    val attempts = mutableListOf<AttemptEntity>()
    val attemptAnswers = mutableListOf<AttemptAnswerEntity>()

    override suspend fun insertTopics(items: List<TopicEntity>) = Unit
    override suspend fun insertQuestions(items: List<QuestionEntity>) = Unit
    override suspend fun insertAnswers(items: List<AnswerEntity>) = Unit
    override suspend fun clearAnswers() = Unit
    override suspend fun clearQuestions() = Unit
    override suspend fun clearTopics() = Unit
    override fun observeTopics(): Flow<List<TopicEntity>> = MutableStateFlow(listOf(TopicEntity(1, "t", "t", 0)))
    override fun observeTickets(): Flow<List<Int>> = MutableStateFlow(listOf(1))
    override suspend fun questionCount() = questions.size
    override suspend fun byTicket(ticket: Int) = questions.filter { it.ticketNo == ticket }
    override suspend fun byTopic(topic: Int) = questions.filter { it.topicId == topic }
    override suspend fun random(limit: Int) = questions.take(limit)
    override suspend fun byIds(ids: List<Int>) = questions.filter { it.id in ids }
    override suspend fun answersFor(ids: List<Int>) = answers.filter { it.questionId in ids }
    override suspend fun stat(id: Int) = stats.value[id]
    override suspend fun upsertStat(stat: QuestionStatEntity) { stats.value = stats.value + (stat.questionId to stat) }
    override fun observeMistakeIds() = stats.map { m -> m.values.filter { it.timesWrong > 0 && it.box < 3 }.map { it.questionId } }
    override fun observeFavoriteIds() = stats.map { m -> m.values.filter { it.isFavorite }.map { it.questionId } }
    override fun observeTopicProgress(): Flow<List<TopicProgress>> = MutableStateFlow(emptyList())
    override fun observeTicketResults(): Flow<List<TicketResult>> = MutableStateFlow(emptyList())
    override fun observeLastExamScores(): Flow<List<Int>> = MutableStateFlow(emptyList())
    override fun observeDailyStats(sinceMs: Long): Flow<List<DayStat>> = MutableStateFlow(emptyList())
    override fun observeSeenCount(): Flow<Int> = stats.map { it.size }
    override fun observeCorrectPercent(): Flow<Int> = MutableStateFlow(0)
    override suspend fun insertAttempt(attempt: AttemptEntity): Long {
        val id = attempts.size + 1L
        attempts += attempt.copy(id = id)
        return id
    }
    override suspend fun insertAttemptAnswers(items: List<AttemptAnswerEntity>) { attemptAnswers += items }
    override suspend fun attempt(id: Long) = attempts.firstOrNull { it.id == id }
    override suspend fun attemptAnswers(id: Long) = attemptAnswers.filter { it.attemptId == id }
}

@OptIn(ExperimentalCoroutinesApi::class)
class QuizViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val dao = FakeQuizDao()
    private val time = FixedTimeSource(Instant.parse("2026-11-17T10:00:00Z"))
    private val repository = QuizRepository(dao, time)
    private val settings = PravaSettings(
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { File(Files.createTempDirectory("p").toFile(), "s.preferences_pb") },
        ),
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(mode: QuizMode, ticket: Int = 0) =
        QuizViewModel(SavedStateHandle(mapOf("mode" to mode.name, "ticket" to ticket, "topic" to 0)), repository, settings, time)

    @Test fun ticketPracticeRevealsImmediatelyAndPasses() = runTest(dispatcher) {
        val vm = vm(QuizMode.TICKET, ticket = 1)
        awaitLoaded(vm)
        val s0 = vm.state.value
        assertEquals(20, s0.questions.size)
        assertEquals(null, s0.secondsLeft)

        repeat(20) { i ->
            val q = vm.state.value.current!!
            // 2 ta xato, qolgani to'g'ri.
            vm.select(if (i < 2) q.question.id * 10 + 1 else q.correctAnswerId)
            assertTrue(vm.state.value.currentRevealed)
            vm.next()
            advanceUntilIdle()
        }
        val attemptId = vm.state.value.finishedAttemptId
        assertNotNull(attemptId)
        val attempt = dao.attempt(attemptId!!)!!
        assertEquals(18, attempt.correct)
        assertTrue(attempt.passed)
        assertEquals(2, repository.mistakeIds.first().size)
    }

    @Test fun examStopsAfterThirdMistake() = runTest(dispatcher) {
        val vm = vm(QuizMode.EXAM)
        awaitLoaded(vm)
        assertEquals(25 * 60, vm.state.value.secondsLeft)

        repeat(3) {
            val q = vm.state.value.current!!
            vm.select(q.question.id * 10 + 2)
            assertFalse("imtihonda javob ko'rsatilmaydi", vm.state.value.currentRevealed)
            vm.select(q.question.id * 10 + 1) // o'zgartirish mumkin
            vm.next()
        }
        assertTrue(vm.state.value.failedEarly)
        vm.finish()
        advanceTimeBy(10)
        val attempt = dao.attempt(vm.state.value.finishedAttemptId!!)!!
        assertFalse(attempt.passed)
        assertEquals(20, dao.attemptAnswers(attempt.id).size) // javob berilmaganlar ham xato sifatida
    }

    @Test fun examTimerFinishesTest() = runTest(dispatcher) {
        val vm = vm(QuizMode.EXAM)
        awaitLoaded(vm)
        advanceTimeBy(25 * 60 * 1000L + 2000)
        assertEquals(0, vm.state.value.secondsLeft)
        assertNotNull(vm.state.value.finishedAttemptId)
    }
}

/** DataStore o'qishi haqiqiy IO oqimida bo'ladi: yuklanguncha kutamiz (virtual vaqtni surmasdan). */
@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.awaitLoaded(vm: QuizViewModel) {
    repeat(500) {
        runCurrent()
        if (!vm.state.value.loading) return
        Thread.sleep(10)
    }
    error("ViewModel yuklanmadi")
}
