package com.example.nianchulai

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechPreparationTest {
    @Test fun chineseDatesReadYearsAsDigits() {
        assertEquals("二零二六年九月三十日", prepareSpeech("2026/09/30", Language.CHINESE))
        assertEquals("二零二六年九月三十日", prepareSpeech("2026年9月30日", Language.CHINESE))
    }

    @Test fun chinesePercentagesAndCurrencyAreExpanded() {
        assertEquals("成長百分之三點五", prepareSpeech("成長3.5%", Language.CHINESE))
        assertEquals("新臺幣一千二百元", prepareSpeech("NT$1,200", Language.CHINESE))
    }

    @Test fun englishAbbreviationsAndPercentagesAreExpanded() {
        assertEquals("A P I 25 percent", prepareSpeech("API 25%", Language.ENGLISH))
        assertEquals("U S A", prepareSpeech("U.S.A.", Language.ENGLISH))
        assertEquals("NASA", prepareSpeech("NASA", Language.CHINESE))
    }
}
