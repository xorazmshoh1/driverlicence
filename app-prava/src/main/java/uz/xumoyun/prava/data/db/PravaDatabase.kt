package uz.xumoyun.prava.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import uz.xumoyun.prava.domain.QuizMode

@Entity(tableName = "topic")
data class TopicEntity(@PrimaryKey val id: Int, val nameUz: String, val nameRu: String, val order: Int)

@Entity(tableName = "question", indices = [Index("ticketNo"), Index("topicId")])
data class QuestionEntity(
    @PrimaryKey val id: Int,
    val ticketNo: Int,
    val topicId: Int,
    val textUz: String,
    val textRu: String,
    val imageRes: String?,
    val explanationUz: String,
    val explanationRu: String,
    val source: String,
)

@Entity(tableName = "answer", indices = [Index("questionId")])
data class AnswerEntity(
    @PrimaryKey val id: Int,
    val questionId: Int,
    val textUz: String,
    val textRu: String,
    val isCorrect: Boolean,
    val order: Int,
)

@Entity(tableName = "attempt")
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: QuizMode,
    val ticketNo: Int?,
    val topicId: Int?,
    val startedAt: Long,
    val finishedAt: Long,
    val correct: Int,
    val wrong: Int,
    val passed: Boolean,
)

@Entity(tableName = "attempt_answer", primaryKeys = ["attemptId", "questionId"])
data class AttemptAnswerEntity(val attemptId: Long, val questionId: Int, val answerId: Int?, val isCorrect: Boolean)

@Entity(tableName = "question_stat")
data class QuestionStatEntity(
    @PrimaryKey val questionId: Int,
    val timesSeen: Int = 0,
    val timesWrong: Int = 0,
    val box: Int = 0,
    val nextReviewAt: Long = 0,
    val isFavorite: Boolean = false,
)

/** Mavzu bo'yicha yig'indi: savollar soni, ko'rilgan va to'g'ri yechilganlar. */
data class TopicProgress(val topicId: Int, val total: Int, val seen: Int, val wrongNow: Int)

data class TicketResult(val ticketNo: Int, val passed: Boolean)

data class DayStat(val day: String, val correct: Int, val total: Int)

@Dao
interface QuizDao {
    // ---- kontent ----
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTopics(items: List<TopicEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertQuestions(items: List<QuestionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAnswers(items: List<AnswerEntity>)
    @Query("DELETE FROM answer") suspend fun clearAnswers()
    @Query("DELETE FROM question") suspend fun clearQuestions()
    @Query("DELETE FROM topic") suspend fun clearTopics()

    @Transaction
    suspend fun replaceContent(topics: List<TopicEntity>, questions: List<QuestionEntity>, answers: List<AnswerEntity>) {
        clearAnswers(); clearQuestions(); clearTopics()
        insertTopics(topics); insertQuestions(questions); insertAnswers(answers)
    }

    @Query("SELECT * FROM topic ORDER BY `order`") fun observeTopics(): Flow<List<TopicEntity>>
    @Query("SELECT DISTINCT ticketNo FROM question ORDER BY ticketNo") fun observeTickets(): Flow<List<Int>>
    @Query("SELECT COUNT(*) FROM question") suspend fun questionCount(): Int

    @Query("SELECT * FROM question WHERE ticketNo = :ticket ORDER BY id") suspend fun byTicket(ticket: Int): List<QuestionEntity>
    @Query("SELECT * FROM question WHERE topicId = :topic ORDER BY id") suspend fun byTopic(topic: Int): List<QuestionEntity>
    @Query("SELECT * FROM question ORDER BY RANDOM() LIMIT :limit") suspend fun random(limit: Int): List<QuestionEntity>
    @Query("SELECT * FROM question WHERE id IN (:ids)") suspend fun byIds(ids: List<Int>): List<QuestionEntity>
    @Query("SELECT * FROM answer WHERE questionId IN (:ids) ORDER BY `order`") suspend fun answersFor(ids: List<Int>): List<AnswerEntity>

    // ---- statistika va takrorlash ----
    @Query("SELECT * FROM question_stat WHERE questionId = :id") suspend fun stat(id: Int): QuestionStatEntity?
    @Upsert suspend fun upsertStat(stat: QuestionStatEntity)

    @Query("SELECT questionId FROM question_stat WHERE timesWrong > 0 AND box < 3 ORDER BY nextReviewAt")
    fun observeMistakeIds(): Flow<List<Int>>

    @Query("SELECT questionId FROM question_stat WHERE isFavorite = 1") fun observeFavoriteIds(): Flow<List<Int>>

    @Query(
        """SELECT q.topicId AS topicId, COUNT(*) AS total,
           SUM(CASE WHEN s.timesSeen > 0 THEN 1 ELSE 0 END) AS seen,
           SUM(CASE WHEN s.timesWrong > 0 AND s.box < 3 THEN 1 ELSE 0 END) AS wrongNow
           FROM question q LEFT JOIN question_stat s ON s.questionId = q.id GROUP BY q.topicId""",
    )
    fun observeTopicProgress(): Flow<List<TopicProgress>>

    @Query(
        """SELECT a.ticketNo AS ticketNo, a.passed AS passed FROM attempt a
           WHERE a.mode = 'TICKET' AND a.id = (SELECT MAX(b.id) FROM attempt b WHERE b.mode = 'TICKET' AND b.ticketNo = a.ticketNo)""",
    )
    fun observeTicketResults(): Flow<List<TicketResult>>

    @Query("SELECT correct FROM attempt WHERE mode = 'EXAM' ORDER BY finishedAt DESC LIMIT 5")
    fun observeLastExamScores(): Flow<List<Int>>

    @Query(
        """SELECT date(t.finishedAt / 1000, 'unixepoch', 'localtime') AS day,
           SUM(CASE WHEN aa.isCorrect THEN 1 ELSE 0 END) AS correct, COUNT(*) AS total
           FROM attempt_answer aa JOIN attempt t ON t.id = aa.attemptId
           WHERE t.finishedAt >= :sinceMs GROUP BY day ORDER BY day""",
    )
    fun observeDailyStats(sinceMs: Long): Flow<List<DayStat>>

    @Query("SELECT COUNT(*) FROM question_stat WHERE timesSeen > 0") fun observeSeenCount(): Flow<Int>
    @Query("SELECT COALESCE(SUM(CASE WHEN isCorrect THEN 1 ELSE 0 END) * 100 / MAX(COUNT(*), 1), 0) FROM attempt_answer")
    fun observeCorrectPercent(): Flow<Int>

    @Insert suspend fun insertAttempt(attempt: AttemptEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAttemptAnswers(items: List<AttemptAnswerEntity>)
    @Query("SELECT * FROM attempt WHERE id = :id") suspend fun attempt(id: Long): AttemptEntity?
    @Query("SELECT * FROM attempt_answer WHERE attemptId = :id") suspend fun attemptAnswers(id: Long): List<AttemptAnswerEntity>
}

@Database(
    entities = [TopicEntity::class, QuestionEntity::class, AnswerEntity::class, AttemptEntity::class, AttemptAnswerEntity::class, QuestionStatEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PravaDatabase : RoomDatabase() {
    abstract fun quiz(): QuizDao
}
