package uz.xumoyun.prava.domain

/** Haydovchilik guvohnomasi toifasi. */
enum class Category { A1, A, B, C, D }

enum class QuizMode { TICKET, TOPIC, EXAM, MISTAKES, FAVORITES }

/**
 * Real imtihon formati: 20 savol, 25 daqiqa.
 * O'tish chegarasi: A, B, C, D — kamida 18 to'g'ri, A1 — 16.
 */
data class ExamRules(val category: Category) {
    val questionCount: Int get() = QUESTIONS
    val durationSeconds: Int get() = DURATION_SECONDS
    val passThreshold: Int get() = if (category == Category.A1) 16 else 18
    /** Ruxsat etilgan xatolar soni: 18 uchun 2 ta, 16 uchun 4 ta. */
    val maxErrors: Int get() = questionCount - passThreshold

    /** Shuncha xatodan keyin imtihon to'xtatiladi ("Imtihondan o'tolmadingiz"). */
    fun isFailedEarly(wrong: Int): Boolean = wrong > maxErrors

    fun isPassed(correct: Int): Boolean = correct >= passThreshold

    companion object {
        const val QUESTIONS = 20
        const val DURATION_SECONDS = 25 * 60
    }
}

/**
 * Oddiy Leitner tizimi: xato — 1-qutiga qaytadi va ertaga qayta chiqadi,
 * to'g'ri — keyingi qutiga o'tadi va oralig'i uzayadi.
 */
object Leitner {
    /** Quti raqami → necha kundan keyin takrorlanadi. */
    private val intervalsDays = mapOf(1 to 1L, 2 to 3L, 3 to 7L, 4 to 14L, 5 to 30L)
    const val MAX_BOX = 5
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    data class Result(val box: Int, val nextReviewAt: Long)

    fun next(currentBox: Int, correct: Boolean, nowMs: Long): Result {
        val box = if (correct) (currentBox + 1).coerceAtMost(MAX_BOX) else 1
        return Result(box, nowMs + intervalsDays.getValue(box) * DAY_MS)
    }

    /** "Xatolarim" ro'yxatida qoladi: hech bo'lmasa bir marta xato qilingan va hali yaxshi o'rganilmagan. */
    fun isMistake(timesWrong: Int, box: Int): Boolean = timesWrong > 0 && box < 3
}

/** Oxirgi 5 ta imtihon simulyatsiyasi asosida "Siz 85% tayyorsiz". */
object Readiness {
    fun percent(lastExamCorrect: List<Int>, questions: Int = ExamRules.QUESTIONS): Int? {
        val last = lastExamCorrect.take(5)
        if (last.isEmpty()) return null
        return (last.sum() * 100.0 / (last.size * questions)).toInt()
    }
}
