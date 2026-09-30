# mini說書（Android）

「mini說書」是免費、以離線使用為優先的 Android 文章朗讀 App。可輸入、貼上、從其他 App 分享文字，或在文章庫匯入 UTF-8 `.txt`；使用裝置的離線文字轉語音（TTS）引擎，以中文或中英文混讀文章。

## 主要功能

- **朗讀**：中文／中英文模式、0.5～1.5 倍語速、播放／暫停／繼續、背景通知控制，以及 15／30／60 分鐘睡眠計時器（唸完目前句子後停止）。
- **逐句**：點選句子跳讀、上一句／下一句、目前句標示；上方精簡語言／語速設定即時套用，手動捲動後在下一句恢復跟隨。字型大小在設定頁調整。
- **文章庫**：新增、TXT 匯入、搜尋、庫內編輯、加入清單與刪除確認；小型圓形操作同列靠右，播放按鈕依狀態可開始／暫停／繼續，文章標題放大。
- **播放清單**：選擇多篇已儲存文章、調整順序、連續朗讀及從上次的文章與句子續聽。
- **匯入與分享**：接收其他 App 分享的純文字；在文章庫透過系統檔案選擇器匯入 UTF-8（含 BOM）`.txt`，輸入標題後建立新文章。

文章與進度儲存在裝置上，App 不要求網路權限，也不使用雲端語音 API。實際可用語音與發音品質取決於手機安裝的 TTS 引擎及其**離線**語音資料；首次安裝語音資料可能需要由系統下載。

## 快速開始

1. 準備 **JDK 17**、Android SDK 35，以及 Android 8.0（API 26）以上的裝置或模擬器。
2. 以 Android Studio 開啟本專案，等待 Gradle 同步並執行 `app`；或在根目錄執行：

   ```powershell
   .\gradlew.bat assembleDebug
   ```

   macOS／Linux 使用 `./gradlew assembleDebug`。Debug APK 輸出至 `app/build/outputs/apk/debug/app-debug.apk`。
3. 在手機的系統文字轉語音設定安裝**中文離線語音**；使用中英文模式時再安裝**英文離線語音**。開啟 App，貼上文章並按「朗讀」。

測試指令：Windows `.\gradlew.bat testDebugUnitTest assembleDebug`；macOS／Linux `./gradlew testDebugUnitTest assembleDebug`。完整操作及開發說明見下方文件。

## 文件導覽

| 文件 | 內容 |
| --- | --- |
| [文件索引](docs/README.md) | 文件清單與閱讀順序 |
| [使用指南](docs/USER_GUIDE.md) | 各分頁、匯入、分享、播放清單及計時器 |
| [系統架構與技術文件](docs/ARCHITECTURE.md) | 元件、資料流、技術堆疊與資料保存 |
| [系統設計文件](docs/SYSTEM_DESIGN.md) | 需求、狀態轉移、例外及驗收案例 |
| [開發與測試](docs/DEVELOPMENT.md) | 環境、建置、測試與專案結構 |
| [隱私與資料](docs/PRIVACY.md) | 本機資料、權限與清除方式 |
| [貢獻指南](CONTRIBUTING.md) | 提交與測試慣例 |

## 授權

本專案以 [MIT License](LICENSE) 授權，著作權所有人為 **mark216tw**。`LICENSE` 保留 MIT 授權條款的標準英文原文；本 README 與 `docs/` 為繁體中文說明。
