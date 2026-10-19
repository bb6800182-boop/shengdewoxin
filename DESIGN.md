---
version: alpha
name: 聲得我心-石板灰
description: "聲得我心（Android 語音助理）的設計系統。以 ElevenLabs（語音 AI 品牌）的版面語言為基底：安靜的單色介面、近黑膠囊主按鈕、少量重點色；配色改成冷調石板灰，重點色是 App 圖示上的青綠聲波。淺色、深色兩套，名稱一一對應（深色加 -dark）。中性、不花俏，適合隨手叫出來用。"

colors:
  # ── 淺色 ──
  primary: "#0F172A"
  on-primary: "#FFFFFF"
  accent: "#1E293B"
  on-accent: "#5EEAD4"
  accent-text: "#0F766E"
  canvas: "#F1F5F9"
  surface-1: "#FFFFFF"
  surface-2: "#F1F5F9"
  hairline: "#E2E8F0"
  hairline-strong: "#CBD5E1"
  ink: "#0F172A"
  ink-muted: "#334155"
  ink-subtle: "#475569"
  success: "#15803D"
  scrim: "#000000"
  # ── 深色 ──
  primary-dark: "#F8FAFC"
  on-primary-dark: "#0F172A"
  accent-dark: "#2DD4BF"
  on-accent-dark: "#0F172A"
  accent-text-dark: "#5EEAD4"
  canvas-dark: "#020617"
  surface-1-dark: "#0F172A"
  surface-2-dark: "#1E293B"
  hairline-dark: "#1E293B"
  hairline-strong-dark: "#334155"
  ink-dark: "#F8FAFC"
  ink-muted-dark: "#CBD5E1"
  ink-subtle-dark: "#94A3B8"
  success-dark: "#4ADE80"
  # ── App 圖示與小工具（淺色、深色共用）──
  icon-start: "#475569"
  icon-end: "#1E293B"
  icon-ring: "#FFFFFF"
  icon-wave: "#5EEAD4"

typography:
  title:
    fontFamily: "Roboto, Noto Sans TC, sans-serif"
    fontSize: 28px
    fontWeight: 600
    lineHeight: 1.25
  heard:
    fontFamily: "Roboto, Noto Sans TC, sans-serif"
    fontSize: 22px
    fontWeight: 400
    lineHeight: 1.4
  reply:
    fontFamily: "Roboto, Noto Sans TC, sans-serif"
    fontSize: 18px
    fontWeight: 400
    lineHeight: 1.5
  item-title:
    fontFamily: "Roboto, Noto Sans TC, sans-serif"
    fontSize: 16px
    fontWeight: 600
    lineHeight: 1.4
  body:
    fontFamily: "Roboto, Noto Sans TC, sans-serif"
    fontSize: 15px
    fontWeight: 400
    lineHeight: 1.5
  body-sm:
    fontFamily: "Roboto, Noto Sans TC, sans-serif"
    fontSize: 14px
    fontWeight: 400
    lineHeight: 1.5
  caption:
    fontFamily: "Roboto, Noto Sans TC, sans-serif"
    fontSize: 13px
    fontWeight: 400
    lineHeight: 1.4
  button:
    fontFamily: "Roboto, Noto Sans TC, sans-serif"
    fontSize: 15px
    fontWeight: 500
    lineHeight: 1.2
  input:
    fontFamily: "Roboto, Noto Sans TC, sans-serif"
    fontSize: 16px
    fontWeight: 400
    lineHeight: 1.25

rounded:
  md: 12px
  lg: 16px
  xl: 24px
  pill: 9999px
  full: 9999px

spacing:
  xxs: 4px
  xs: 8px
  sm: 12px
  md: 16px
  lg: 20px
  xl: 24px
  xxl: 32px

components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.button}"
    rounded: "{rounded.pill}"
    padding: 0 20px
    height: 48px
  button-primary-dark:
    backgroundColor: "{colors.primary-dark}"
    textColor: "{colors.on-primary-dark}"
    typography: "{typography.button}"
    rounded: "{rounded.pill}"
    padding: 0 20px
    height: 48px
  button-secondary:
    backgroundColor: "{colors.surface-2}"
    textColor: "{colors.ink}"
    typography: "{typography.button}"
    rounded: "{rounded.pill}"
    padding: 0 20px
    height: 48px
  button-secondary-dark:
    backgroundColor: "{colors.surface-2-dark}"
    textColor: "{colors.ink-dark}"
    typography: "{typography.button}"
    rounded: "{rounded.pill}"
    padding: 0 20px
    height: 48px
  card:
    backgroundColor: "{colors.surface-1}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.lg}"
    padding: 16px 20px
  card-dark:
    backgroundColor: "{colors.surface-1-dark}"
    textColor: "{colors.ink-dark}"
    typography: "{typography.body}"
    rounded: "{rounded.lg}"
    padding: 16px 20px
  card-note:
    backgroundColor: "{colors.surface-1}"
    textColor: "{colors.ink-subtle}"
    typography: "{typography.caption}"
  card-note-dark:
    backgroundColor: "{colors.surface-1-dark}"
    textColor: "{colors.ink-subtle-dark}"
    typography: "{typography.caption}"
  text-input:
    backgroundColor: "{colors.surface-2}"
    textColor: "{colors.ink}"
    typography: "{typography.input}"
    rounded: "{rounded.md}"
    padding: 10px 12px
    height: 48px
  text-input-dark:
    backgroundColor: "{colors.surface-2-dark}"
    textColor: "{colors.ink-dark}"
    typography: "{typography.input}"
    rounded: "{rounded.md}"
    padding: 10px 12px
    height: 48px
  routine-row:
    backgroundColor: "{colors.surface-2}"
    textColor: "{colors.ink}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.md}"
    padding: 12px 16px
  routine-row-dark:
    backgroundColor: "{colors.surface-2-dark}"
    textColor: "{colors.ink-dark}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.md}"
    padding: 12px 16px
  list-row:
    backgroundColor: "{colors.surface-1}"
    textColor: "{colors.ink}"
    typography: "{typography.item-title}"
    height: 56px
  list-row-dark:
    backgroundColor: "{colors.surface-1-dark}"
    textColor: "{colors.ink-dark}"
    typography: "{typography.item-title}"
    height: 56px
  fold-row:
    backgroundColor: "{colors.surface-1}"
    textColor: "{colors.ink}"
    typography: "{typography.item-title}"
    height: 48px
  fold-row-dark:
    backgroundColor: "{colors.surface-1-dark}"
    textColor: "{colors.ink-dark}"
    typography: "{typography.item-title}"
    height: 48px
  help-toggle:
    backgroundColor: "{colors.surface-1}"
    textColor: "{colors.ink-subtle}"
    typography: "{typography.button}"
    rounded: "{rounded.full}"
    size: 44px
  help-toggle-dark:
    backgroundColor: "{colors.surface-1-dark}"
    textColor: "{colors.ink-subtle-dark}"
    typography: "{typography.button}"
    rounded: "{rounded.full}"
    size: 44px
  assistant-sheet:
    backgroundColor: "{colors.surface-1}"
    textColor: "{colors.ink}"
    typography: "{typography.heard}"
    rounded: "{rounded.xl}"
    padding: 16px 24px 24px
  assistant-sheet-dark:
    backgroundColor: "{colors.surface-1-dark}"
    textColor: "{colors.ink-dark}"
    typography: "{typography.heard}"
    rounded: "{rounded.xl}"
    padding: 16px 24px 24px
  status-listening:
    backgroundColor: "{colors.surface-1}"
    textColor: "{colors.accent-text}"
    typography: "{typography.body-sm}"
  status-listening-dark:
    backgroundColor: "{colors.surface-1-dark}"
    textColor: "{colors.accent-text-dark}"
    typography: "{typography.body-sm}"
  status-success:
    backgroundColor: "{colors.surface-1}"
    textColor: "{colors.success}"
    typography: "{typography.body-sm}"
  status-success-dark:
    backgroundColor: "{colors.surface-1-dark}"
    textColor: "{colors.success-dark}"
    typography: "{typography.body-sm}"
  mic-button:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.on-accent}"
    rounded: "{rounded.full}"
    size: 64px
  mic-button-dark:
    backgroundColor: "{colors.accent-dark}"
    textColor: "{colors.on-accent-dark}"
    rounded: "{rounded.full}"
    size: 64px
  # 小工具分深色、淺色兩版給使用者看桌布選，不跟著系統切換，所以只用淺深共用的 App 圖示顏色
  widget-button:
    backgroundColor: "{colors.icon-end}"
    textColor: "{colors.icon-wave}"
    rounded: "{rounded.full}"
    size: 56px
  widget-button-light:
    backgroundColor: "{colors.icon-ring}"
    textColor: "{colors.icon-end}"
    rounded: "{rounded.full}"
    size: 56px
  page:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.ink}"
  page-dark:
    backgroundColor: "{colors.canvas-dark}"
    textColor: "{colors.ink-dark}"
  reply-text:
    backgroundColor: "{colors.surface-1}"
    textColor: "{colors.ink-muted}"
    typography: "{typography.reply}"
  reply-text-dark:
    backgroundColor: "{colors.surface-1-dark}"
    textColor: "{colors.ink-muted-dark}"
    typography: "{typography.reply}"
  card-border:
    backgroundColor: "{colors.hairline}"
  card-border-dark:
    backgroundColor: "{colors.hairline-dark}"
  input-border:
    backgroundColor: "{colors.hairline-strong}"
  input-border-dark:
    backgroundColor: "{colors.hairline-strong-dark}"
  assistant-scrim:
    backgroundColor: "{colors.scrim}"
  app-icon-ring:
    backgroundColor: "{colors.icon-end}"
    textColor: "{colors.icon-ring}"
  app-icon:
    backgroundColor: "{colors.icon-start}"
    textColor: "{colors.icon-wave}"
    rounded: "{rounded.full}"
---

## 總覽

聲得我心是叫出來說一句話就收起來的工具，介面要安靜、好認、不搶戲。基底取 ElevenLabs 的做法：大面積中性色、近黑的膠囊主按鈕、重點色只出現在少數地方；配色換成冷調石板灰（Tailwind slate 色階），重點色是 App 圖示上的青綠聲波。使用者要的是中性、不柔、不女性化的感覺，所以沒有粉紅、紫色，也不用心形圖案。

- 淺色、深色各一套，token 名稱一一對應；預設淺色，可在設定頁選「淺色／深色／跟隨系統」。
- 所有文字和底色維持 WCAG AA（一般文字 ≥ 4.5:1），按鈕和圖示圖形 ≥ 3:1；實際數字見「顏色」。

## 顏色

- **primary／on-primary**：主要按鈕（膠囊）。淺色是近黑底白字（17.9:1），深色反過來是近白底深字（17.1:1）。
- **accent／on-accent**：助理面板的麥克風按鈕。淺色用石板灰底＋青綠圖形（9.9:1），跟 App 圖示同一組；深色底上石板灰看不出來，改成青綠底＋深色圖形（9.6:1）。
- **小工具**：分深色、淺色兩版，讓使用者看桌布選，不跟著系統切換，所以只用 App 圖示那組淺深共用的顏色。深色版 icon-end 底＋icon-wave 麥克風（9.9:1），適合亮的桌布；淺色版 icon-ring 白底＋icon-end 麥克風（14.6:1），適合暗的桌布。
- **accent-text**：「請說…」這類狀態字。淺色用深青綠 #0F766E（白卡片上 5.5:1），深色用淺青綠 #5EEAD4（12.1:1）。
- **canvas → surface-1 → surface-2**：頁面底、卡片、輸入框與次要按鈕，三層由淺到深（深色由深到淺），靠 hairline 細框分層，不用陰影。
- **ink → ink-muted → ink-subtle**：主文字、次要文字、說明文字；最淡的 ink-subtle 在各層底色上都 ≥ 5.7:1。
- **success**：「✓ 完成」與已完成的步驟。
- **scrim**：助理面板後面的黑色遮罩，淺色 40%、深色 60%。
- 錯誤不另設紅色：用「⚠」符號加一般文字色，避免和重點色搶。

## 字體

用系統字（Roboto／Noto Sans TC），不另外打包字型。層級：設定頁標題 `title` 28、助理聽到的句子 `heard` 22、回覆 `reply` 18、項目標題 `item-title` 16（粗）、輸入框 `input` 16、內文 `body` 15、說明 `body-sm` 14、小字與卡片標題 `caption` 13、按鈕 `button` 15。字距用預設，行高 1.25 倍。

## 版面

- 4dp 為基本單位；頁面左右 16dp，卡片之間 16dp，卡片內距上下 16、左右 20。
- 按鈕與輸入框高度至少 48dp（好點、好按）。
- 主畫面分兩頁：「設定」（開始使用 → 個人化 → 情境 → AI（選用）→ YouTube 金鑰（選用）→ 權限（選用））和「說明」（叫出助理的方式 → 可以說什麼 → 常見問題 → 關於）。
- 畫面要簡潔：每個項目只留一行（標題＋狀態或按鈕＋「?」），說明收在「?」裡；選用的區塊和長內容預設收起，收起時在標題列右邊顯示摘要（例如「未設定」「已開 1/3」）。
- 助理面板從底部升起，佔畫面下半部，底部讓出導覽列；點遮罩關閉。

## 形狀

| token | 值 | 用在 |
|---|---|---|
| `{rounded.md}` | 12px | 輸入框、情境列表項目 |
| `{rounded.lg}` | 16px | 卡片 |
| `{rounded.xl}` | 24px | 助理面板上緣 |
| `{rounded.pill}` | 9999px | 所有按鈕 |
| `{rounded.full}` | 9999px | 麥克風按鈕、小工具 |

## 元件

- **button-primary**：每張卡片最多一顆，放最主要的動作（儲存、開始說話）。
- **button-secondary**：其他動作；外觀選項的「目前選擇」改用 primary 並加「✓」，不只靠顏色分辨。
- **分頁**：兩顆等寬按鈕，選中的用 button-primary、沒選的用 button-secondary；螢幕閱讀器會念「已選」。
- **list-row**：設定與說明的一列：標題（item-title）＋狀態或按鈕＋「?」。完成後按鈕換成「✓ 已完成」（success、caption）。可收合卡片（AI、權限）的標題列也是這個高度，右邊放摘要（card-note）和箭頭。
- **help-toggle**：44dp 圓形「?」，點了才展開說明（body-sm、ink-subtle）；螢幕閱讀器會念「已收合／已展開」。
- **fold-row**：卡片裡可收合的一列（範例、常見問題），點標題才展開內容（body-sm、ink-muted）；第二列起上方一條 hairline。
- **routine-row**：情境清單的一列：觸發語句＋一行摘要（回應、做幾件事），整列可點，點開就地展開編輯（不跳對話框）；刪除要按兩次。
- **assistant-sheet**：狀態字（accent-text）、聽到的句子（heard）、回覆（reply，ink-muted）、選項按鈕、麥克風按鈕。
- **mic-button**：跟著音量微微放大，看得出有在聽。
- **widget-button／widget-button-light**：主畫面與鎖定畫面小工具的深色、淺色版，一顆置中的麥克風按鈕（最大 56px，格子比較小時圓和麥克風一起等比縮小），整塊都能點；小工具清單裡各列一個（「聲得我心（深色）」「聲得我心（淺色）」）。

## App 圖示

石板灰斜向漸層底（icon-start → icon-end），白色圓環中間穿過一條青綠聲波線。前景只有線條，同一份也當 Android 13 起的單色主題圖示。白色圓環在漸層兩端對比 7.6:1 以上，青綠聲波 5.1:1 以上。

## 該做與不該做

- 該做：顏色、字級、圓角一律引用 token；新增顏色先寫進這份檔案，再同步 `values/colors.xml` 與 `values-night/colors.xml`。
- 該做：狀態同時用文字或符號表示（✓、⚠），不只靠顏色。
- 不該做：加第二個重點色、粉紅或紫色系、心形圖案。
- 不該做：用影視作品或其他品牌的造型（例如車用 AI 的聲音面板）當圖示或動畫。
