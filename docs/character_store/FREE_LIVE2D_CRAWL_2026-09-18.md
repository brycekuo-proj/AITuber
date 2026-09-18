# AITuber 免費 Live2D 角色商城：第一輪爬蟲與一鍵安裝分析
更新：2026-09-18

## 結論先行

目前商城方向可行，但「下載後不改 APK、直接出現在首頁角色輪播」需要先做 **一次性的 Generic External Live2D Loader**。

目前 AITuber Native Runtime 主要以 Android AssetManager / APK assets 為來源；現有 Haru / Loaf Dog / Tororo / Hijiki 也是程式內 profile。這代表現在從網路下載到 App 私有儲存空間的模型，Native Runtime 尚不能直接載入。

一次改完後，後續角色不需要再為每個模型重新編譯 APK：

商城下載 -> App private storage -> 解壓 -> 掃描 model3.json -> 驗證 moc3 / textures -> 自動 Parameter mapping -> 建立 local profile -> 自動加入首頁角色輪播。

## 來源平台的一鍵下載限制

### GitHub
可做到最接近真正的一鍵安裝。公開 Repository / Release 可使用穩定 URL 下載 ZIP，不需要帳號。最適合 AITuber。

### BOOTH
BOOTH 免費數位商品雖然顯示「免費下載」，實測其 downloadables URL 在未登入時會轉到 BOOTH 登入頁。

因此 BOOTH 不能假設為匿名直連下載。建議：
- 商城卡片仍只有一個「下載」按鈕。
- App 內部打開 BOOTH 下載 URL。
- 第一次若尚未登入，讓使用者在來源頁登入。
- 保存該來源的 Web session；之後同來源可接近一鍵。
- 下載完成後由 App 接管檔案、解壓、驗證、加入角色輪播。

不要在 AITuber 自己收集 BOOTH 密碼，也不要把 BOOTH 模型轉存到自己的伺服器。

### itch.io
Name-your-own-price 免費商品會先進 purchase / download gate，通常還有「No thanks, just take me to the downloads」。因此屬於半自動來源；可由 App 內 browser 接住最終檔案，但不應假設永遠存在匿名單步直連。

## 第一輪候選

| ID | 角色 | 平台 | 免費檔案 | Runtime-ready | 商城判定 | 備註 |
|---|---|---|---|---|---|---|
| scarlett | Scarlett | BOOTH | Scarlett_vts.zip / 2.49 MB | 高 | B | VTube Studio ready；條款允許商用內容，但完整 TOS 仍建議人工覆核 |
| goth_lolita | ダウナーなゴスロリ少女 | BOOTH | gosurori_vts.zip / 1.2 MB | **高** | **A-候選** | 明確含 moc3 + model3.json + physics3.json + texture；禁止再配布，適合 link-only |
| office_girl | オフィスの女の子 | BOOTH | office_f_vts.zip / 11.7 MB | **高** | **A-候選** | moc3 已由作者說明；動作/表情多；商用可 |
| generic_boy01 | generic_boy01 | BOOTH | generic_boy01.zip / 3.19 MB | 高 | **A-候選** | App/game/VTuber 使用許可；需標作者；禁止再配布 |
| sumire | sumire free | BOOTH | live2d_sumire_free.zip / 17.4 MB | 高 | C-覆核 | 商用影片/配信可，但禁止再配布、改變，且有 AI 學習/生成限制；AITuber 使用情境需先取得作者確認較穩 |
| alexia | Alexia | BOOTH | Alexia.zip / 25.9 MB | **高** | B | 頁面明確說 moc3 / ready-to-use；TOS 另有外部文件，正式上架前覆核 |
| ice_girl | Ice Girl | BOOTH | IceGirl_Live2d.rar / 17.9 MB | 中 | C | **RAR**，Android 需額外 RAR extractor；且 BOOTH 每帳號限制 1 件 |
| gloria | Gloria | BOOTH | GLORIA.zip / 16.3 MB | 高 | B | 商用允許；不允許二次散布；link-only 方向吻合 |
| small_cat | ちいさなねこ FREE | BOOTH | ちいさなねこ無料版.zip / 4.0 MB | **高** | C-覆核 | 有臉 XYZ、眨眼、口、呼吸、尾巴；但禁止 AI 學習/生成AI輸入，AITuber 情境需先確認 |
| snow_bear_girl | 雪熊少女 | BOOTH | 雪熊企划_雪熊少女.zip / 15.4 MB | 高 | B | 5 表情 + 5 toggle；個人/企業可但單使用者授權；禁止轉讓/轉售 |
| silver_hair_4_costumes | Silver-haired girl 4 costume | BOOTH | ulvm2_0001.zip / 24.3 MB | 高 | B | 4 套服裝 + 16 表情；正式商城前再覆核完整 NG 條款 |
| mayoi | Mayoi | BOOTH | Mayoi.zip / 11.6 MB | 高 | B | 免費；明確禁止二次銷售/免費分享；link-only 才適合 |
| arch_chan | Arch Chan | GitHub | repository ZIP | 中～高 | **A-候選** | CC0-1.0；Live2D 目錄包含可供 VTube Studio / PrprLive 使用的 moc3；最適合測真正一鍵 |
| garnet | Garnet | itch.io | GarnetGal.cmo3 + PSD | **低** | D | CC0 很乾淨，但下載頁目前列的是 cmo3 + PSD，沒有 runtime moc3/model3 package；無法直接一鍵安裝 |

## 分級

- **A-候選**：適合優先做 AITuber 真機一鍵安裝驗證。
- **B**：技術上可裝，但條款/來源流程需要額外確認。
- **C / C-覆核**：技術或授權有明顯阻力，先不要放正式商城。
- **D**：不是可直接 Runtime 使用的角色包。

## Preview / 頭像

第一輪預覽圖已抓到本機，僅作商城 UI / 內部研究：
`docs/character_store/research_previews/`

目前包含：
- scarlett.jpg
- goth_lolita.jpg
- office_girl.jpg
- generic_boy01.jpg
- sumire.jpg
- alexia.jpg
- ice_girl.jpg
- gloria.jpg
- small_cat.jpg
- snow_bear_girl.jpg
- silver_hair_4_costumes.jpg
- mayoi.jpg
- garnet.jpg（來源實際為 itch.io GIF，副檔名需後續正規化）
- arch_chan.jpg

正式發佈商城時，**不要直接假設可以把作者宣傳圖重新打包進 APK**。比較安全的模式是：
1. 取得作者明確允許商城展示圖；或
2. 商城 manifest 保存來源端公開 preview URL，遠端顯示；或
3. 作者提供專用 thumbnail。

## Generic External Live2D Loader：一次性必要改造

### 1. 下載層
- sourceType: github_direct / booth_web / itch_web
- ZIP 優先
- RAR 第一版直接標示不支援
- 最大下載大小限制
- HTTPS only

### 2. 安全解壓
- 只允許 App private storage
- 阻擋 ../ path traversal
- 解壓大小 / 檔案數上限
- 不執行任何下載內容

### 3. 模型自動偵測
解壓後遞迴搜尋：
- exactly one preferred `*.model3.json`
- 該 JSON 引用的 `*.moc3`
- texture PNG/WebP
- optional physics3 / pose3 / motion3 / exp3

### 4. Profile 自動產生
優先從 model3.json Groups 使用 LipSync / EyeBlink IDs。
其次比對常見 Parameter：
- ParamMouthOpenY
- ParamMouthOpen
- PARAM_MOUTH_OPEN_Y
- ParamEyeLOpen / ParamEyeROpen
- PARAM_EYE_L_OPEN / PARAM_EYE_R_OPEN
- ParamBreath / PARAM_BREATH
- ParamAngleX / ParamAngleY / ParamBodyAngleX

找不到嘴參數時，不要讓安裝失敗：角色仍能顯示，但標記 lipSync capability=false。

### 5. Runtime 必改一次
目前 C++ loader 走 AssetManager。需抽象成：
- APK asset source
- filesystem source

下載角色放：
`context.filesDir/characters/<characterId>/`

之後 model3.json 裡所有相對路徑都從該角色根目錄解析。

### 6. 首頁整合
安裝成功後寫入 local character index。Broadway 首頁 carousel 每次讀：
- 內建角色
- local installed characters

下載成功後直接刷新 carousel，不需要重啟 App、不需要重新 build APK。

## 建議第一批真機驗證順序

1. Arch Chan：GitHub + CC0，驗證真正 direct download。
2. Gothic Lolita：超小 1.2 MB ZIP + 完整 runtime 檔，驗證 BOOTH authenticated flow。
3. generic_boy01：3.19 MB，驗證另一作者的標準 VTS ZIP。
4. Office Girl：11.7 MB + motion/expressions 較多，驗證複雜角色包。
5. Alexia：25.9 MB，驗證較大 package。

這五個都通過後，再擴大爬蟲與商城上架名單。
