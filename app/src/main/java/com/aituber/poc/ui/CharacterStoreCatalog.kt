package com.aituber.poc.ui

/**
 * A store card.
 *
 * How "下載" works:
 *  - [downloadFiles] set: the app fetches exactly these files (public, no login) and installs them.
 *  - [directDownload] with [downloadUrl] pointing at a ZIP: the app downloads and installs it.
 *  - otherwise: [downloadUrl] opens in the in-app download page (StoreDownloadActivity), which
 *    starts the creator's download itself (BOOTH needs a one-time login) and installs the file.
 */
internal data class CharacterStoreEntry(
    val id: String,
    val displayName: String,
    val source: String,
    val sourceUrl: String,
    val downloadUrl: String,
    val previewUrl: String,
    val directDownload: Boolean = false,
    val downloadFiles: List<String> = emptyList(),
    /** Already bundled in the APK: "使用" selects this built-in profile, nothing is downloaded. */
    val builtInProfileId: String? = null,
    /** Non-null: listed but not downloadable yet (e.g. rights holder has not answered). */
    val unavailableReason: String? = null
)

/** IDs that must never be listed: creator terms forbid app use without written permission. */
internal val EXCLUDED_WITHOUT_PERMISSION = setOf(
    "raiga", "king_gilgamesh", "jupiter", "arias", "queen_nefertiti", "ram_khamhaeng",
    "julius_caesar", "hippocrates", "tyra", "apollo", "goddess_athena"
)

private const val ARCH_CHAN_RAW = "https://raw.githubusercontent.com/Speykious/arch-chan/main/"

/**
 * Official Live2D sample models published in Live2D Inc.'s own GitHub repositories
 * (CubismWebSamples, CubismUnityComponents, CubismWebMotionSyncComponents), pinned to a
 * commit so the files cannot change under us. Only the files model3.json references.
 */
private val OFFICIAL_GITHUB_FILES: Map<String, List<String>> = mapOf(
    "hiyori_momose" to listOf(
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Hiyori/Hiyori.model3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Hiyori/Hiyori.moc3",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Hiyori/Hiyori.2048/texture_00.png",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Hiyori/Hiyori.2048/texture_01.png",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Hiyori/Hiyori.physics3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Hiyori/Hiyori.pose3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Hiyori/Hiyori.cdi3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Hiyori/Hiyori.userdata3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Hiyori/motions/Hiyori_m01.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Hiyori/motions/Hiyori_m02.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Hiyori/motions/Hiyori_m03.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Hiyori/motions/Hiyori_m05.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Hiyori/motions/Hiyori_m06.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Hiyori/motions/Hiyori_m07.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Hiyori/motions/Hiyori_m08.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Hiyori/motions/Hiyori_m09.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Hiyori/motions/Hiyori_m10.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Hiyori/motions/Hiyori_m04.motion3.json"
    ),
    "niziiro_mao" to listOf(
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mao/Mao.model3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mao/Mao.moc3",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mao/Mao.2048/texture_00.png",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mao/Mao.physics3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mao/Mao.pose3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mao/Mao.cdi3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mao/expressions/exp_01.exp3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mao/expressions/exp_02.exp3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mao/expressions/exp_03.exp3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mao/expressions/exp_04.exp3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mao/expressions/exp_05.exp3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mao/expressions/exp_06.exp3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mao/expressions/exp_07.exp3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mao/expressions/exp_08.exp3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mao/motions/mtn_01.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mao/motions/sample_01.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mao/motions/mtn_02.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mao/motions/mtn_03.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mao/motions/mtn_04.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mao/motions/special_01.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mao/motions/special_02.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mao/motions/special_03.motion3.json"
    ),
    "mark_kun" to listOf(
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mark/Mark.model3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mark/Mark.moc3",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mark/Mark.2048/texture_00.png",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mark/Mark.physics3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mark/Mark.cdi3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mark/Mark.userdata3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mark/motions/mark_m01.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mark/motions/mark_m02.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mark/motions/mark_m03.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mark/motions/mark_m04.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mark/motions/mark_m05.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Mark/motions/mark_m06.motion3.json"
    ),
    "ren_foster" to listOf(
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Ren/Ren.model3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Ren/Ren.moc3",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Ren/Ren.2048/texture_00.png",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Ren/Ren.physics3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Ren/Ren.cdi3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Ren/expressions/exp_01.exp3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Ren/expressions/exp_02.exp3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Ren/expressions/exp_03.exp3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Ren/expressions/exp_04.exp3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Ren/expressions/exp_05.exp3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Ren/motions/mtn_01.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Ren/motions/mtn_02.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Ren/motions/mtn_03.motion3.json"
    ),
    "rice_glassfield" to listOf(
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Rice/Rice.model3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Rice/Rice.moc3",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Rice/Rice.2048/texture_00.png",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Rice/Rice.2048/texture_01.png",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Rice/Rice.physics3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Rice/Rice.cdi3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Rice/motions/idle.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Rice/motions/mtn_01.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Rice/motions/mtn_02.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Rice/motions/mtn_03.motion3.json"
    ),
    "wankoromochi" to listOf(
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Wanko/Wanko.model3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Wanko/Wanko.moc3",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Wanko/Wanko.1024/texture_00.png",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Wanko/Wanko.physics3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Wanko/Wanko.cdi3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Wanko/motions/idle_01.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Wanko/motions/idle_03.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Wanko/motions/idle_04.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Wanko/motions/touch_02.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebSamples/b1de66b0b1f1cb881d95fb6158622aeb6a2827bd/Samples/Resources/Wanko/motions/touch_04.motion3.json"
    ),
    "koharu" to listOf(
        "https://raw.githubusercontent.com/Live2D/CubismUnityComponents/5b378410225b4c78688f6da7a012adb1dc1cd24f/Assets/Live2D/Cubism/Samples/Models/Koharu/Koharu.model3.json",
        "https://raw.githubusercontent.com/Live2D/CubismUnityComponents/5b378410225b4c78688f6da7a012adb1dc1cd24f/Assets/Live2D/Cubism/Samples/Models/Koharu/Koharu.moc3",
        "https://raw.githubusercontent.com/Live2D/CubismUnityComponents/5b378410225b4c78688f6da7a012adb1dc1cd24f/Assets/Live2D/Cubism/Samples/Models/Koharu/Koharu.2048/texture_00.png",
        "https://raw.githubusercontent.com/Live2D/CubismUnityComponents/5b378410225b4c78688f6da7a012adb1dc1cd24f/Assets/Live2D/Cubism/Samples/Models/Koharu/Animation/body.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismUnityComponents/5b378410225b4c78688f6da7a012adb1dc1cd24f/Assets/Live2D/Cubism/Samples/Models/Koharu/Animation/face01.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismUnityComponents/5b378410225b4c78688f6da7a012adb1dc1cd24f/Assets/Live2D/Cubism/Samples/Models/Koharu/Animation/face02.motion3.json"
    ),
    "kei" to listOf(
        "https://raw.githubusercontent.com/Live2D/CubismWebMotionSyncComponents/d974ec56bd1534bd365ed4d50fdbbe6d52660124/Samples/Resources/Kei_basic/Kei_basic.model3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebMotionSyncComponents/d974ec56bd1534bd365ed4d50fdbbe6d52660124/Samples/Resources/Kei_basic/Kei_basic.moc3",
        "https://raw.githubusercontent.com/Live2D/CubismWebMotionSyncComponents/d974ec56bd1534bd365ed4d50fdbbe6d52660124/Samples/Resources/Kei_basic/Kei_basic.2048/texture_00.png",
        "https://raw.githubusercontent.com/Live2D/CubismWebMotionSyncComponents/d974ec56bd1534bd365ed4d50fdbbe6d52660124/Samples/Resources/Kei_basic/Kei_basic.physics3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebMotionSyncComponents/d974ec56bd1534bd365ed4d50fdbbe6d52660124/Samples/Resources/Kei_basic/Kei_basic.cdi3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebMotionSyncComponents/d974ec56bd1534bd365ed4d50fdbbe6d52660124/Samples/Resources/Kei_basic/motions/01_kei_en.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebMotionSyncComponents/d974ec56bd1534bd365ed4d50fdbbe6d52660124/Samples/Resources/Kei_basic/motions/01_kei_jp.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebMotionSyncComponents/d974ec56bd1534bd365ed4d50fdbbe6d52660124/Samples/Resources/Kei_basic/motions/01_kei_ko.motion3.json",
        "https://raw.githubusercontent.com/Live2D/CubismWebMotionSyncComponents/d974ec56bd1534bd365ed4d50fdbbe6d52660124/Samples/Resources/Kei_basic/motions/01_kei_zh.motion3.json"
    )
)

/** Official samples that ship inside the APK already. */
private val OFFICIAL_BUILT_IN = mapOf("tororo_sample" to "tororo", "hijiki_sample" to "hijiki", "haru_sample" to "haru")

/** Collaboration / third-party IP samples still waiting for a written OK (see license registry). */
private val OFFICIAL_PENDING_RIGHTS = setOf("zundamon", "hatsune_miku_sample", "jin_natori", "tsumiki_harugasa")

private fun official(id: String, name: String): CharacterStoreEntry {
    val page = "https://www.live2d.com/en/learn/sample/"
    val files = OFFICIAL_GITHUB_FILES[id].orEmpty()
    return CharacterStoreEntry(
        id = id,
        displayName = name,
        source = "Live2D Official",
        sourceUrl = page,
        downloadUrl = page,
        previewUrl = "",
        directDownload = files.isNotEmpty(),
        downloadFiles = files,
        builtInProfileId = OFFICIAL_BUILT_IN[id],
        unavailableReason = if (id in OFFICIAL_PENDING_RIGHTS) "授權確認中" else null
    )
}

internal object CharacterStoreCatalog {
    val entries: List<CharacterStoreEntry> get() = allEntries.filter { it.id !in EXCLUDED_WITHOUT_PERMISSION }

    private val allEntries: List<CharacterStoreEntry> = listOf(
        CharacterStoreEntry(
            id = "arch_chan",
            displayName = "Arch Chan",
            source = "GitHub",
            sourceUrl = "https://github.com/Speykious/arch-chan",
            downloadUrl = "https://github.com/Speykious/arch-chan/archive/refs/heads/main.zip",
            previewUrl = "https://opengraph.githubassets.com/6370eee9228799331896bbe10b38ef920e6b8f1cd6a462133f6a915a2045a0ce/Speykious/arch-chan",
            directDownload = true,
            // Only the Live2D runtime files (~2.4 MB) instead of the 135 MB repository archive.
            downloadFiles = listOf(
                "Live2D/arch%20chan%20model0.model3.json",
                "Live2D/Arch%20Chan%20Model0.moc3",
                "Live2D/Arch%20Chan%20Model0.2048/texture_00.png",
                "Live2D/Arch%20Chan%20Model0.physics3.json",
                "Live2D/Arch%20Chan%20Model0.cdi3.json",
                "Live2D/Mouse.exp3.json",
                "LICENSE.md"
            ).map { ARCH_CHAN_RAW + it }
        ),
        CharacterStoreEntry(
            id = "goth_lolita",
            displayName = "ダウナーなゴスロリ少女",
            source = "BOOTH",
            sourceUrl = "https://booth.pm/zh-tw/items/8589066",
            downloadUrl = "https://booth.pm/zh-tw/items/8589066",
            previewUrl = "https://booth.pximg.net/c/620x620/3f120f24-c61d-47f7-aa59-da982dd8a18f/i/8589066/e2d0ee89-e7ba-4a47-a16e-9e5e1daba4c8_base_resized.jpg"
        ),
        CharacterStoreEntry(
            id = "office_girl",
            displayName = "オフィスの女の子",
            source = "BOOTH",
            sourceUrl = "https://booth.pm/ja/items/4304615",
            downloadUrl = "https://booth.pm/ja/items/4304615",
            previewUrl = "https://booth.pximg.net/c/620x620/c69a8eec-60be-4b06-a043-56e78c17e092/i/4304615/f3ef002e-7c93-496c-892e-4cfa35b56276_base_resized.jpg"
        ),
        CharacterStoreEntry(
            id = "generic_boy01",
            displayName = "generic_boy01",
            source = "BOOTH",
            sourceUrl = "https://booth.pm/ja/items/5235226",
            downloadUrl = "https://booth.pm/ja/items/5235226",
            previewUrl = "https://booth.pximg.net/c/620x620/393c2f14-b02d-49d3-a4bc-b819b7489fbf/i/5235226/8dc8b009-f166-45ab-aae4-036ccd112cef_base_resized.jpg"
        ),
        CharacterStoreEntry(
            id = "scarlett",
            displayName = "Scarlett",
            source = "BOOTH",
            sourceUrl = "https://booth.pm/ja/items/6499001",
            downloadUrl = "https://booth.pm/ja/items/6499001",
            previewUrl = "https://booth.pximg.net/c/620x620/da520e9a-71a8-4df7-8d9c-b029e7dfd3cf/i/6499001/dd042ed5-e402-4b35-9a42-af7800bafb7e_base_resized.jpg"
        ),
        CharacterStoreEntry(
            id = "alexia",
            displayName = "Alexia",
            source = "BOOTH",
            sourceUrl = "https://booth.pm/ja/items/5576188",
            downloadUrl = "https://booth.pm/ja/items/5576188",
            previewUrl = "https://booth.pximg.net/c/620x620/5ea662ff-f1b5-4846-ae83-9cc682a9ef27/i/5576188/df51f6ab-865d-48c6-b70f-235c2debbfe3_base_resized.jpg"
        ),
        CharacterStoreEntry(
            id = "gloria",
            displayName = "Gloria",
            source = "BOOTH",
            sourceUrl = "https://booth.pm/zh-tw/items/4860071",
            downloadUrl = "https://booth.pm/zh-tw/items/4860071",
            previewUrl = "https://booth.pximg.net/c/620x620/c4330768-84bd-44f0-9760-6285854d81c5/i/4860071/0ee6863f-96b2-4b6f-97c5-37d71157ebbd_base_resized.jpg"
        ),
        CharacterStoreEntry(
            id = "snow_bear_girl",
            displayName = "雪熊少女",
            source = "BOOTH",
            sourceUrl = "https://booth.pm/zh-tw/items/7763817",
            downloadUrl = "https://booth.pm/zh-tw/items/7763817",
            previewUrl = "https://booth.pximg.net/c/620x620/12bc9ed4-4f56-4b8f-9741-772cab0a5540/i/7763817/5ed8d5c7-5f3e-4a68-b631-312b496ab674_base_resized.jpg"
        ),
        CharacterStoreEntry(
            id = "silver_hair_4_costumes",
            displayName = "Silver-haired girl",
            source = "BOOTH",
            sourceUrl = "https://booth.pm/ja/items/4154562",
            downloadUrl = "https://booth.pm/ja/items/4154562",
            previewUrl = "https://booth.pximg.net/c/620x620/e6129b72-54ab-4167-865c-1bd66ea02e66/i/4154562/4b6411c7-9b5f-4f55-a4d0-248afcb0d0fb_base_resized.jpg"
        ),
        CharacterStoreEntry(
            id = "mayoi",
            displayName = "Mayoi",
            source = "BOOTH",
            sourceUrl = "https://booth.pm/zh-tw/items/8447197",
            downloadUrl = "https://booth.pm/zh-tw/items/8447197",
            previewUrl = "https://booth.pximg.net/c/620x620/5ecf35cf-4d34-4799-87d9-9ae9d9a7f780/i/8447197/720d17d9-f700-45ed-8680-132a77deaa7a_base_resized.jpg"
        ),

        // 2026-09-18 expanded free Live2D pool.
        official("zundamon", "Zundamon"),
        official("ren_foster", "Ren Foster"),
        official("parameter_controller_sample", "Parameter Controller Sample"),
        official("kei", "Kei"),
        official("niziiro_mao", "Niziiro Mao"),
        official("hiyori_momose", "Hiyori Momose"),
        official("mark_kun", "Mark-kun"),
        official("hatsune_miku_sample", "Hatsune Miku"),
        official("jin_natori", "Jin Natori"),
        official("haru_receptionist", "Haru (Receptionist)"),
        official("rice_glassfield", "Rice Glassfield"),
        official("miara", "Miara"),
        official("tororo_sample", "Tororo"),
        official("hijiki_sample", "Hijiki"),
        official("simple_model", "Simple Model"),
        official("epsilon", "Epsilon"),
        official("haru_sample", "Haru"),
        official("chitose", "Chitose"),
        official("shizuku", "Shizuku"),
        official("hibiki", "Hibiki"),
        official("koharu", "Koharu"),
        official("haruto", "Haruto"),
        official("wankoromochi", "Wankoromochi"),
        official("tsumiki_harugasa", "Tsumiki Harugasa"),
        official("unity_chan", "Unity-chan"),
        official("izumi", "Izumi"),
        official("gantzert", "Gantzert"),
        official("felixander", "Felixander"),
        official("nito", "Nito"),

        CharacterStoreEntry("free_elf", "Free Elf Model", "BOOTH", "https://booth.pm/en/items/5260598", "https://booth.pm/en/items/5260598", ""),
        CharacterStoreEntry("black_hair_red_eye_boy", "Black Hair Red Eye Boy", "BOOTH", "https://booth.pm/en/items/6094263", "https://booth.pm/en/items/6094263", ""),
        CharacterStoreEntry("toki_rabbit_princess", "Toki / Rabbit Princess", "BOOTH", "https://booth.pm/en/items/3072192", "https://booth.pm/en/items/3072192", ""),
        CharacterStoreEntry("idol_style_female", "Idol-style Female Model", "BOOTH", "https://booth.pm/en/items/5775313", "https://booth.pm/en/items/5775313", ""),
        CharacterStoreEntry("retro_girl", "Retro Girl", "BOOTH", "https://booth.pm/en/items/5098191", "https://booth.pm/en/items/5098191", ""),
        CharacterStoreEntry("or_01", "or_01 Free Model", "BOOTH", "https://booth.pm/en/items/3932628", "https://booth.pm/en/items/3932628", ""),
        CharacterStoreEntry("cream_soda", "CreamSoda", "BOOTH", "https://booth.pm/en/items/5355619", "https://booth.pm/en/items/5355619", ""),
        CharacterStoreEntry("daru_angel", "DARU-Angel", "BOOTH", "https://booth.pm/en/items/5274628", "https://booth.pm/en/items/5274628", ""),
        CharacterStoreEntry("xiao_lai", "Xiao'Lai", "BOOTH", "https://booth.pm/en/items/8661826", "https://booth.pm/en/items/8661826", ""),
        CharacterStoreEntry("nitorudraw", "Nitorudraw", "BOOTH", "https://booth.pm/en/items/3987160", "https://booth.pm/en/items/3987160", "")
    )
}
