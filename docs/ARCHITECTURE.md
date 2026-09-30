# 系統架構與技術文件

## 技術堆疊

| 項目 | 採用技術 |
| --- | --- |
| 語言／介面 | Kotlin、Jetpack Compose、Material 3 |
| 平台 | Android 8.0+（minSdk 26），compileSdk／targetSdk 35 |
| 語音 | Android `TextToSpeech`，僅選用回報為非網路連線的 `Voice` |
| 背景播放 | `mediaPlayback` 類型前景 `Service`、通知動作與 `PendingIntent` |
| 持久化 | 私有 `SharedPreferences`；文章清單以 JSON 儲存 |
| 匯入 | Storage Access Framework `OpenDocument`、嚴格 UTF-8 解碼 |
| 建置／測試 | Gradle Wrapper 8.9、Android Gradle Plugin 8.7.3、Kotlin 2.0.21、JDK 17、JUnit 4 |

## 元件與責任

```text
外部 App 分享文字 ─┐
系統檔案選擇器 ────┼─> MainActivity / Compose 四個分頁 ──> ArticleStore
使用者操作 ────────┘              │                  └─ 本機草稿、文章、清單、進度與設定
                                 ↓ Intent（動作、句子索引、語速／模式）
                         ReadingService（前景通知、清單、計時器）
                                 │
                                 ↓
                        ReadingSession / Reader
                                 │
             ReadingSegments + SpeechPreparation
                                 │
                                 ↓
                  裝置上的 Android TextToSpeech 引擎
```

- **`MainActivity.kt`**：接收 `ACTION_SEND` 純文字；使用系統檔案選擇器讀取 `.txt`；Compose 畫面提供朗讀、逐句、文章庫、播放清單。文章由草稿傳給服務時，服務從本機讀取草稿，避免長文章超過 Android Intent 的 Binder 大小限制。
- **`ReadingService.kt`**：接收開始、暫停、續讀、停止、跳句、播放清單與計時器命令；維持前景通知，依選定順序接續下一篇並保存進度。睡眠計時以 `SystemClock.elapsedRealtime()` 計算，服務在目前句的最後一個語音片段完成後停止。
- **`Reader.kt`**：持有 TTS 引擎與朗讀狀態，逐個送出語音片段；利用 `UtteranceProgressListener` 在完成時推進。暫停／跳句會停止現有 utterance，以 generation／utterance ID 忽略過期回呼。`ReadingSession` 在同一 App 行程內讓畫面與服務觀察同一個 Reader。
- **`ReadingSegments.kt`**：分出具原文位置的句子，再按語種與最長 700 字元切成送給 TTS 的片段。中文模式使用中文語音；中英文模式依文字片段選擇中文或英文語音，數字依鄰近語境決定。
- **`SpeechPreparation.kt`**：在**送往 TTS 的副本**中處理常見日期、百分比、新臺幣金額與簡短英文縮寫；原文及逐句列表不受影響。
- **`ArticleStore.kt`**：讀寫草稿、語言、語速、逐句字體、文章 JSON、播放清單 ID、單篇書籤與清單進度。更名與修改保留文章 ID。
- **`TextFileImport.kt`**：檢查檔名、2 MB 上限與文字內容；UTF-8（含 BOM）以 `CodingErrorAction.REPORT` 嚴格解碼，失敗時不寫入草稿。
- **`PlaybackDecisions.kt`**：不依賴 Android UI 的句尾停止與清單選篇判斷，便於單元測試。

## 主要資料流

1. **單篇朗讀**：文章輸入或匯入 → `ArticleStore.draft` → Activity 發送 `START`（語速、模式、句子索引）→ Service 讀取草稿 → Reader 分句／分片段 → Android TTS → 回呼更新畫面及書籤。
2. **播放清單**：`SavedArticle.id` 清單 → `START_PLAYLIST` → Service 依文章 ID 從 ArticleStore 載入文字 → Reader 完成一篇後 Service 啟動下一篇；每個句子變動都更新 `PlaylistProgress(articleId, sentence, speed, mode)`。
3. **睡眠計時**：Service 設定主執行緒定時任務；到時若播放中，Reader 完成同一句剩餘的中英片段後停止並將續讀位置指向下一句（若文章已結束，指向清單下一篇）。暫停中到時則立即停止。
4. **分享／匯入**：分享文字直接取代草稿；匯入從檔案 URI 讀取後先顯示預覽，使用者確認才寫入草稿。兩者都不會自動寫入文章庫。

## 資料、生命週期與權限

所有文章與設定儲存在 App 私有的 `SharedPreferences`（名稱 `articles`）。儲存內容包括 `draft`、`saved`（JSON）、`playlist`（文章 ID JSON 陣列）、`bookmark_*`、`playlist_*`、`mode`、`speed`、`sentence_font_size`。資料不會上傳；`AndroidManifest.xml` 沒有 `INTERNET` 或儲存空間權限。

Manifest 宣告 `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_MEDIA_PLAYBACK` 供背景朗讀，以及 `POST_NOTIFICATIONS` 供 Android 13+ 顯示通知；另以 `queries` 查詢 TTS 服務。檔案選取由系統選擇器授予單一 URI 的暫時讀取權限。Android 系統若終止整個行程，前景朗讀與當次計時會結束；下次可從已保存的書籤繼續。

## 目錄對照

```text
app/src/main/java/com/example/nianchulai/  # Activity、Service、TTS、分段、匯入與資料儲存
app/src/main/res/                     # 大聲公自適應圖示、通知圖示、色彩
app/src/test/java/com/example/nianchulai/  # 純邏輯單元測試
docs/                                 # 繁體中文文件
```
