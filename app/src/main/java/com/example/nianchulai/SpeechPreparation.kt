package com.example.nianchulai

/** Only the spoken copy is normalized; article text and highlighting remain unchanged. */
internal fun prepareSpeech(text: String, language: Language): String {
    if (language == Language.ENGLISH) {
        return text.replace(Regex("(?<![A-Za-z])(?:[A-Z]\\.){2,}[A-Z]?")) { match ->
            match.value.replace('.', ' ').trim()
        }.replace(Regex("(?<![A-Za-z])[A-Z]{2,4}(?![A-Za-z])")) { match ->
            match.value.toCharArray().joinToString(" ")
        }.replace(Regex("(\\d+(?:\\.\\d+)?)%"), "$1 percent")
    }
    var spoken = text
    spoken = spoken.replace(Regex("(?<!\\d)(\\d{4})[/-](\\d{1,2})[/-](\\d{1,2})(?!\\d)")) {
        "${yearDigits(it.groupValues[1])}年${chineseNumber(it.groupValues[2])}月${chineseNumber(it.groupValues[3])}日"
    }
    spoken = spoken.replace(Regex("(?<!\\d)(\\d{4})年(\\d{1,2})月(\\d{1,2})日")) {
        "${yearDigits(it.groupValues[1])}年${chineseNumber(it.groupValues[2])}月${chineseNumber(it.groupValues[3])}日"
    }
    spoken = spoken.replace(Regex("(?<!\\d)(\\d{4})年")) { "${yearDigits(it.groupValues[1])}年" }
    spoken = spoken.replace(Regex("NT\\$\\s*([\\d,]+)(?:\\.(\\d{1,2}))?", RegexOption.IGNORE_CASE)) {
        "新臺幣${chineseNumber(it.groupValues[1])}元" +
            it.groupValues[2].takeIf(String::isNotEmpty)?.let { cents -> "${chineseNumber(cents)}分" }.orEmpty()
    }
    spoken = spoken.replace(Regex("(?<!\\d)(\\d+(?:\\.\\d+)?)%")) {
        "百分之${chineseNumber(it.groupValues[1])}"
    }
    return spoken
}

private val digits = "零一二三四五六七八九"

private fun yearDigits(year: String): String = year.map { digits[it - '0'] }.joinToString("")

private fun chineseNumber(value: String): String {
    val clean = value.replace(",", "")
    if ('.' in clean) {
        val parts = clean.split('.', limit = 2)
        return chineseNumber(parts[0]) + "點" + yearDigits(parts[1])
    }
    val number = clean.toIntOrNull() ?: return clean
    if (number == 0) return "零"
    if (number >= 10000) return clean // Let the installed Chinese TTS engine handle larger values.
    val units = listOf("千", "百", "十", "")
    val padded = number.toString().padStart(4, '0')
    val result = StringBuilder()
    var pendingZero = false
    for ((index, char) in padded.withIndex()) {
        val digit = char - '0'
        if (digit == 0) {
            if (result.isNotEmpty()) pendingZero = true
        } else {
            if (pendingZero) result.append('零')
            pendingZero = false
            if (!(digit == 1 && index == 2 && result.isEmpty())) result.append(digits[digit])
            result.append(units[index])
        }
    }
    return result.toString()
}
