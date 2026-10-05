package com.aituber.poc.character.live2d.install

/** Why a creator package could not be installed. [userMessage] is shown in the app. */
enum class PackageError(val userMessage: String) {
    NOT_AN_ARCHIVE("這不是 ZIP 壓縮檔"),
    UNSUPPORTED_ARCHIVE_RAR("RAR 壓縮檔目前不支援，請先解壓後重新壓成 ZIP 再匯入"),
    UNSUPPORTED_ARCHIVE_7Z("7z 壓縮檔目前不支援，請先解壓後重新壓成 ZIP 再匯入"),
    CORRUPT_ARCHIVE("壓縮檔已損壞或下載不完整"),
    ARCHIVE_TOO_LARGE("壓縮檔太大"),
    TOO_MANY_FILES("壓縮檔內的檔案數量太多"),
    FILE_TOO_LARGE("壓縮檔內有單一檔案太大"),
    NO_MODEL("找不到 Live2D 模型（.model3.json）"),
    CUBISM2_MODEL("這是 Cubism 2 舊版模型（.moc），目前不支援"),
    EDITOR_SOURCE_ONLY("這是 Cubism 編輯器原始檔（.cmo3），需要作者匯出的執行檔（.moc3 + .model3.json）"),
    ENCRYPTED_LPK("這是 Live2DViewerEX 加密格式（.lpk），無法轉換"),
    OTHER_FORMAT("這不是 Live2D Cubism 模型（可能是 VRM / Inochi2D / Spine）"),
    MODEL_JSON_INVALID("model3.json 格式錯誤"),
    MOC_MISSING("找不到模型本體檔（.moc3）"),
    MOC_INVALID("模型本體檔（.moc3）已損壞"),
    MOC_TOO_NEW("這個模型用的 Cubism 版本比 App 支援的新，請等 App 更新"),
    TEXTURE_MISSING("找不到模型貼圖"),
    TEXTURE_INVALID("模型貼圖不是有效的 PNG"),
    TEXTURE_TOO_LARGE("模型貼圖尺寸超過 8192px"),
    DOWNLOAD_FAILED("下載失敗"),
    STORAGE_FAILED("無法寫入手機儲存空間"),
    INTERNAL("轉換時發生未預期的錯誤")
}

class PackageException(val error: PackageError, detail: String = "") :
    Exception(if (detail.isEmpty()) error.name else "${error.name}: $detail")
