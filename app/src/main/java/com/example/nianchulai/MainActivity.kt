package com.example.nianchulai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var sharedText by mutableStateOf<String?>(null)
    private var importPreview by mutableStateOf<ImportedTextFile?>(null)
    private var importError by mutableStateOf<String?>(null)

    private val textFilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { importTextFile(contentResolver, uri) } }
            result.onSuccess { importPreview = it }
                .onFailure { importError = it.message ?: "無法讀取檔案。" }
        }
    }

    private fun acceptShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptShare(intent)
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Audio still works if notification permission is declined. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) acceptShare(intent)
        val reader = ReadingSession.reader(this)
        val store = ArticleStore(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                    ReadingScreen(reader, store, sharedText, importPreview, importError,
                        shareHandled = { sharedText = null },
                        requestImport = {
                            importError = null
                            importPreview = null
                            textFilePicker.launch(arrayOf("*/*"))
                        },
                        importHandled = { importPreview = null },
                        errorHandled = { importError = null },
                        play = { _, speed, position, mode ->
                            if (Build.VERSION.SDK_INT >= 33 &&
                                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            val intent = Intent(this, ReadingService::class.java)
                                .setAction(ReadingService.START)
                                .putExtra(ReadingService.SPEED, speed)
                                .putExtra(ReadingService.POSITION, position)
                                .putExtra(ReadingService.MODE, mode.name)
                            startForegroundService(intent)
                        },
                        control = { action ->
                            startService(Intent(this, ReadingService::class.java).setAction(action))
                        },
                        seek = { sentence ->
                            startService(Intent(this, ReadingService::class.java)
                                .setAction(ReadingService.SEEK).putExtra(ReadingService.POSITION, sentence))
                        },
                        startPlaylist = { resume ->
                            if (Build.VERSION.SDK_INT >= 33 &&
                                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            startForegroundService(Intent(this, ReadingService::class.java)
                                .setAction(ReadingService.START_PLAYLIST)
                                .putExtra(ReadingService.RESUME_PLAYLIST, resume))
                        },
                        setTimer = { minutes ->
                            val intent = Intent(this, ReadingService::class.java)
                                .setAction(if (minutes == null) ReadingService.CANCEL_TIMER else ReadingService.SET_TIMER)
                            if (minutes != null) intent.putExtra(ReadingService.MINUTES, minutes)
                            startService(intent)
                        },
                        openVoiceSettings = {
                            val install = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                            startActivity(if (install.resolveActivity(packageManager) != null) install
                                else Intent(Settings.ACTION_SETTINGS))
                        })
                }
            }
        }
    }
}

@Composable
private fun ReadingScreen(
    reader: Reader,
    store: ArticleStore,
    sharedText: String?,
    importPreview: ImportedTextFile?,
    importError: String?,
    shareHandled: () -> Unit,
    requestImport: () -> Unit,
    importHandled: () -> Unit,
    errorHandled: () -> Unit,
    play: (String, Float, Int, ReadingMode) -> Unit,
    control: (String) -> Unit,
    seek: (Int) -> Unit,
    startPlaylist: (Boolean) -> Unit,
    setTimer: (Int?) -> Unit,
    openVoiceSettings: () -> Unit
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var article by remember { mutableStateOf(store.draft) }
    var speed by remember { mutableFloatStateOf(store.speed) }
    var mode by remember { mutableStateOf(store.mode) }
    var saved by remember { mutableStateOf(store.articles()) }
    var playlistIds by remember { mutableStateOf(store.playlistIds()) }
    var search by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editingTitle by remember { mutableStateOf("") }
    var sentenceFontSize by remember { mutableIntStateOf(store.sentenceFontSize) }
    var followingSentence by remember { mutableStateOf(true) }
    val active = reader.status == ReaderStatus.PLAYING || reader.status == ReaderStatus.PAUSED
    val inPlaylist = active && ReadingSession.playlistArticleId != null
    val bookmark = if (!active) store.bookmark() else null
    val displayedArticle = if (inPlaylist) reader.readingText else article
    val sentences = remember(displayedArticle) { sentencesForReading(displayedArticle) }
    val sentenceListState = rememberLazyListState()
    val isDraggingSentences by sentenceListState.interactionSource.collectIsDraggedAsState()
    val readerScroll = rememberScrollState()
    val libraryScroll = rememberScrollState()
    val playlistScroll = rememberScrollState()
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(ReadingSession.timerEndsAt) {
        while (ReadingSession.timerEndsAt > 0) {
            now = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }

    LaunchedEffect(displayedArticle) { followingSentence = true }
    LaunchedEffect(isDraggingSentences) {
        if (isDraggingSentences) followingSentence = false
    }
    LaunchedEffect(reader.currentSentenceIndex, active, displayedArticle, followingSentence, page) {
        if (page == 1 && followingSentence && active &&
            reader.readingText == displayedArticle && sentences.isNotEmpty()) {
            sentenceListState.animateScrollToItem(reader.currentSentenceIndex)
        }
    }

    fun updateArticle(text: String) {
        if (active) control(ReadingService.STOP)
        article = text
        store.draft = text
    }

    fun saveNewArticle() {
        if (article.isBlank()) return
        val newItem = store.save(article)
        saved = store.articles()
        editingId = newItem.id
        editingTitle = newItem.title
        page = 0
    }

    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            updateArticle(sharedText)
            editingId = null
            page = 0
            shareHandled()
        }
    }

    if (importPreview != null) {
        AlertDialog(
            onDismissRequest = importHandled,
            title = { Text("預覽文字檔") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${importPreview.name} · ${importPreview.text.length} 字")
                    Text(
                        importPreview.text.take(2500) + if (importPreview.text.length > 2500) "\n…（僅預覽前 2500 字）" else "",
                        modifier = Modifier.fillMaxWidth().height(280.dp).verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    updateArticle(importPreview.text)
                    editingId = null
                    page = 0
                    importHandled()
                }) { Text("確認匯入") }
            },
            dismissButton = { TextButton(onClick = importHandled) { Text("取消") } }
        )
    }
    if (importError != null) {
        AlertDialog(onDismissRequest = errorHandled, title = { Text("匯入失敗") },
            text = { Text(importError) },
            confirmButton = { TextButton(onClick = errorHandled) { Text("知道了") } })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (page == 1) {
            Column(modifier = Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("逐句朗讀", style = MaterialTheme.typography.headlineMedium)
                Text(if (inPlaylist) "播放清單：${ReadingSession.currentTitle}" else "目前輸入的文章",
                    style = MaterialTheme.typography.titleMedium)
                Text(reader.message, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(18 to "小字", 22 to "中字", 28 to "大字").forEach { (size, label) ->
                        FilterChip(selected = sentenceFontSize == size, onClick = {
                            sentenceFontSize = size
                            store.sentenceFontSize = size
                        }, label = { Text(label) })
                    }
                }
                if (!followingSentence) {
                    OutlinedButton(onClick = { followingSentence = true },
                        enabled = active && reader.readingText == displayedArticle) {
                        Text("回到目前句（恢復自動跟隨）")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { control(ReadingService.PREVIOUS) },
                        enabled = active && reader.currentSentenceIndex > 0) { Text("上一句") }
                    Button(onClick = {
                        when (reader.status) {
                            ReaderStatus.PLAYING -> control(ReadingService.PAUSE)
                            ReaderStatus.PAUSED -> control(ReadingService.RESUME)
                            ReaderStatus.READY -> play(displayedArticle, speed, 0, mode)
                            else -> Unit
                        }
                    }, enabled = active || (reader.status == ReaderStatus.READY && sentences.isNotEmpty())) {
                        Text(when (reader.status) {
                            ReaderStatus.PLAYING -> "暫停"
                            ReaderStatus.PAUSED -> "繼續"
                            else -> "朗讀"
                        })
                    }
                    OutlinedButton(onClick = { control(ReadingService.NEXT) },
                        enabled = active && reader.currentSentenceIndex < reader.sentenceCount - 1) {
                        Text("下一句")
                    }
                }
                if (active) {
                    OutlinedButton(onClick = { control(ReadingService.STOP) }) { Text("停止") }
                }
                if (sentences.isEmpty()) {
                    Text("請先到朗讀頁輸入文章，或從文章庫開啟文章。")
                } else {
                    LazyColumn(state = sentenceListState, modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(sentences) { index, sentence ->
                            val selected = active && reader.readingText == displayedArticle &&
                                reader.currentSentenceIndex == index
                            Surface(onClick = {
                                if (active && reader.readingText == displayedArticle) seek(index)
                                else play(displayedArticle, speed, index, mode)
                            }, enabled = reader.status == ReaderStatus.READY || active,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                    else null) {
                                Text("${index + 1}. ${sentence.text}",
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                    fontSize = sentenceFontSize.sp,
                                    lineHeight = (sentenceFontSize * 1.5f).sp,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        } else {
        Column(modifier = Modifier.weight(1f).verticalScroll(
            when (page) { 0 -> readerScroll; 2 -> libraryScroll; else -> playlistScroll }
        ).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (page) {
        0 -> {
        Text("朗讀", style = MaterialTheme.typography.headlineMedium)
        if (inPlaylist) Text("播放清單：${ReadingSession.currentTitle}",
            style = MaterialTheme.typography.titleMedium)
        else Text("貼上文章或從其他 App 分享文字，離線朗讀。")
        if (inPlaylist) {
            OutlinedTextField(value = displayedArticle, onValueChange = {}, readOnly = true,
                label = { Text("目前播放的文章") }, modifier = Modifier.fillMaxWidth().height(200.dp))
        } else {
        if (editingId != null) {
            OutlinedTextField(value = editingTitle, onValueChange = { editingTitle = it },
                label = { Text("文章標題（可重新命名）") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                val id = editingId ?: return@Button
                store.update(id, editingTitle, article)
                saved = store.articles()
            }, enabled = article.isNotBlank()) { Text("儲存修改") }
        }
        OutlinedTextField(
            value = article,
            onValueChange = ::updateArticle,
            label = { Text("文章內容") },
            placeholder = { Text("在這裡輸入或貼上文章…") },
            modifier = Modifier.fillMaxWidth().height(280.dp),
            minLines = 8
        )
        OutlinedButton(onClick = ::saveNewArticle, enabled = article.isNotBlank()) {
            Text("儲存為新文章")
        }
        OutlinedButton(onClick = requestImport) { Text("匯入 .txt 文字檔") }
        }
        Text("朗讀語言", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ReadingMode.CHINESE to "中文", ReadingMode.MIXED to "中英文").forEach { (choice, label) ->
                FilterChip(selected = mode == choice, enabled = !inPlaylist, onClick = {
                    if (active) control(ReadingService.STOP)
                    mode = choice
                    store.mode = choice
                }, label = { Text(label) })
            }
        }
        Text("語速：${String.format(Locale.US, "%.1f", if (inPlaylist) reader.readingSpeed else speed)} 倍")
        Slider(value = speed, onValueChange = { speed = it; store.speed = it },
            valueRange = 0.5f..1.5f, steps = 9, enabled = !inPlaylist)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                when (reader.status) {
                    ReaderStatus.PLAYING -> control(ReadingService.PAUSE)
                    ReaderStatus.PAUSED -> control(ReadingService.RESUME)
                    ReaderStatus.READY -> play(article, speed, 0, mode)
                    else -> Unit
                }
            }, enabled = active || (reader.status == ReaderStatus.READY && article.isNotBlank())) {
                Text(when (reader.status) {
                    ReaderStatus.PLAYING -> "暫停"
                    ReaderStatus.PAUSED -> "繼續"
                    else -> "朗讀"
                })
            }
            OutlinedButton(onClick = { control(ReadingService.STOP) },
                enabled = active) { Text("停止") }
        }
        if (!active && bookmark != null && reader.status == ReaderStatus.READY) {
            OutlinedButton(onClick = {
                val last = store.bookmark() ?: return@OutlinedButton
                article = last.text
                store.draft = last.text
                editingId = null
                speed = last.speed
                mode = last.mode
                store.mode = last.mode
                play(last.text, last.speed, last.position, last.mode)
            }) { Text("從上次進度繼續") }
        }
        Text(reader.message, style = MaterialTheme.typography.bodyMedium)
        Text("睡眠計時器", style = MaterialTheme.typography.titleMedium)
        val remaining = ((ReadingSession.timerEndsAt - now).coerceAtLeast(0) / 1000)
        if (ReadingSession.timerFinishing) Text("時間已到，唸完目前句子後停止。")
        else if (ReadingSession.timerEndsAt > 0) Text("剩餘 ${remaining / 60}:${String.format(Locale.US, "%02d", remaining % 60)}")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(15, 30, 60).forEach { minutes ->
                FilterChip(selected = false, enabled = active,
                    onClick = { setTimer(minutes) }, label = { Text("$minutes 分") })
            }
        }
        if (ReadingSession.timerEndsAt > 0) {
            OutlinedButton(onClick = { setTimer(null) }) { Text("取消計時") }
        }
        if (reader.currentText.isNotEmpty()) {
            Text("目前句子", style = MaterialTheme.typography.titleMedium)
            Text(reader.currentText, style = MaterialTheme.typography.bodyLarge)
        }
        if (reader.status == ReaderStatus.ERROR || reader.message.startsWith("找不到英文的離線語音")) {
            Button(onClick = openVoiceSettings) { Text("安裝／設定離線語音") }
        }
        }
        2 -> {
        Text("文章庫", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(value = search, onValueChange = { search = it },
            label = { Text("搜尋標題或內文") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = ::saveNewArticle, enabled = article.isNotBlank()) { Text("儲存為新文章") }
        val visible = saved.filter { item ->
            item.title.contains(search, ignoreCase = true) || item.text.contains(search, ignoreCase = true)
        }
        if (visible.isEmpty()) Text(if (search.isBlank()) "尚未儲存文章。" else "找不到符合的文章。")
        visible.forEach { item ->
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    updateArticle(item.text)
                    editingId = item.id
                    editingTitle = item.title
                    page = 0
                }) { Text("開啟／編輯") }
                OutlinedButton(onClick = {
                    store.delete(item.id)
                    saved = store.articles()
                    playlistIds = store.playlistIds()
                    if (editingId == item.id) editingId = null
                }) { Text("刪除") }
            }
        }
        }
        else -> {
            Text("播放清單", style = MaterialTheme.typography.headlineMedium)
            Text("從文章庫選擇多篇文章，依下方順序連續朗讀。")
            val queue = playlistIds.mapNotNull { id -> saved.firstOrNull { it.id == id } }
            if (queue.isEmpty()) Text("播放清單尚無文章，請先到文章庫儲存文章。")
            queue.forEachIndexed { index, item ->
                Text("${index + 1}. ${item.title}", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val changed = playlistIds.toMutableList()
                        val old = changed[index]
                        changed[index] = changed[index - 1]
                        changed[index - 1] = old
                        store.setPlaylist(changed)
                        playlistIds = store.playlistIds()
                    }, enabled = index > 0) { Text("上移") }
                    OutlinedButton(onClick = {
                        val changed = playlistIds.toMutableList()
                        val old = changed[index]
                        changed[index] = changed[index + 1]
                        changed[index + 1] = old
                        store.setPlaylist(changed)
                        playlistIds = store.playlistIds()
                    }, enabled = index < queue.lastIndex) { Text("下移") }
                    OutlinedButton(onClick = {
                        store.setPlaylist(playlistIds.filterNot { it == item.id })
                        playlistIds = store.playlistIds()
                    }) { Text("移除") }
                }
            }
            Button(onClick = { startPlaylist(false); page = 0 },
                enabled = queue.isNotEmpty() && reader.status == ReaderStatus.READY) { Text("從第一篇開始") }
            val progress = store.playlistProgress()
            if (progress != null && reader.status == ReaderStatus.READY) {
                val title = saved.firstOrNull { it.id == progress.articleId }?.title.orEmpty()
                OutlinedButton(onClick = { startPlaylist(true); page = 0 }) {
                    Text("續聽：$title · 第 ${progress.sentence + 1} 句")
                }
            }
            if (ReadingSession.playlistArticleId != null) {
                Text("目前：${ReadingSession.currentTitle} · 第 ${reader.currentSentenceIndex + 1} 句")
            }
            HorizontalDivider()
            Text("加入文章", style = MaterialTheme.typography.titleMedium)
            saved.filterNot { it.id in playlistIds }.forEach { item ->
                OutlinedButton(onClick = {
                    store.setPlaylist(playlistIds + item.id)
                    playlistIds = store.playlistIds()
                }) { Text("加入：${item.title}") }
            }
        }
        }
        Spacer(Modifier.height(16.dp))
        }
        }
        NavigationBar {
            listOf("朗讀", "逐句", "文章庫", "播放清單").forEachIndexed { index, label ->
                NavigationBarItem(selected = page == index, onClick = { page = index },
                    icon = { Text(listOf("▶", "☷", "▤", "♫")[index]) }, label = { Text(label) })
            }
        }
    }
}
