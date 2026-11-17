package uz.xumoyun.prava.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uz.xumoyun.core.data.AssetSource
import uz.xumoyun.prava.data.db.AnswerEntity
import uz.xumoyun.prava.data.db.QuestionEntity
import uz.xumoyun.prava.data.db.QuizDao
import uz.xumoyun.prava.data.db.TopicEntity
import javax.inject.Inject
import javax.inject.Singleton

/** assets/questions.json formati. Har savolda `source` — rasmiy manba (TZ talabi). */
@Serializable
data class ContentJson(val version: Int, val topics: List<TopicJson>, val questions: List<QuestionJson>)

@Serializable
data class TopicJson(val id: Int, val uz: String, val ru: String)

@Serializable
data class LocalizedText(val uz: String, val ru: String)

@Serializable
data class QuestionJson(
    val id: Int,
    val ticket: Int,
    val topic: Int,
    val text: LocalizedText,
    val image: String? = null,
    val answers: List<LocalizedText>,
    /** To'g'ri javob indeksi (0 dan). */
    val correct: Int,
    val explanation: LocalizedText,
    val source: String,
)

object ContentValidator {
    /** Kontentdagi xatolarni qaytaradi (bo'sh ro'yxat — hammasi joyida). */
    fun validate(content: ContentJson): List<String> = buildList {
        val topicIds = content.topics.map { it.id }.toSet()
        val ids = mutableSetOf<Int>()
        content.questions.forEach { q ->
            if (!ids.add(q.id)) add("${q.id}: takroriy id")
            if (q.topic !in topicIds) add("${q.id}: noma'lum mavzu ${q.topic}")
            if (q.answers.size !in 2..5) add("${q.id}: javoblar soni ${q.answers.size} (2..5 bo'lishi kerak)")
            if (q.correct !in q.answers.indices) add("${q.id}: to'g'ri javob indeksi noto'g'ri")
            if (q.source.isBlank()) add("${q.id}: manba (source) ko'rsatilmagan")
            if (q.text.uz.isBlank() || q.text.ru.isBlank()) add("${q.id}: matn bo'sh")
        }
        content.questions.groupBy { it.ticket }.forEach { (t, qs) ->
            if (qs.size != 20) add("bilet $t: ${qs.size} ta savol (20 bo'lishi kerak)")
        }
    }
}

/** Birinchi ishga tushishda (yoki versiya o'zgarganda) savollar bazasini Room'ga yuklaydi. */
@Singleton
class ContentLoader @Inject constructor(
    private val assets: AssetSource,
    private val json: Json,
    private val dao: QuizDao,
    private val settings: PravaSettings,
) {
    fun parse(): ContentJson = json.decodeFromString(ContentJson.serializer(), assets.readText("questions.json"))

    suspend fun ensureLoaded() {
        val content = parse()
        if (settings.contentVersion() == content.version && dao.questionCount() > 0) return
        val topics = content.topics.mapIndexed { i, t -> TopicEntity(t.id, t.uz, t.ru, i) }
        val questions = content.questions.map { q ->
            QuestionEntity(q.id, q.ticket, q.topic, q.text.uz, q.text.ru, q.image, q.explanation.uz, q.explanation.ru, q.source)
        }
        val answers = content.questions.flatMap { q ->
            val order = displayOrder(q.id, q.answers.size)
            q.answers.mapIndexed { i, a -> AnswerEntity(q.id * 10 + i, q.id, a.uz, a.ru, i == q.correct, order[i]) }
        }
        dao.replaceContent(topics, questions, answers)
        settings.setContentVersion(content.version)
    }

    companion object {
        /**
         * JSON'dagi javob indeksi → ekrandagi o'rni (F1, F2...). Savol id si bo'yicha
         * barqaror aralashtiriladi: to'g'ri javob har doim birinchi turmaydi, tartib esa o'zgarmaydi.
         */
        fun displayOrder(questionId: Int, size: Int): List<Int> =
            (0 until size).shuffled(kotlin.random.Random(questionId * 7919L))
    }
}
