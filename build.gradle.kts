plugins {
    alias(libs.plugins.android.application) apply false
}

// 專案放在 Dropbox 裡，編譯輸出改放到 Dropbox 外面，避免同步搶檔案
val localAppData: String? = System.getenv("LOCALAPPDATA")
if (localAppData != null && "Dropbox" in rootDir.path) {
    allprojects { layout.buildDirectory.set(File(localAppData, "voice-assistant-build/$name")) }
}
