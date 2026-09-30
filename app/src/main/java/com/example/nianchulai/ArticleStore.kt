package com.example.nianchulai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class SavedArticle(val id: String, val title: String, val text: String)
internal data class ReadingBookmark(val text: String, val position: Int, val speed: Float, val mode: ReadingMode)
internal data class PlaylistProgress(val articleId: String, val sentence: Int, val speed: Float, val mode: ReadingMode)

internal class ArticleStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("articles", Context.MODE_PRIVATE)

    var draft: String
        get() = prefs.getString("draft", "").orEmpty()
        set(value) { prefs.edit().putString("draft", value).apply() }

    var speed: Float
        get() = prefs.getFloat("speed", 1f)
        set(value) { prefs.edit().putFloat("speed", value).apply() }

    var mode: ReadingMode
        get() = prefs.getString("mode", null)?.let { runCatching { ReadingMode.valueOf(it) }.getOrNull() }
            ?: ReadingMode.CHINESE
        set(value) { prefs.edit().putString("mode", value.name).apply() }

    var sentenceFontSize: Int
        get() = prefs.getInt("sentence_font_size", 22).takeIf { it in listOf(18, 22, 28) } ?: 22
        set(value) { prefs.edit().putInt("sentence_font_size", value).apply() }

    fun articles(): List<SavedArticle> = runCatching {
        val array = JSONArray(prefs.getString("saved", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            SavedArticle(item.getString("id"), item.getString("title"), item.getString("text"))
        }
    }.getOrDefault(emptyList())

    fun save(text: String): SavedArticle {
        val title = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(35) ?: "未命名文章"
        val item = SavedArticle(UUID.randomUUID().toString(), title, text)
        writeArticles(listOf(item) + articles())
        return item
    }

    fun delete(id: String) {
        writeArticles(articles().filterNot { it.id == id })
        setPlaylist(playlistIds().filterNot { it == id })
        if (playlistProgress()?.articleId == id) clearPlaylistProgress()
    }

    fun update(id: String, title: String, text: String) {
        writeArticles(articles().map { item ->
            if (item.id == id) item.copy(title = title.trim().ifBlank { "未命名文章" }, text = text)
            else item
        })
    }

    private fun writeArticles(items: List<SavedArticle>) {
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("id", it.id).put("title", it.title).put("text", it.text)) }
        prefs.edit().putString("saved", array.toString()).apply()
    }

    fun playlistIds(): List<String> {
        val available = articles().map { it.id }.toSet()
        return runCatching {
            val array = JSONArray(prefs.getString("playlist", "[]"))
            (0 until array.length()).map { array.getString(it) }.distinct().filter { it in available }
        }.getOrDefault(emptyList())
    }

    fun setPlaylist(ids: List<String>) {
        val available = articles().map { it.id }.toSet()
        val array = JSONArray()
        ids.distinct().filter { it in available }.forEach(array::put)
        prefs.edit().putString("playlist", array.toString()).apply()
        if (playlistProgress()?.articleId !in ids) clearPlaylistProgress()
    }

    fun playlistProgress(): PlaylistProgress? {
        val id = prefs.getString("playlist_article", null) ?: return null
        if (id !in playlistIds()) return null
        val mode = runCatching {
            ReadingMode.valueOf(prefs.getString("playlist_mode", ReadingMode.CHINESE.name).orEmpty())
        }.getOrDefault(ReadingMode.CHINESE)
        return PlaylistProgress(id, prefs.getInt("playlist_sentence", 0),
            prefs.getFloat("playlist_speed", 1f), mode)
    }

    fun setPlaylistProgress(progress: PlaylistProgress) {
        prefs.edit().putString("playlist_article", progress.articleId)
            .putInt("playlist_sentence", progress.sentence)
            .putFloat("playlist_speed", progress.speed)
            .putString("playlist_mode", progress.mode.name).apply()
    }

    fun clearPlaylistProgress() {
        prefs.edit().remove("playlist_article").remove("playlist_sentence")
            .remove("playlist_speed").remove("playlist_mode").apply()
    }

    fun bookmark(): ReadingBookmark? {
        val text = prefs.getString("bookmark_text", null) ?: return null
        val mode = prefs.getString("bookmark_mode", null)?.let {
            runCatching { ReadingMode.valueOf(it) }.getOrNull()
        } ?: ReadingMode.MIXED // Bookmarks created before this option used mixed reading.
        val position = if (prefs.contains("bookmark_sentence")) prefs.getInt("bookmark_sentence", 0)
            else splitForReading(text, mode).getOrNull(prefs.getInt("bookmark_position", 0))?.sentenceIndex ?: 0
        return ReadingBookmark(text, position, prefs.getFloat("bookmark_speed", 1f), mode)
    }

    fun setBookmark(text: String, position: Int, speed: Float, mode: ReadingMode) {
        prefs.edit().putString("bookmark_text", text).putInt("bookmark_sentence", position)
            .putFloat("bookmark_speed", speed).putString("bookmark_mode", mode.name).apply()
    }

    fun clearBookmark() {
        prefs.edit().remove("bookmark_text").remove("bookmark_position").remove("bookmark_speed")
            .remove("bookmark_mode").remove("bookmark_sentence").apply()
    }
}
