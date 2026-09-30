package com.example.nianchulai

internal enum class ArticlePlaybackAction { START, PAUSE, RESUME }

internal fun articlePlaybackAction(id: String, currentId: String?, status: ReaderStatus): ArticlePlaybackAction =
    when {
        id != currentId -> ArticlePlaybackAction.START
        status == ReaderStatus.PLAYING -> ArticlePlaybackAction.PAUSE
        status == ReaderStatus.PAUSED -> ArticlePlaybackAction.RESUME
        else -> ArticlePlaybackAction.START
    }

/** A sleep timer expires between sentences, not between the language segments of one sentence. */
internal fun finishAfterSegment(timerExpired: Boolean, currentSentence: Int, nextSentence: Int?): Boolean =
    timerExpired && nextSentence != currentSentence

internal fun playlistStartIndex(ids: List<String>, articleId: String?): Int =
    ids.indexOf(articleId).takeIf { it >= 0 } ?: 0

/** Text alone cannot identify an article: users can save identical copies or edit a draft. */
internal fun savedArticleForReading(id: String?, text: String, articles: List<SavedArticle>): SavedArticle? =
    articles.firstOrNull { id != null && it.id == id && it.text == text }

/** Language changes restart the current sentence, including all its new language parts. */
internal fun segmentIndexForSentence(segments: List<ReadingSegment>, sentence: Int): Int =
    segments.indexOfFirst { it.sentenceIndex >= sentence }.coerceAtLeast(0)

/** Deleted and empty articles are skipped while preserving the chosen queue order. */
internal fun nextPlayableIndex(ids: List<String>, from: Int, articles: List<SavedArticle>): Int? {
    val available = articles.filter { it.text.isNotBlank() }.map { it.id }.toSet()
    return (from.coerceAtLeast(0) until ids.size).firstOrNull { ids[it] in available }
}
