package com.example.nianchulai

import java.nio.charset.CharacterCodingException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFileImportTest {
    @Test fun utf8AndBomAreDecodedWithoutChangingArticleContent() {
        val text = "第一段\nEnglish 第二段。"
        assertEquals(text, decodeUtf8Text(text.toByteArray(Charsets.UTF_8)))
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        assertEquals(text, decodeUtf8Text(bom + text.toByteArray(Charsets.UTF_8)))
    }

    @Test fun malformedUtf8IsRejectedInsteadOfReplacingCharacters() {
        assertThrows(CharacterCodingException::class.java) {
            decodeUtf8Text(byteArrayOf(0xE4.toByte(), 0xB8.toByte()))
        }
    }

    @Test fun onlyTxtNamesAreAccepted() {
        assertTrue(isTxtFile("文章.TXT"))
        assertFalse(isTxtFile("article.pdf"))
        assertFalse(isTxtFile("article.txt.pdf"))
    }
}
