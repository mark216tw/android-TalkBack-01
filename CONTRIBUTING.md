# 貢獻指南

歡迎透過 GitHub Issue 回報問題，或提交 Pull Request 改善「mini說書」。請以繁體中文撰寫使用者文件與介面文字，程式碼沿用專案既有的 Kotlin 命名方式。

## 建議流程

1. 先閱讀 [系統架構](docs/ARCHITECTURE.md)與[系統設計](docs/SYSTEM_DESIGN.md)，確認變更涉及的畫面、朗讀服務與資料保存。
2. 修改程式時，保留中文／中英文模式、離線語音選擇及現有文章進度的相容性。涉及文字分段或轉換時，加入有意義的單元測試。
3. 在專案根目錄執行 Windows `.\gradlew.bat testDebugUnitTest assembleDebug`，或 macOS／Linux `./gradlew testDebugUnitTest assembleDebug`。若變更背景播放、通知、檔案選擇器或 TTS 語音，請另在 Android 裝置或模擬器驗證。
4. 更新受影響的文件，並在 Pull Request 說明使用情境、主要修改與測試結果。

請勿提交 `local.properties`、私人金鑰、憑證、個人文章或建置產物。專案授權條款見 [LICENSE](LICENSE)。
