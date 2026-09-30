package com.example.nianchulai


internal data class ReadingSegment(val text: String, val language: Language, val sentenceIndex: Int = 0)
internal data class ReadingSentence(val text: String, val start: Int, val end: Int)

internal enum class Language { CHINESE, ENGLISH }
internal enum class ReadingMode { CHINESE, MIXED }

/** The offsets refer to the original article, including its punctuation and spacing. */
internal fun sentencesForReading(article: String): List<ReadingSentence> {
    val sentences = mutableListOf<ReadingSentence>()
    var start = 0
    fun add(end: Int) {
        var first = start
        var last = end
        while (first < last && article[first].isWhitespace()) first++
        while (last > first && article[last - 1].isWhitespace()) last--
        if (first < last) sentences += ReadingSentence(article.substring(first, last), first, last)
        start = end
    }
    for (index in article.indices) {
        val char = article[index]
        val decimal = char == '.' && index > 0 && index + 1 < article.length &&
            article[index - 1].isDigit() && article[index + 1].isDigit()
        val dottedInitial = char == '.' && index > 0 && index + 1 < article.length &&
            article[index - 1] in 'A'..'Z' && article[index + 1] in 'A'..'Z'
        if (!decimal && !dottedInitial && (char in "。！？!?；;" || char == '.' || char == '\n')) add(index + 1)
    }
    add(article.length)
    return sentences
}

/** Keep each utterance short enough for Android TTS and switch voices at script boundaries. */
internal fun splitForReading(article: String, mode: ReadingMode = ReadingMode.CHINESE): List<ReadingSegment> {
    val result = mutableListOf<ReadingSegment>()
    sentencesForReading(article).forEachIndexed { sentenceIndex, sentence ->
        val buffer = StringBuilder()
        var language: Language? = null
        val currencyRanges = Regex("NT\\$\\s*[\\d,]+(?:\\.\\d{1,2})?", RegexOption.IGNORE_CASE)
            .findAll(sentence.text).map { it.range }.toList()

        fun flush() {
            val text = buffer.toString().trim()
            if (text.isNotEmpty()) result += ReadingSegment(text, language ?: Language.CHINESE, sentenceIndex)
            buffer.clear()
            language = null
        }

        for ((index, char) in sentence.text.withIndex()) {
            val nextLanguage = when {
                mode == ReadingMode.CHINESE || currencyRanges.any { index in it } -> Language.CHINESE
                char.isLatinLetter() -> Language.ENGLISH
                char.isHanOrCjkSymbol() -> Language.CHINESE
                char.isDigit() -> numberLanguage(sentence.text, index)
                else -> null
            }
            if (nextLanguage != null && language != null && nextLanguage != language) flush()
            if (nextLanguage != null) language = nextLanguage
            buffer.append(char)
            if (buffer.length >= 700) flush()
        }
        flush()
    }
    return result
}

private fun numberLanguage(article: String, index: Int): Language {
    // Keep a whole number with its preceding word; a number at the start of a
    // sentence follows the next word. Unattached numbers default to Chinese.
    fun nearby(indices: IntProgression): Language? {
        for (i in indices) {
            val char = article[i]
            if (char.isLatinLetter()) return Language.ENGLISH
            if (char.isHanOrCjkSymbol()) return Language.CHINESE
            if (char.isDigit() || char.isWhitespace() || char in ",，.．") continue
            break
        }
        return null
    }
    return nearby((index - 1) downTo 0)
        ?: nearby((index + 1) until article.length)
        ?: Language.CHINESE
}

private fun Char.isLatinLetter(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z' ||
        this in '\u00c0'..'\u024f'

private fun Char.isHanOrCjkSymbol(): Boolean =
    this in '\u3400'..'\u9fff' || this in '\uf900'..'\ufaff' ||
        this in '\u3000'..'\u303f' || this in '\uff00'..'\uffef'
