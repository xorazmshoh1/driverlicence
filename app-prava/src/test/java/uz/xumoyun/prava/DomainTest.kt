package uz.xumoyun.prava

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.xumoyun.core.data.AppJson
import uz.xumoyun.prava.data.ContentJson
import uz.xumoyun.prava.data.ContentLoader
import uz.xumoyun.prava.data.ContentValidator
import uz.xumoyun.prava.domain.Category
import uz.xumoyun.prava.domain.ExamRules
import uz.xumoyun.prava.domain.Leitner
import uz.xumoyun.prava.domain.Readiness
import java.io.File

class DomainTest {

    @Test fun examRulesForCategories() {
        val b = ExamRules(Category.B)
        assertEquals(20, b.questionCount)
        assertEquals(25 * 60, b.durationSeconds)
        assertEquals(18, b.passThreshold)
        assertEquals(2, b.maxErrors)
        assertFalse(b.isFailedEarly(2))
        assertTrue("3-xatodan keyin to'xtaydi", b.isFailedEarly(3))
        assertTrue(b.isPassed(18))
        assertFalse(b.isPassed(17))

        val a1 = ExamRules(Category.A1)
        assertEquals(16, a1.passThreshold)
        assertFalse(a1.isFailedEarly(4))
        assertTrue(a1.isFailedEarly(5))
    }

    @Test fun leitnerMovesBoxes() {
        val now = 1_000_000L
        val day = 24 * 3600 * 1000L
        val wrong = Leitner.next(currentBox = 3, correct = false, nowMs = now)
        assertEquals(1, wrong.box)
        assertEquals(now + day, wrong.nextReviewAt)
        val right = Leitner.next(currentBox = 1, correct = true, nowMs = now)
        assertEquals(2, right.box)
        assertEquals(now + 3 * day, right.nextReviewAt)
        assertEquals(Leitner.MAX_BOX, Leitner.next(Leitner.MAX_BOX, true, now).box)
        assertTrue(Leitner.isMistake(timesWrong = 1, box = 2))
        assertFalse(Leitner.isMistake(timesWrong = 1, box = 3))
        assertFalse(Leitner.isMistake(timesWrong = 0, box = 1))
    }

    @Test fun readinessFromLastFiveExams() {
        assertNull(Readiness.percent(emptyList()))
        assertEquals(85, Readiness.percent(listOf(17, 17, 17, 17, 17)))
        assertEquals(100, Readiness.percent(listOf(20, 20, 20, 20, 20, 0, 0)))
        assertEquals(90, Readiness.percent(listOf(18)))
    }

    @Test fun bundledContentIsValid() {
        val content = AppJson.decodeFromString(ContentJson.serializer(), File("src/main/assets/questions.json").readText())
        assertEquals(emptyList<String>(), ContentValidator.validate(content))
        assertEquals(40, content.questions.size)
        // Har bir rasm nomi uchun drawable bor.
        val drawables = File("src/main/res/drawable").list()!!.map { it.removeSuffix(".xml") }.toSet()
        content.questions.mapNotNull { it.image }.forEach { assertTrue(it, it in drawables) }
    }

    @Test fun validatorCatchesErrors() {
        val content = AppJson.decodeFromString(ContentJson.serializer(), File("src/main/assets/questions.json").readText())
        val broken = content.copy(questions = content.questions.take(3).mapIndexed { i, q -> if (i == 0) q.copy(source = "", correct = 9) else q })
        val errors = ContentValidator.validate(broken)
        assertTrue(errors.any { "source" in it })
        assertTrue(errors.any { "indeksi" in it })
        assertTrue(errors.any { "bilet" in it })
    }

    @Test fun answerOrderIsStableAndNotAlwaysFirst() {
        val positionsOfCorrect = (1..40).map { id -> ContentLoader.displayOrder(id, 3)[0] }
        assertTrue("to'g'ri javob har xil o'rinda bo'lishi kerak", positionsOfCorrect.toSet().size > 1)
        assertEquals(ContentLoader.displayOrder(7, 4), ContentLoader.displayOrder(7, 4))
        assertEquals(setOf(0, 1, 2, 3), ContentLoader.displayOrder(7, 4).toSet())
    }
}
