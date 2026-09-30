package com.example.nianchulai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackDecisionsTest {
    @Test fun articleButtonsToggleOnlyTheCurrentArticleAndResetAfterStopOrQueueAdvance() {
        assertEquals(ArticlePlaybackAction.PAUSE, articlePlaybackAction("a", "a", ReaderStatus.PLAYING))
        assertEquals(ArticlePlaybackAction.RESUME, articlePlaybackAction("a", "a", ReaderStatus.PAUSED))
        assertEquals(ArticlePlaybackAction.START, articlePlaybackAction("b", "a", ReaderStatus.PLAYING))
        assertEquals(ArticlePlaybackAction.START, articlePlaybackAction("b", "a", ReaderStatus.PAUSED))
        assertEquals(ArticlePlaybackAction.START, articlePlaybackAction("a", null, ReaderStatus.READY))
        assertEquals(ArticlePlaybackAction.START, articlePlaybackAction("a", "a", ReaderStatus.READY))
        assertEquals(ArticlePlaybackAction.START, articlePlaybackAction("a", "b", ReaderStatus.PLAYING))
    }

    @Test fun languageChangeRestartsSameSentenceAcrossDifferentSegmentation() {
        val text = "第一句。第二句 Hello 世界。第三句。"
        val chinese = splitForReading(text, ReadingMode.CHINESE)
        val mixed = splitForReading(text, ReadingMode.MIXED)
        val chineseStart = segmentIndexForSentence(chinese, 1)
        val mixedStart = segmentIndexForSentence(mixed, 1)
        assertEquals(1, chinese[chineseStart].sentenceIndex)
        assertEquals(1, mixed[mixedStart].sentenceIndex)
        assertEquals("第二句", mixed[mixedStart].text)
        assertTrue(mixed.count { it.sentenceIndex == 1 } > chinese.count { it.sentenceIndex == 1 })
        assertTrue(mixed.drop(mixedStart).any { it.sentenceIndex == 2 })
    }

    @Test fun playingIdentityDistinguishesCopiesAndRejectsChangedOrRemovedDrafts() {
        val first = SavedArticle("a", "原文", "相同內容")
        val copy = SavedArticle("b", "副本", "相同內容")
        val articles = listOf(first, copy)
        assertEquals(copy, savedArticleForReading("b", "相同內容", articles))
        assertNull(savedArticleForReading(null, "相同內容", articles))
        assertNull(savedArticleForReading("b", "尚未儲存的修改", articles))
        assertNull(savedArticleForReading("removed", "相同內容", articles))
    }

    @Test fun selectedArticleStartsAtItsCurrentQueuePositionAndContinuesForward() {
        val articles = listOf(SavedArticle("a", "甲", "甲內容"),
            SavedArticle("b", "乙", "乙內容"), SavedArticle("c", "丙", "丙內容"))
        val ids = listOf("a", "b", "c")
        val start = playlistStartIndex(ids, "b")
        assertEquals(1, nextPlayableIndex(ids, start, articles))
        assertEquals(2, nextPlayableIndex(ids, start + 1, articles))
        assertEquals(2, playlistStartIndex(listOf("c", "a", "b"), "b"))
        assertEquals(0, playlistStartIndex(ids, null))
        assertEquals(0, playlistStartIndex(ids, "removed"))
    }

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
