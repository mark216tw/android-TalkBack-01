package com.example.nianchulai

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal data class ImportedTextFile(val name: String, val text: String)

internal fun decodeUtf8Text(bytes: ByteArray): String {
    val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return decoder.decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
}

internal fun isTxtFile(name: String): Boolean = name.endsWith(".txt", ignoreCase = true)

/** The system file picker grants access to this URI; no storage permission is needed. */
internal fun importTextFile(resolver: ContentResolver, uri: Uri): ImportedTextFile {
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: throw IOException("無法辨識檔名，請選擇 .txt 檔案。")
    if (!isTxtFile(name)) throw IOException("僅支援 .txt 文字檔。")

    val bytes = ByteArrayOutputStream()
    val input = resolver.openInputStream(uri) ?: throw IOException("無法開啟檔案。")
    input.use { stream ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            if (bytes.size() + read > MAX_FILE_BYTES) throw IOException("檔案超過 2 MB，請選擇較短的文章。")
            bytes.write(buffer, 0, read)
        }
    }
    val text = try {
        decodeUtf8Text(bytes.toByteArray())
    } catch (_: java.nio.charset.CharacterCodingException) {
        throw IOException("檔案不是有效的 UTF-8 編碼，請轉存為 UTF-8 後再匯入。")
    }
    if (text.isBlank()) throw IOException("檔案沒有可朗讀的文字。")
    return ImportedTextFile(name, text)
}

private const val MAX_FILE_BYTES = 2 * 1024 * 1024
