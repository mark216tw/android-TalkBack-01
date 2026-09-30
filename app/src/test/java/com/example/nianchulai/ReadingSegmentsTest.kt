package com.example.nianchulai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingSegmentsTest {
    @Test fun mixedChineseAndEnglishUsesSeparateVoicesInOrder() {
        val segments = splitForReading("今天介紹 Android 和 TalkBack。Hello world!", ReadingMode.MIXED)
        assertEquals(
            listOf(Language.CHINESE, Language.ENGLISH, Language.CHINESE,
                Language.ENGLISH, Language.CHINESE, Language.ENGLISH),
            segments.map { it.language }
        )
        assertTrue(segments.any { it.text.contains("Android") })
        assertTrue(segments.any { it.text.contains("TalkBack") })
    }

    @Test fun longArticleNeverProducesOversizedUtterances() {
        val segments = splitForReading("中".repeat(2000))
        assertEquals(2000, segments.sumOf { it.text.length })
        assertTrue(segments.all { it.text.length <= 700 })
    }

    @Test fun blankArticleHasNoUtterances() {
        assertTrue(splitForReading(" \n  ").isEmpty())
    }

    @Test fun chineseModeKeepsNumbersAndLatinTextInChineseVoice() {
        val segments = splitForReading("今天有 3 本書，Android 14 更新了。")
        assertEquals(listOf(Language.CHINESE), segments.map { it.language })
        assertTrue(segments.single().text.contains("3"))
    }

    @Test fun mixedModeUsesChineseForChineseContextNumbers() {
        val segments = splitForReading("我有 3 本書。2026 年再讀。", ReadingMode.MIXED)
        assertTrue(segments.all { it.language == Language.CHINESE })
        assertEquals("我有 3 本書。2026 年再讀。", segments.joinToString("") { it.text })
    }

    @Test fun mixedModeKeepsEnglishContextNumbersWithEnglishVoice() {
        val segments = splitForReading("Android 14 更新了", ReadingMode.MIXED)
        assertEquals(Language.ENGLISH, segments.first().language)
        assertTrue(segments.first().text.contains("14"))
        assertEquals(Language.CHINESE, segments.last().language)
    }

    @Test fun sentencesKeepOriginalOffsetsAndDoNotSplitDecimals() {
        val article = "  今天是 2026/09/30。\n費率 3.5%！  再試一次。"
        val sentences = sentencesForReading(article)
        assertEquals(listOf("今天是 2026/09/30。", "費率 3.5%！", "再試一次。"),
            sentences.map { it.text })
        assertTrue(sentences.all { article.substring(it.start, it.end) == it.text })
        assertEquals(listOf(0, 1, 2), splitForReading(article).map { it.sentenceIndex })
    }

    @Test fun mixedCurrencyStaysWithChineseVoice() {
        val segments = splitForReading("付款 NT$1,200 元", ReadingMode.MIXED)
        assertTrue(segments.all { it.language == Language.CHINESE })
    }
}
