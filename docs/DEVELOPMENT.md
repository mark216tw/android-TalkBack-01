# 開發與測試

## 環境與取得原始碼

- JDK 17、Android Studio（或已安裝 Android SDK 與命令列工具）。
- Android SDK Platform 35；測試裝置 Android 8.0（API 26）以上。
- 專案已附 Gradle Wrapper 8.9；不需要全域安裝 Gradle。Gradle 第一次建置可能需要下載外部程式庫；這不代表 App 需要網路權限。

```powershell
git clone https://github.com/mark216tw/android-TalkBack-01.git
cd android-TalkBack-01
.\gradlew.bat testDebugUnitTest assembleDebug
```

macOS／Linux 以 `./gradlew testDebugUnitTest assembleDebug` 取代最後一行；Android Studio 可直接開啟根目錄並執行 `app`。Debug APK 位於 `app/build/outputs/apk/debug/app-debug.apk`。Android SDK 路徑由本機環境變數或 `local.properties` 提供，該檔已排除於版本控制。

## 專案結構

```text
app/build.gradle.kts                          # Android 版本、Compose 與依賴
app/src/main/AndroidManifest.xml             # 活動、服務、語音查詢與必要權限
app/src/main/java/com/example/nianchulai/    # 主畫面、朗讀、分段、匯入、文章與播放清單
app/src/main/res/                             # 圖示與顏色
app/src/test/java/com/example/nianchulai/    # JUnit 單元測試
docs/                                         # 使用、架構、設計、開發與隱私文件
```

| 測試檔 | 驗證重點 |
| --- | --- |
| `ReadingSegmentsTest.kt` | 中英分段、數字語境、句子位置、長文片段上限 |
| `SpeechPreparationTest.kt` | 日期、金額、百分比、英文縮寫的朗讀文字轉換 |
| `PlaybackDecisionsTest.kt` | 句尾計時停止、跳過空白／已刪文章、指定清單起播及重排定位、播放 ID 辨識、切換語言後保留目前句起點 |
| `TextFileImportTest.kt` | UTF-8／BOM、非法編碼及 `.txt` 檔名判斷 |

若只需單元測試：Windows `.\gradlew.bat testDebugUnitTest`；macOS／Linux `./gradlew testDebugUnitTest`。若需建置：`assembleDebug`。單元測試不會驗證手機語音引擎的實際發音或系統檔案選擇器，這些需實機檢查。

## 建議實機檢查

1. 安裝中文與英文**離線**語音，檢查中文／中英文模式的選擇、數字及縮寫發音。
2. 使用其他 App 分享純文字；以系統選擇器測試 UTF-8、含 BOM、非 UTF-8、空白與超過 2 MB 的 `.txt`。
3. 從朗讀頁齒輪進入設定切換逐句字體大小，再到逐句頁檢查長句換行、四周留白；手動捲動後，確認自動讀到下一句及按「下一句」均恢復跟隨。
4. 切到其他 App、鎖定螢幕與使用通知播放控制；確認播放清單可跨文章接續及保存文章／句子位置。
5. 計時器到期時確認完整唸完目前句子；檢查剩餘時間同列的「取消」、等待句尾時取消，以及暫停中到期；Android 13+ 再驗證通知權限狀態。
6. 設定頁切換系統／淺色／深色及兩列共六個主題色、拖曳 Hue 滑桿，確認色塊與選項整合、色彩辨識度、即時生效與重開後保留；檢查狀態欄、手勢與三鍵系統導覽列，以及返回主畫面。
7. 新文章標題預設值、空白禁存與取消；文章庫單篇起播及跳轉逐句頁；播放清單第一篇／中間文章起播及後續接續。底部朗讀圖示只切頁；逐句圖示在其他頁只切頁，在逐句頁可開始／暫停／繼續。頁內朗讀按鈕啟播並跳頁，播放中保留進度、暫停中續讀。確認 400 dp 文章欄位及同列左側儲存、右側朗讀，移除單篇續讀入口與清單固定說明。

提交程式前請同步更新受影響的文件。貢獻方式見[貢獻指南](../CONTRIBUTING.md)。

文章庫新增／TXT 匯入／庫內編輯需檢查標題、內容空白禁存、取消不建立或修改文章、不影響正在朗讀的草稿；確認同列靠右圖示、未加入文章的加入清單圖示及刪除確認。使用兩篇相同內文文章確認只有正確 ID 的播放按鈕切換為暫停／繼續，可操作且留在列表；測試清單換篇、停止後圖示恢復及第一篇上方的續聽。確認標題為 20 sp 且無播放中圖示，播放清單無加入文章區塊。

逐句頁確認語言／語速列預設隱藏，右上角 ⋮ 可展開與收起、不保留空白，無障礙描述對應顯示／隱藏狀態。播放中與暫停中展開並調整語言／語速，確認下一片段套用速度、目前句重唸、暫停狀態不變、後續清單使用新設定、計時器持續；缺少英文離線語音時原設定及進度不變。另檢查桌面、分享選單、系統 App 名稱及通知均為「mini說書人」，朗讀頁無語言、語速、TXT 匯入及狀態文字。

檢查 40 dp 圓形、20 dp 圖示的實心／描邊與停用樣式、至少 48 dp 點擊範圍。逐句頁測試語速下拉全部選項、已選標示及選取後套用；窄螢幕與放大字型下確認語言／語速及四個播放控制仍為單列，順序為上一句、下一句、播放切換、停止，停止未播放時停用。
