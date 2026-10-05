# 和角色聊天（Claude）

首頁的「💬 和角色聊天（Claude）」打開 `ClaudeChatActivity`：AITuber 自己直接和 Claude 對話，
不需要開其他 AI App。角色會思考、把回覆念出來，嘴型跟著聲音動。

Code: `app/src/main/java/com/aituber/poc/claudechat/`

| 檔案 | 做什麼 |
|---|---|
| `ClaudeApiClient` | 串流呼叫 Messages API（`POST https://api.anthropic.com/v1/messages`，`anthropic-version: 2023-06-01`） |
| `ClaudeSseParser` | 讀 SSE：`content_block_delta/text_delta`、`message_delta.stop_reason`、`message_stop`、`error` |
| `ClaudeConversation` | 對話紀錄（最多 24 則）、角色 system prompt、request JSON |
| `SentenceChunker` | 把串流文字切成句子，第一句寫完就開始念；去掉 Markdown 符號 |
| `ClaudeSpeaker` | 手機內建 TTS；在自己的 audio session 上掛 Visualizer 量音量給嘴型 |
| `ClaudeChatSession` | 串起來：THINKING → SPEAKING → IDLE，並把狀態送進 `CaptureSessionState` |
| `ClaudeChatSettings` | API key（Android Keystore AES-GCM 加密）、模型、是否朗讀 |
| `ClaudeChatActivity` | 畫面：角色、對話、打字／🎤 語音輸入 |

- **API key**：使用者自己的 key（console.anthropic.com → API Keys），費用算在他的 Anthropic 帳戶。
  Key 加密存在手機，只送到 api.anthropic.com。**不要把任何 key 寫進 APK。**
- **模型**：Haiku 4.5（預設，最快開口）、Sonnet 5.5、Opus 5.5。
- **角色**：聊天畫面自己載入首頁選的角色。浮動角色開著時改由浮動角色說話
  （同時只能有一個 Live2D 畫面）；聊天畫面在前景時，`CaptureSessionState.updateDirectChat`
  覆蓋偵測結果，退到背景就交還給 AI App 偵測。
- **嘴型**：Visualizer 聽得到 TTS 的聲音時照音量；聽不到（沒有錄音權限、TTS 引擎不用我們的
  audio session）就用通用的說話節奏。
- **語音輸入**：系統的 `RecognizerIntent`（一次說一句）。

未在實機測過：TTS 引擎是否接受 `KEY_PARAM_SESSION_ID`（不接受時會自動改用通用節奏）、
各品牌手機的語音輸入畫面。
