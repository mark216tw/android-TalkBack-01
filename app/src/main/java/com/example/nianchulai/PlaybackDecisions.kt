package com.example.nianchulai

/** A sleep timer expires between sentences, not between the language segments of one sentence. */
internal fun finishAfterSegment(timerExpired: Boolean, currentSentence: Int, nextSentence: Int?): Boolean =
    timerExpired && nextSentence != currentSentence

/** Deleted and empty articles are skipped while preserving the chosen queue order. */
internal fun nextPlayableIndex(ids: List<String>, from: Int, articles: List<SavedArticle>): Int? {
    val available = articles.filter { it.text.isNotBlank() }.map { it.id }.toSet()
    return (from.coerceAtLeast(0) until ids.size).firstOrNull { ids[it] in available }
}
