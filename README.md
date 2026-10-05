# AITuber

Universal AI Character Skin for ChatGPT, Gemini, Claude, Grok and more.

AITuber 是一個 Android App：在你已經在用的 AI 語音聊天 App（ChatGPT、Gemini、Claude、Grok……）上面，
放一個 Live2D 角色當「皮膚」。AI 在想、在說話時，角色跟著動嘴、換狀態；角色可以從 App 裡的角色商城
一鍵下載。

> 狀態：alpha（`0.1.0-alpha2`）。Kotlin + C++/JNI，Live2D Cubism Native Framework 5-r.5，minSdk 29。

## 功能

### 角色疊在 AI App 上（主要用法）
- 浮動角色視窗蓋在 AI App 上方，AI 說話時角色動嘴。
- 支援 ChatGPT、Gemini、Claude、Grok、Copilot、Perplexity（`AiTargetApps`）。
- 用的是各 AI App 自己的帳號／訂閱，AITuber 不碰對話內容。
- 偵測來源（不需要無障礙服務）：
  - **Audio Playback Capture**：直接聽 AI App 自己播放的媒體聲音（朗讀、以媒體方式播放的語音模式）。
  - **Visualizer（整體輸出）**：通話式語音模式（例如 ChatGPT）無法被擷取時使用，只在「語音對話進行中」才採用，
    避免跟著音樂動嘴。
  - **語音對話判斷**（`VoiceSessionDetector`）：通話音訊、助理音訊、通訊模式，或「另一個 App 開著麥克風＋有聲音在播」
    （Claude 免手持模式可插話，所以麥克風一直開著；按住說話模式放開後 60 秒內仍算）。
    AITuber 自己的擷取與「Hey Google」常駐收音不算。

### 角色商城：按「下載」就能用
- 下載創作者授權的免費 Live2D 模型，**在手機上**自動轉成 AITuber 的統一格式，完成後直接進角色頁。
  不需要另存、轉檔或手動匯入。
- 轉換器處理：Shift_JIS／GBK 檔名、路徑大小寫、多個模型、待機動作命名、motion／physics 的 Meta 數量、
  Cubism JSON 解析器不接受的數字寫法、參數 ID 自動對應（VTube Studio 設定 → 群組 → 模型內 ID → 預設值）。
- 會清楚拒絕：RAR／7z、Cubism 2、只有編輯檔（.cmo3）、加密 .lpk、超出版本的 moc3、超大貼圖。
- 不轉散布：創作者大多禁止再散布，所以 AITuber 不代管模型，每位使用者從原作者處下載後在自己手機上轉換。
- Live2D 官方範例：8 個從 Live2D 官方 GitHub 直接下載（固定 commit）、3 個內建、4 個授權確認中、
  其餘在 App 內開官方頁面自動挑選。
- 詳見 [docs/CHARACTER_PACKAGE_CONVERTER.md](docs/CHARACTER_PACKAGE_CONVERTER.md)。

### 和角色聊天（Claude）
- 首頁「💬 和角色聊天（Claude）」：AITuber 直接和 Claude 對話，角色思考、把回覆念出來、嘴型跟著聲音動。
- 打字或 🎤 語音輸入；模型可選 Haiku 4.5／Sonnet 5.5／Opus 5.5。
- **需要使用者自己的 Claude API key**（Anthropic Console 建立，按用量計費）。Claude Pro／Max 訂閱不含 API；
  只想用訂閱的話，請用上面的「疊在 Claude App 上」。Key 以 Android Keystore 加密存在手機，只送往
  api.anthropic.com。**請勿把任何 API key 寫進程式碼或 APK。**
- 詳見 [docs/CLAUDE_CHAT.md](docs/CLAUDE_CHAT.md)。

### Claude Code mod：AITuber Buddy
- `tools/claude-code-mod/aituber-buddy/`：讓一個像素小角色在 Claude Code 旁邊陪你工作，
  跟著 Claude 思考／說話／執行工具／完成／出錯換表情。可用 Claude 訂閱登入的 Claude Code。
- `claude --plugin-dir tools/claude-code-mod/aituber-buddy`，詳見該資料夾的 README。

## 建置

需求：Android Studio（或 Android SDK 35 + JDK 17）、Android 10 以上的 arm64 手機。

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

### Live2D Cubism SDK（不在本 repo）
依 Live2D 授權，SDK、原生函式庫與官方範例模型**不放進這個公開 repo**（見 `.gitignore`）。

1. 自行從 Live2D 官網取得 Cubism SDK for Native（5-r.5）。
2. 在 `local.properties` 設定：
   ```properties
   live2d.sdk.dir=/path/to/CubismSdkForNative-5-r.5
   live2d.test.model.dir=/path/to/a/model/folder
   ```
3. 有 SDK 時會從 `app/src/main/cpp/` 編出 `libaituber_live2d.so`。
   沒有 SDK 時會使用本機 `app/src/main/jniLibs/arm64-v8a/libaituber_live2d.so`（不納入版控）。
   **舊的預編譯 .so 沒有下載角色需要的 `nativeInitializeV2`，請用目前的原始碼重新編譯**，
   否則下載的角色會被隱藏、商城會拒絕安裝。

詳見 [docs/live2d-setup.md](docs/live2d-setup.md)。

## 專案結構

```
app/src/main/java/com/aituber/poc/
  aiadapter/        Playback Capture 等偵測來源
  poc/              擷取服務、Visualizer、狀態判斷（UniversalStateResolver、VoiceSessionDetector）
  state/            UniversalStateSnapshot、MouthSignal 等共用狀態
  character/        角色引擎、眨眼、呼吸；live2d/ 原生橋接；live2d/install/ 角色包轉換器
  overlay/          浮動角色服務、嘴型對應
  claudechat/       和角色聊天（Claude API、TTS、語音輸入）
  ui/               首頁、角色商城、下載頁
app/src/main/cpp/   Live2D 原生端（JNI）
docs/               架構與各功能說明
tools/              claude-code-mod、layer_qa
experiments/        see_through_colab（圖片拆層實驗）
```

架構與依賴方向：`AI Adapter → Universal State → Character Engine → Character Adapter`，
見 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 授權與素材
- 角色模型屬於各原作者，依各自授權使用；AITuber 只提供下載入口與手機端轉換，不轉散布。
- Live2D Cubism SDK 與官方範例模型依 Live2D 的授權條款，需自行取得。
