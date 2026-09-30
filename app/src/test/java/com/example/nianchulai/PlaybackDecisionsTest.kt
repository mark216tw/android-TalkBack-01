package com.example.nianchulai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackDecisionsTest {
    @Test fun timerWaitsThroughAllLanguagePartsOfCurrentSentence() {
        assertFalse(finishAfterSegment(true, 4, 4))
        assertTrue(finishAfterSegment(true, 4, 5))
        assertTrue(finishAfterSegment(true, 4, null))
        assertFalse(finishAfterSegment(false, 4, 5))
    }

    @Test fun queueSkipsRemovedOrEmptyArticlesInSelectedOrder() {
        val ids = listOf("a", "removed", "blank", "b")
        val articles = listOf(SavedArticle("a", "甲", "內容"),
            SavedArticle("blank", "空白", "  "), SavedArticle("b", "乙", "Hello"))
        assertEquals(0, nextPlayableIndex(ids, 0, articles))
        assertEquals(3, nextPlayableIndex(ids, 1, articles))
        assertNull(nextPlayableIndex(ids, 4, articles))
    }
}
