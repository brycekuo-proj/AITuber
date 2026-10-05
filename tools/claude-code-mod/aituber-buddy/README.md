# AITuber Buddy（Claude Code mod）

在 Claude Code 旁邊放一個 AITuber 小角色，跟著 Claude 的狀態換表情：

| Claude 在做什麼 | 角色 |
|---|---|
| 收到指令、在想 | 思考（對話框：嗯…讓我想想…） |
| 正在寫回覆 | 說話，嘴巴一開一合，對話框顯示最新幾句 |
| 執行工具（Bash、改檔、搜尋…） | 做事，冒汗，對話框寫正在做什麼 |
| 工具失敗 / 出錯 | 擔心 |
| 回覆完成 | 開心，6 秒後回到待機 |

終端機用色塊畫出角色（任何支援 truecolor 的終端機都行），桌面版 / VS Code 用 SVG。
狀態列也會顯示一個小表情，面板沒打開時也看得到。

## 使用

```bash
claude --plugin-dir "/Users/user/Bryce AI Studio/AITuber/source/tools/claude-code-mod/aituber-buddy"
```

或在 `~/.claude/settings.json` 的 `env` 加上
`"CLAUDE_CODE_PLUGIN_DIRS": "/Users/user/Bryce AI Studio/AITuber/source/tools/claude-code-mod/aituber-buddy"`，
桌面版 Claude Code 也會載入。

- 終端機寬度 144 欄以上會自動在旁邊打開面板；較窄時輸入 `/aituber` 打開。
- 檢查：`claude plugin validate <資料夾>`；測試：`claude plugin test <資料夾>`。

需要 Claude Code 2.1.289 以上（function hooks 仍是 early access，API 之後可能變動）。
