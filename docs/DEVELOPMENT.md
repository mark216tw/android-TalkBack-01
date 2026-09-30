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
| `PlaybackDecisionsTest.kt` | 句尾計時停止、跳過空白／已刪文章 |
| `TextFileImportTest.kt` | UTF-8／BOM、非法編碼及 `.txt` 檔名判斷 |

若只需單元測試：Windows `.\gradlew.bat testDebugUnitTest`；macOS／Linux `./gradlew testDebugUnitTest`。若需建置：`assembleDebug`。單元測試不會驗證手機語音引擎的實際發音或系統檔案選擇器，這些需實機檢查。

## 建議實機檢查

1. 安裝中文與英文**離線**語音，檢查中文／中英文模式的選擇、數字及縮寫發音。
2. 使用其他 App 分享純文字；以系統選擇器測試 UTF-8、含 BOM、非 UTF-8、空白與超過 2 MB 的 `.txt`。
3. 在逐句頁切換字體大小，檢查長句換行、四周留白、捲動跟隨與「回到目前句」。
4. 切到其他 App、鎖定螢幕與使用通知播放控制；確認播放清單可跨文章接續及保存文章／句子位置。
5. 計時器到期時確認完整唸完目前句子；檢查暫停中到期與取消計時；Android 13+ 再驗證通知權限狀態。

提交程式前請同步更新受影響的文件。貢獻方式見[貢獻指南](../CONTRIBUTING.md)。
