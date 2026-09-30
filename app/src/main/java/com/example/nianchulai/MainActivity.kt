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
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
        val themeSettings = ThemeSettings(this)
        setContent {
            AppTheme(themeSettings, this) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ReadingScreen(reader, store, themeSettings, sharedText, importPreview, importError,
                        shareHandled = { sharedText = null },
                        requestImport = {
                            importError = null
                            importPreview = null
                            textFilePicker.launch(arrayOf("*/*"))
                        },
                        importHandled = { importPreview = null },
                        errorHandled = { importError = null },
                         play = { _, speed, position, mode, articleId ->
                            if (Build.VERSION.SDK_INT >= 33 &&
                                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            val intent = Intent(this, ReadingService::class.java)
                                 .setAction(ReadingService.START)
                                 .putExtra(ReadingService.ARTICLE_ID, articleId)
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
                         startPlaylist = { resume, articleId ->
                            if (Build.VERSION.SDK_INT >= 33 &&
                                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            startForegroundService(Intent(this, ReadingService::class.java)
                                 .setAction(ReadingService.START_PLAYLIST)
                                 .putExtra(ReadingService.PLAYLIST_ARTICLE_ID, articleId)
                                .putExtra(ReadingService.RESUME_PLAYLIST, resume))
                        },
                         setTimer = { minutes ->
                            val intent = Intent(this, ReadingService::class.java)
                                .setAction(if (minutes == null) ReadingService.CANCEL_TIMER else ReadingService.SET_TIMER)
                            if (minutes != null) intent.putExtra(ReadingService.MINUTES, minutes)
                             startService(intent)
                         },
                         changePlaybackSettings = { speed, mode ->
                             startService(Intent(this, ReadingService::class.java)
                                 .setAction(ReadingService.UPDATE_SETTINGS)
                                 .putExtra(ReadingService.SPEED, speed)
                                 .putExtra(ReadingService.MODE, mode.name))
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
    themeSettings: ThemeSettings,
    sharedText: String?,
    importPreview: ImportedTextFile?,
    importError: String?,
    shareHandled: () -> Unit,
    requestImport: () -> Unit,
    importHandled: () -> Unit,
    errorHandled: () -> Unit,
    play: (String, Float, Int, ReadingMode, String?) -> Unit,
    control: (String) -> Unit,
    seek: (Int) -> Unit,
    startPlaylist: (Boolean, String?) -> Unit,
    setTimer: (Int?) -> Unit,
    changePlaybackSettings: (Float, ReadingMode) -> Unit,
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
    var sentenceFontSize by remember { mutableIntStateOf(store.sentenceFontSize) }
    var followingSentence by remember { mutableStateOf(true) }
    var showReadingSettings by rememberSaveable { mutableStateOf(false) }
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var newTitle by rememberSaveable { mutableStateOf("") }
    var showArticleForm by remember { mutableStateOf(false) }
    var formTitle by remember { mutableStateOf("") }
    var formText by remember { mutableStateOf("") }
    var formImportedName by remember { mutableStateOf<String?>(null) }
    var formEditingId by remember { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val active = reader.status == ReaderStatus.PLAYING || reader.status == ReaderStatus.PAUSED
    val inPlaylist = active && ReadingSession.playlistArticleId != null
    val effectiveSpeed = if (active) reader.readingSpeed else speed
    val effectiveMode = if (active) reader.readingMode else mode
    val displayedArticle = if (inPlaylist) reader.readingText else article
    val sentences = remember(displayedArticle) { sentencesForReading(displayedArticle) }
    val sentenceListState = rememberLazyListState()
    val isDraggingSentences by sentenceListState.interactionSource.collectIsDraggedAsState()
    val readerScroll = rememberScrollState()
    val libraryScroll = rememberScrollState()
    val playlistScroll = rememberScrollState()
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    BackHandler(enabled = page == 4) { page = 0 }

    LaunchedEffect(active, reader.readingSpeed, reader.readingMode) {
        if (active) {
            speed = reader.readingSpeed
            mode = reader.readingMode
        }
    }

    LaunchedEffect(ReadingSession.timerEndsAt) {
        while (ReadingSession.timerEndsAt > 0) {
            now = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }

    LaunchedEffect(displayedArticle) { followingSentence = true }
    LaunchedEffect(reader.currentSentenceIndex, reader.readingText) { followingSentence = true }
    LaunchedEffect(isDraggingSentences) {
        if (isDraggingSentences) followingSentence = false
    }
    LaunchedEffect(reader.currentSentenceIndex, active, displayedArticle, followingSentence, page) {
        if (page == 1 && followingSentence && active &&
            reader.readingText == displayedArticle && sentences.isNotEmpty()) {
            sentenceListState.animateScrollToItem(reader.currentSentenceIndex.coerceIn(0, sentences.lastIndex))
        }
    }

    fun updateArticle(text: String) {
        if (active) control(ReadingService.STOP)
        article = text
        store.draft = text
    }

    fun saveNewArticle() {
        if (article.isBlank()) return
        newTitle = defaultArticleTitle(article)
        showSaveDialog = true
    }

    fun readArticle(text: String, rate: Float, position: Int, readingMode: ReadingMode, id: String? = editingId) {
        val savedId = savedArticleForReading(id, text, saved)?.id
        followingSentence = true
        play(text, rate, position, readingMode, savedId)
    }

    fun toggleReading() {
        when (reader.status) {
            ReaderStatus.PLAYING -> control(ReadingService.PAUSE)
            ReaderStatus.PAUSED -> control(ReadingService.RESUME)
            ReaderStatus.READY -> if (sentences.isNotEmpty()) readArticle(displayedArticle, speed, 0, mode)
            else -> Unit
        }
    }

    fun toggleArticle(item: SavedArticle, playlist: Boolean) {
        when (articlePlaybackAction(item.id, ReadingSession.articleId, reader.status)) {
            ArticlePlaybackAction.PAUSE -> control(ReadingService.PAUSE)
            ArticlePlaybackAction.RESUME -> control(ReadingService.RESUME)
            ArticlePlaybackAction.START -> {
                followingSentence = true
                if (playlist) startPlaylist(false, item.id)
                else {
                    article = item.text
                    store.draft = item.text
                    editingId = item.id
                    readArticle(item.text, effectiveSpeed, 0, effectiveMode, item.id)
                }
                page = 1
            }
        }
    }

    fun selectReadingSettings(rate: Float, choice: ReadingMode) {
        if (active) changePlaybackSettings(rate, choice)
        else {
            speed = rate
            mode = choice
            store.speed = rate
            store.mode = choice
        }
    }

    LaunchedEffect(importPreview) {
        if (importPreview != null) {
            formTitle = ""
            formText = importPreview.text
            formImportedName = importPreview.name
            formEditingId = null
            showArticleForm = true
            page = 2
            importHandled()
        }
    }

    if (showArticleForm) {
        ArticleFormDialog(formTitle, formText, formImportedName,
            changeTitle = { formTitle = it }, changeText = { formText = it },
            save = {
                val id = formEditingId
                if (id == null) store.save(formText, formTitle)
                else store.update(id, formTitle, formText)
                saved = store.articles()
                search = ""
                showArticleForm = false
                formText = ""
                page = 2
            }, dismiss = { showArticleForm = false; formText = "" }, editing = formEditingId != null)
    }

    val deleting = saved.firstOrNull { it.id == deletingId }
    if (deleting != null) {
        AlertDialog(onDismissRequest = { deletingId = null }, title = { Text("刪除文章？") },
            text = { Text("確定刪除「${deleting.title}」？此文章也會從播放清單移除。") },
            confirmButton = {
                TextButton(onClick = {
                    store.delete(deleting.id)
                    saved = store.articles()
                    playlistIds = store.playlistIds()
                    if (editingId == deleting.id) editingId = null
                    deletingId = null
                }) { Text("刪除") }
            }, dismissButton = { TextButton(onClick = { deletingId = null }) { Text("取消") } })
    }
    ReadingSession.settingsError?.let { error ->
        AlertDialog(onDismissRequest = { ReadingSession.settingsError = null }, title = { Text("無法切換朗讀設定") },
            text = { Text(error) }, confirmButton = {
                TextButton(onClick = { ReadingSession.settingsError = null }) { Text("知道了") }
            })
    }

    if (showSaveDialog) {
        AlertDialog(onDismissRequest = { showSaveDialog = false },
            title = { Text("儲存為新文章") },
            text = {
                OutlinedTextField(value = newTitle, onValueChange = { newTitle = it },
                    label = { Text("文章標題（必填）") }, singleLine = true,
                    isError = newTitle.isBlank(), modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(enabled = newTitle.isNotBlank() && article.isNotBlank(), onClick = {
                    val newItem = store.save(article, newTitle)
                    saved = store.articles()
                    editingId = newItem.id
                    showSaveDialog = false
                    page = 0
                }) { Text("儲存") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("取消") } })
    }

    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            updateArticle(sharedText)
            editingId = null
            page = 0
            shareHandled()
        }
    }

    if (importError != null) {
        AlertDialog(onDismissRequest = errorHandled, title = { Text("匯入失敗") },
            text = { Text(importError) },
            confirmButton = { TextButton(onClick = errorHandled) { Text("知道了") } })
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        if (page == 4) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { page = 0 }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "回到主畫面")
                }
                Text("設定", style = MaterialTheme.typography.titleLarge)
            }
            SettingsScreen(themeSettings, active,
                (ReadingSession.timerEndsAt - now).coerceAtLeast(0) / 1000,
                setTimer, sentenceFontSize, selectFontSize = { size ->
                    sentenceFontSize = size
                    store.sentenceFontSize = size
                }, modifier = Modifier.weight(1f).fillMaxWidth())
        } else if (page == 1) {
            Column(modifier = Modifier.weight(1f).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PageHeading("逐句朗讀") {
                    IconButton(onClick = { showReadingSettings = !showReadingSettings }) {
                        Icon(Icons.Default.MoreVert,
                            contentDescription = if (showReadingSettings) "隱藏朗讀設定" else "顯示朗讀設定")
                    }
                }
                if (showReadingSettings) {
                    CompactReadingSettings(effectiveMode, effectiveSpeed, ::selectReadingSettings)
                }
                if (inPlaylist) Text("播放清單：${ReadingSession.currentTitle}",
                    style = MaterialTheme.typography.titleMedium)
                Text(reader.message, style = MaterialTheme.typography.bodyMedium)
                if (reader.status == ReaderStatus.ERROR || reader.message.startsWith("找不到英文的離線語音")) {
                    Button(onClick = openVoiceSettings) { Text("安裝／設定離線語音") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { followingSentence = true; control(ReadingService.PREVIOUS) },
                        modifier = Modifier.weight(1.15f), contentPadding = PaddingValues(horizontal = 4.dp),
                        enabled = active && reader.currentSentenceIndex > 0) { Text("上一句") }
                    OutlinedButton(onClick = { followingSentence = true; control(ReadingService.NEXT) },
                        modifier = Modifier.weight(1.15f), contentPadding = PaddingValues(horizontal = 4.dp),
                        enabled = active && reader.currentSentenceIndex < reader.sentenceCount - 1) { Text("下一句") }
                    Button(onClick = ::toggleReading,
                        modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp),
                        enabled = active || (reader.status == ReaderStatus.READY && sentences.isNotEmpty())) {
                        Text(when (reader.status) {
                            ReaderStatus.PLAYING -> "暫停"
                            ReaderStatus.PAUSED -> "繼續"
                            else -> "朗讀"
                        })
                    }
                    OutlinedButton(onClick = { control(ReadingService.STOP) }, enabled = active,
                        modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("停止") }
                }
                if (sentences.isEmpty()) {
                    Text("請先到朗讀頁輸入文章，或從文章庫開始朗讀。")
                } else {
                    LazyColumn(state = sentenceListState, modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(sentences) { index, sentence ->
                            val selected = active && reader.readingText == displayedArticle &&
                                reader.currentSentenceIndex == index
                            Surface(onClick = {
                                if (active && reader.readingText == displayedArticle) seek(index)
                                else readArticle(displayedArticle, speed, index, mode)
                            }, enabled = reader.status == ReaderStatus.READY || active,
                                 modifier = Modifier.fillMaxWidth(),
                                 shape = RoundedCornerShape(12.dp),
                                 color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                    else null) {
                                 Text("${if (selected) "✓ " else ""}${index + 1}. ${sentence.text}",
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                    fontSize = sentenceFontSize.sp,
                                    lineHeight = (sentenceFontSize * 1.5f).sp,
                                     fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                     color = if (selected) MaterialTheme.colorScheme.onPrimary
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
        PageHeading("朗讀") {
            IconButton(onClick = { page = 4 }) {
                Icon(Icons.Default.Settings, contentDescription = "設定")
            }
        }
        if (inPlaylist) Text("播放清單：${ReadingSession.currentTitle}",
            style = MaterialTheme.typography.titleMedium)
        else Text("貼上文章或從其他 App 分享文字。")
        if (inPlaylist) {
            OutlinedTextField(value = displayedArticle, onValueChange = {}, readOnly = true,
                label = { Text("目前播放的文章") }, modifier = Modifier.fillMaxWidth().height(400.dp))
        } else {
        OutlinedTextField(
            value = article,
            onValueChange = ::updateArticle,
            label = { Text("文章內容") },
            placeholder = { Text("在這裡輸入或貼上文章…") },
            modifier = Modifier.fillMaxWidth().height(400.dp),
            minLines = 8
        )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!inPlaylist) {
                OutlinedButton(onClick = ::saveNewArticle, enabled = article.isNotBlank()) {
                    Text("儲存為新文章")
                }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                if (reader.status != ReaderStatus.PLAYING) toggleReading()
                page = 1
            }, enabled = active || (reader.status == ReaderStatus.READY && sentences.isNotEmpty())) {
                Text("朗讀")
            }
        }
        }
        2 -> {
        PageHeading("文章庫") {
            ArticleActionButton(onClick = {
                formTitle = ""
                formText = ""
                formImportedName = null
                formEditingId = null
                showArticleForm = true
            }) { Icon(Icons.Default.Add, "新增文章") }
            ArticleActionButton(onClick = requestImport) {
                Icon(painterResource(R.drawable.ic_import), "匯入 TXT 文字檔")
            }
        }
        OutlinedTextField(value = search, onValueChange = { search = it },
            label = { Text("搜尋標題或內文") }, modifier = Modifier.fillMaxWidth())
        val visible = saved.filter { item ->
            item.title.contains(search, ignoreCase = true) || item.text.contains(search, ignoreCase = true)
        }
        if (visible.isEmpty()) Text(if (search.isBlank()) "尚未儲存文章。" else "找不到符合的文章。")
        visible.forEach { item ->
              ArticleHeading(item.title)
             Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                   ArticleActionButton(primary = true, onClick = { toggleArticle(item, playlist = false) },
                       enabled = item.text.isNotBlank() && (reader.status == ReaderStatus.READY || active)) {
                      ArticlePlaybackIcon(item.title, articlePlaybackAction(item.id, ReadingSession.articleId, reader.status))
                 }
                  ArticleActionButton(onClick = {
                      formTitle = item.title
                     formText = item.text
                     formImportedName = null
                     formEditingId = item.id
                     showArticleForm = true
                 }) { Icon(Icons.Default.Edit, "編輯：${item.title}") }
                 if (item.id !in playlistIds) {
                      ArticleActionButton(onClick = {
                          store.setPlaylist(playlistIds + item.id)
                         playlistIds = store.playlistIds()
                     }) { Icon(painterResource(R.drawable.ic_playlist_add), "加入清單：${item.title}") }
                 }
                  ArticleActionButton(onClick = { deletingId = item.id }) { Icon(Icons.Default.Delete, "刪除：${item.title}") }
            }
        }
        }
        else -> {
            PageHeading("播放清單")
            val queue = playlistIds.mapNotNull { id -> saved.firstOrNull { it.id == id } }
            if (queue.isEmpty()) Text("播放清單尚無文章，請到文章庫使用加入清單圖示。")
            if (ReadingSession.playlistArticleId != null) {
                Text("目前：${ReadingSession.currentTitle} · 第 ${reader.currentSentenceIndex + 1} 句")
            }
            val progress = store.playlistProgress()
            if (progress != null && reader.status == ReaderStatus.READY) {
                val title = saved.firstOrNull { it.id == progress.articleId }?.title.orEmpty()
                OutlinedButton(onClick = { followingSentence = true; startPlaylist(true, null); page = 1 }) {
                    Text("續聽：$title · 第 ${progress.sentence + 1} 句")
                }
            }
            queue.forEachIndexed { index, item ->
                  ArticleHeading("${index + 1}. ${item.title}", showArticleIcon = false)
                 Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                       ArticleActionButton(primary = true, onClick = { toggleArticle(item, playlist = true) },
                         enabled = item.text.isNotBlank() && (reader.status == ReaderStatus.READY || active)) {
                          ArticlePlaybackIcon(item.title, articlePlaybackAction(item.id, ReadingSession.articleId, reader.status))
                     }
                      ArticleActionButton(onClick = {
                        val changed = playlistIds.toMutableList()
                        val old = changed[index]
                        changed[index] = changed[index - 1]
                        changed[index - 1] = old
                        store.setPlaylist(changed)
                        playlistIds = store.playlistIds()
                     }, enabled = index > 0) { Icon(Icons.Default.KeyboardArrowUp, "上移：${item.title}") }
                      ArticleActionButton(onClick = {
                        val changed = playlistIds.toMutableList()
                        val old = changed[index]
                        changed[index] = changed[index + 1]
                        changed[index + 1] = old
                        store.setPlaylist(changed)
                        playlistIds = store.playlistIds()
                     }, enabled = index < queue.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, "下移：${item.title}") }
                      ArticleActionButton(onClick = {
                        store.setPlaylist(playlistIds.filterNot { it == item.id })
                        playlistIds = store.playlistIds()
                     }) { Icon(Icons.Default.Close, "移除：${item.title}") }
                }
            }
        }
        }
        Spacer(Modifier.height(16.dp))
        }
        }
        NavigationBar {
            listOf("朗讀", "逐句", "文章庫", "播放清單").forEachIndexed { index, label ->
                NavigationBarItem(selected = page == index, onClick = {
                    if (index == 1 && page == 1) toggleReading()
                    else page = index
                }, colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary),
                    icon = { Text(listOf("▶", "☷", "▤", "♫")[index]) },
                    label = { Text(label,
                        fontWeight = if (page == index) FontWeight.Bold else FontWeight.Normal) })
            }
        }
    }
}
