# 聲得我心

取代 Google 助理的 Android 語音助理，只做簡單的事，但做得快。長按電源鍵或在手機背面點兩下，說「明天早上七點叫我」「打開手電筒」「取消靜音」，馬上就做好。

做這個 App 的初衷很單純：Google 助理換成 Gemini 之後，連設鬧鐘、開手電筒這種小事都要經過大型 AI 模型。我只需要簡單的動作，不需要呼叫這麼高階的模型。所以常用指令都在手機上直接判斷，不需要 AI、不用登入；AI 只是選配。

- 免費、開放原始碼（[MIT 授權](LICENSE)）。
- 不收集任何資料，沒有使用統計，沒有廣告。
- 用 APK 安裝，沒有上架 Google Play：到[說明網站](https://c520.pages.dev/shengdewoxin/)下載，安裝步驟與常見問題也在那裡。

## 功能

- **叫出來的方式**：長按電源鍵、手機背面點兩下（Pixel 的「快速輕觸」）、主畫面與鎖定畫面小工具、快速設定磁貼、長按圖示的「說話」捷徑、藍牙耳機或車上的語音鍵。都不用一直開著麥克風，不額外耗電。
- **不需要 AI 的指令**（在手機上直接判斷，說完就做）：
  - 鬧鐘、計時：「明天早上七點叫我」「十分鐘後提醒我關火」。
  - 手電筒、音量、靜音與震動、螢幕亮度、勿擾。
  - 音樂：播放、暫停、上一首、下一首。
  - 打開 App（同音字、辨識錯字也找得到）、導航、打電話（名字相近的人會列出來讓你選，不會打錯人）。
  - 打開 Wi-Fi、藍牙、定位等設定頁。
  - 現在幾點、今天幾號、今天或明天的天氣。
- **情境**：自己設一句話做好幾件事。例如說「我到家了」，先回你「小明，歡迎回家」，再取消靜音、媒體音量調到 70、關閉勿擾。「要做的事」用平常說話的方式一行寫一句。
- **稱呼**：設定助理怎麼叫你，聆聽提示、情境的回應都會用到。
- **AI（選用）**：規則聽不懂的句子和一般問答，可以交給你自己的 AI 金鑰（Claude、DeepSeek、OpenAI，或其他 OpenAI 相容服務）。預設用最小、最快的模型並關掉「思考」，大約 1 秒回應。
- **外觀**：淺色、深色，或跟著系統。

## 還不會的事

- 喊名字叫出來：第三方 App 用不到手機裡專門聽喚醒詞的低耗電晶片，持續收音很耗電，所以改用上面那些不耗電的叫法。
- 傳 LINE、念通知。
- 直接切換 Wi-Fi、藍牙：Android 不開放，改成打開設定面板讓你點一下。

## 從原始碼編譯

需要 Android Studio（用它內附的 JDK 就可以），手機 Android 12 以上。

1. 用 Android Studio 開啟這個資料夾（第一次會自動下載 Gradle）。
2. 手機開啟「開發人員選項」與「USB 偵錯」，接上電腦。
3. 編譯並安裝：
   ```sh
   ./gradlew assembleDebug
   adb install -r --user 0 app/build/outputs/apk/debug/app-debug.apk
   ```
   `--user 0` 只裝在手機的主要使用者；不加的話，有工作資料夾或私人空間的手機會連那邊一起裝。
   在 Windows 命令列編譯前，先把 `JAVA_HOME` 設成 Android Studio 內附的 `jbr` 資料夾。
   專案放在 Windows 的 Dropbox 裡時，編譯輸出會改放到 `%LOCALAPPDATA%\voice-assistant-build`（見 `build.gradle.kts`）。
4. 打開 App，依設定頁完成「設為預設數位助理」與麥克風權限，再到系統設定把「按住電源鍵」改成「數位助理」。

正式版（release）要自己的簽章金鑰：在使用者的 `~/.gradle/gradle.properties`（不是專案資料夾）加上

```properties
shengdewoxin.storeFile=C:/Users/你的帳號/keys/shengdewoxin.jks
shengdewoxin.storePassword=…
shengdewoxin.keyAlias=…
shengdewoxin.keyPassword=…
```

再執行 `./gradlew assembleRelease`。金鑰與密碼不要放進版控。

## 需要的權限

- **麥克風**（必要）：聽你說話。語音辨識交給手機上的 Google 語音服務。
- **聯絡人、電話**：「打給媽媽」。
- **大概位置**：沒說地點的天氣。
- **行事曆**：「提醒我週五下午三點開會」寫進行事曆。
- **修改系統設定**：調螢幕亮度。
- **勿擾權限**：開關勿擾、切換靜音模式。
- **網路**：只在你填了 AI 金鑰或查天氣時使用。

用不到的權限可以不給，其他功能照常。

## 隱私

- 設定（含 AI 金鑰）只存在手機，不備份、不轉移到別的裝置。
- 會送出去的只有兩種：填了 AI 金鑰時，規則聽不懂的那一句話（加上 3 分鐘內的上一句）送給你選的 AI 服務；查天氣時，大概位置（經緯度到小數點後三位）送給 [Open-Meteo](https://open-meteo.com/)。
- 語音辨識由手機上的 Google 語音服務處理，依 Google 的隱私權政策。

## 授權與聲明

- 本專案以 [MIT 授權](LICENSE) 釋出。除了 Kotlin 標準函式庫（Apache 2.0），沒有使用其他第三方套件。
- 天氣資料來自 [Open-Meteo](https://open-meteo.com/)（CC BY 4.0）。
- Android、Google、Gemini 是 Google LLC 的商標；Claude 是 Anthropic 的商標；OpenAI 是 OpenAI 的商標；DeepSeek 是其所屬公司的商標。本 App 是個人開發的獨立作品，與上述公司沒有關係，也沒有經過認可。
- 設計系統見 [DESIGN.md](DESIGN.md)。
