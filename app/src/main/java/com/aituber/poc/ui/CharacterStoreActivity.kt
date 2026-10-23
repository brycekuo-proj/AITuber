package com.aituber.poc.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.aituber.poc.character.live2d.Live2DCharacterProfile
import com.aituber.poc.character.live2d.Live2DCharacterProfiles
import com.aituber.poc.character.live2d.Live2DNativeBridge
import com.aituber.poc.character.live2d.Live2DProfileStore
import com.aituber.poc.character.live2d.install.CharacterPackageInstaller
import com.aituber.poc.character.live2d.install.InstalledCharacterRegistry

class CharacterStoreActivity : Activity() {

    private var selectedSource: String? = null
    private lateinit var installer: CharacterPackageInstaller
    private var installDialog: AlertDialog? = null
    private var installMessage: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(43, 8, 18)
        window.navigationBarColor = Color.rgb(33, 8, 14)
        installer = CharacterPackageInstaller(this)
        InstalledCharacterRegistry.ensureLoaded(this)
        setContentView(buildStore())
    }

    private var shownInstalledIds: Set<String> = emptySet()
    /** Blocks a double tap from opening two download pages / starting two installs. */
    private var downloadInFlight = false

    override fun onResume() {
        super.onResume()
        downloadInFlight = installDialog != null
        // Back from the in-app download page: refresh "使用" / "下載" only if something changed.
        val installedNow = installedIds()
        if (installedNow != shownInstalledIds) setContentView(buildStore())
    }

    private fun installedIds(): Set<String> =
        CharacterStoreCatalog.entries.map { storeCharacterId(it) }
            .filter { InstalledCharacterRegistry.isInstalled(this, it) }.toSet()

    override fun onDestroy() {
        installDialog?.dismiss()
        installDialog = null
        super.onDestroy()
    }

    /** False when the bundled native library is too old to render downloaded characters. */
    private fun ensureInstallSupported(): Boolean {
        if (Live2DNativeBridge.supportsDownloadedCharacters) return true
        AlertDialog.Builder(this)
            .setTitle("暫時無法安裝")
            .setMessage("這個版本的 Live2D 元件太舊，無法載入下載的角色。需要重新編譯 App 的 Live2D 元件後才能使用。")
            .setPositiveButton("確定", null)
            .show()
        return false
    }

    private fun buildStore(): View {
        shownInstalledIds = installedIds()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.rgb(39, 3, 13),
                    Color.rgb(89, 8, 23),
                    Color.rgb(29, 2, 9)
                )
            )
        }

        root.addView(buildHeader())
        root.addView(buildTabs())
        root.addView(buildSourceFilters())

        val visibleEntries = selectedSource?.let { source ->
            CharacterStoreCatalog.entries.filter { it.source == source }
        } ?: CharacterStoreCatalog.entries

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(24))
        }

        content.addView(
            TextView(this).apply {
                text = if (selectedSource == null) {
                    "精選免費角色 · ${visibleEntries.size}"
                } else {
                    "${sourceLabel(selectedSource!!)} · ${visibleEntries.size}"
                }
                textSize = 25f
                setTextColor(GOLD)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setShadowLayer(4f, 0f, dp(2).toFloat(), Color.BLACK)
                setPadding(0, dp(4), 0, dp(6))
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        content.addView(
            TextView(this).apply {
                text = "按「下載」即可加入角色，檔案由原作者網站提供"
                textSize = 13f
                setTextColor(0xFFECCFB1.toInt())
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(12))
            }
        )

        visibleEntries.chunked(2).forEach { rowEntries ->
            content.addView(buildRow(rowEntries))
        }

        scroll.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        return root
    }

    private fun buildHeader(): View {
        return FrameLayout(this).apply {
            minimumHeight = dp(70)

            addView(
                TextView(this@CharacterStoreActivity).apply {
                    text = "角色商城"
                    textSize = 30f
                    setTextColor(GOLD)
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setShadowLayer(5f, 0f, dp(2).toFloat(), Color.BLACK)
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    dp(66)
                ).apply {
                    gravity = Gravity.CENTER
                    leftMargin = dp(64)
                    rightMargin = dp(64)
                }
            )

            addView(
                TextView(this@CharacterStoreActivity).apply {
                    text = "‹"
                    textSize = 48f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    contentDescription = "返回我的角色"
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { finish() }
                    installBroadwayPressFeedback()
                },
                FrameLayout.LayoutParams(dp(58), dp(58)).apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                }
            )

            addView(
                BroadwayControlView(
                    context = this@CharacterStoreActivity,
                    kind = BroadwayControlView.Kind.SETTINGS
                ).apply {
                    contentDescription = "Settings"
                    setOnClickListener {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:" + packageName)
                            }
                        )
                    }
                },
                FrameLayout.LayoutParams(dp(58), dp(58)).apply {
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                }
            )
        }
    }

    private fun buildTabs(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(10))

            addView(
                navTab("我的角色", active = false) { finish() },
                LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                    marginEnd = dp(6)
                }
            )
            addView(
                navTab("角色商城", active = true) {},
                LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                    marginStart = dp(6)
                }
            )
        }
    }

    private fun buildSourceFilters(): View {
        val sources = CharacterStoreCatalog.entries.map { it.source }.distinct()

        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(
                LinearLayout(this@CharacterStoreActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(2), 0, dp(10))

                    addView(
                        sourceFilterButton(
                            label = "全部",
                            count = CharacterStoreCatalog.entries.size,
                            source = null
                        )
                    )

                    sources.forEach { source ->
                        addView(
                            sourceFilterButton(
                                label = sourceLabel(source),
                                count = CharacterStoreCatalog.entries.count { it.source == source },
                                source = source
                            ),
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                dp(42)
                            ).apply {
                                marginStart = dp(8)
                            }
                        )
                    }
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun sourceFilterButton(label: String, count: Int, source: String?): View {
        val active = selectedSource == source
        return TextView(this).apply {
            text = "$label  $count"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            setTextColor(if (active) Color.rgb(78, 13, 10) else Color.WHITE)
            background = roundedPanel(
                fill = if (active) GOLD else Color.rgb(73, 12, 28),
                stroke = GOLD,
                strokeWidth = dp(2),
                radius = dp(18).toFloat()
            )
            minHeight = dp(42)
            isClickable = true
            isFocusable = true
            contentDescription = "篩選來源 $label"
            setOnClickListener {
                if (selectedSource != source) {
                    selectedSource = source
                    setContentView(buildStore())
                }
            }
            installBroadwayPressFeedback()
        }
    }

    private fun sourceLabel(source: String): String = when (source) {
        "Live2D Official" -> "Live2D"
        else -> source
    }

    private fun navTab(label: String, active: Boolean, action: () -> Unit): View {
        return TextView(this).apply {
            text = label
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(if (active) Color.rgb(78, 13, 10) else Color.WHITE)
            background = roundedPanel(
                fill = if (active) GOLD else Color.rgb(93, 9, 28),
                stroke = GOLD,
                strokeWidth = dp(2),
                radius = dp(14).toFloat()
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            installBroadwayPressFeedback()
        }
    }

    private fun buildRow(entries: List<CharacterStoreEntry>): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, 0, 0, dp(12))

            entries.forEachIndexed { index, entry ->
                addView(
                    buildCard(entry),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (index == 0) marginEnd = dp(6) else marginStart = dp(6)
                    }
                )
            }
            if (entries.size == 1) {
                addView(
                    View(this@CharacterStoreActivity),
                    LinearLayout.LayoutParams(0, 1, 1f).apply {
                        marginStart = dp(6)
                    }
                )
            }
        }
    }

    private fun buildCard(entry: CharacterStoreEntry): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(10))
            background = roundedPanel(
                fill = Color.rgb(54, 7, 16),
                stroke = Color.rgb(201, 148, 55),
                strokeWidth = dp(2),
                radius = dp(16).toFloat()
            )

            val previewFrame = FrameLayout(this@CharacterStoreActivity).apply {
                background = roundedPanel(
                    fill = Color.rgb(26, 4, 9),
                    stroke = Color.rgb(137, 85, 34),
                    strokeWidth = dp(1),
                    radius = dp(11).toFloat()
                )
            }
            val placeholder = TextView(this@CharacterStoreActivity).apply {
                text = "🎭"
                textSize = 36f
                gravity = Gravity.CENTER
                setTextColor(0xFFBBA68E.toInt())
            }
            val preview = ImageView(this@CharacterStoreActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = entry.displayName
            }
            previewFrame.addView(
                placeholder,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            previewFrame.addView(
                preview,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                previewFrame,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(180)
                )
            )
            CharacterStoreImageLoader.load(entry.previewUrl, preview)

            addView(
                TextView(this@CharacterStoreActivity).apply {
                    text = entry.displayName
                    textSize = 16f
                    maxLines = 2
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    setPadding(dp(2), dp(8), dp(2), dp(5))
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(52)
                )
            )

            addView(
                LinearLayout(this@CharacterStoreActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    addView(badge(entry.source, 0xFF532A5F.toInt()))
                    addView(
                        badge("免費", 0xFF166D58.toInt()),
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            marginStart = dp(5)
                        }
                    )
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(31)
                )
            )

            val installed = entry.builtInProfileId != null ||
                InstalledCharacterRegistry.isInstalled(this@CharacterStoreActivity, storeCharacterId(entry))
            val blocked = entry.unavailableReason
            // Downloaded from here (not built in): it can be deleted again.
            val downloadedProfile = if (entry.builtInProfileId == null && installed) {
                Live2DCharacterProfiles.all.firstOrNull { it.id == storeCharacterId(entry) }
                    ?.takeIf(DownloadedCharacters::isDownloaded)
            } else {
                null
            }
            val actionButton =
                TextView(this@CharacterStoreActivity).apply {
                    text = when {
                        blocked != null -> blocked
                        installed -> "使用"
                        else -> "下載"
                    }
                    textSize = 18f
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setTextColor(if (blocked != null) Color.rgb(190, 170, 150) else Color.rgb(86, 43, 0))
                    background = roundedPanel(
                        fill = if (blocked != null) Color.rgb(90, 60, 55) else Color.rgb(244, 193, 85),
                        stroke = if (blocked != null) Color.rgb(130, 100, 80) else Color.rgb(255, 226, 142),
                        strokeWidth = dp(2),
                        radius = dp(12).toFloat()
                    )
                    isClickable = true
                    isFocusable = true
                    contentDescription = text.toString() + " " + entry.displayName
                    setOnClickListener {
                        when {
                            blocked != null -> Toast.makeText(
                                this@CharacterStoreActivity,
                                "正在等待版權方的書面同意，確認後就能下載",
                                Toast.LENGTH_SHORT
                            ).show()
                            entry.builtInProfileId != null -> useInstalled(entry.builtInProfileId)
                            installed -> useInstalled(storeCharacterId(entry))
                            else -> download(entry)
                        }
                    }
                    installBroadwayPressFeedback()
                }
            val actionParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)
            ).apply {
                topMargin = dp(8)
            }
            if (downloadedProfile == null) {
                addView(actionButton, actionParams)
            } else {
                addView(
                    LinearLayout(this@CharacterStoreActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        addView(actionButton, LinearLayout.LayoutParams(0, dp(46), 1f))
                        addView(
                            deleteButton(downloadedProfile),
                            LinearLayout.LayoutParams(dp(58), dp(46)).apply { marginStart = dp(6) }
                        )
                    },
                    actionParams
                )
            }
        }
    }

    private fun deleteButton(profile: Live2DCharacterProfile): View {
        return TextView(this).apply {
            text = "刪除"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = roundedPanel(
                fill = Color.rgb(150, 36, 40),
                stroke = Color.rgb(222, 120, 110),
                strokeWidth = dp(2),
                radius = dp(12).toFloat()
            )
            isClickable = true
            isFocusable = true
            contentDescription = "刪除 " + profile.displayName
            setOnClickListener {
                if (downloadInFlight) return@setOnClickListener
                DownloadedCharacters.confirmDelete(this@CharacterStoreActivity, profile) {
                    val store = this@CharacterStoreActivity
                    if (!store.isFinishing && !store.isDestroyed) store.setContentView(store.buildStore())
                }
            }
            installBroadwayPressFeedback()
        }
    }

    private fun badge(textValue: String, fill: Int): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = roundedPanel(fill, GOLD, dp(1), dp(20).toFloat())
        }
    }

    /**
     * One tap from the store to the character page: public files are fetched
     * directly; creator-site downloads (BOOTH etc.) run in the in-app download page.
     */
    private fun download(entry: CharacterStoreEntry) {
        if (downloadInFlight) return
        if (!ensureInstallSupported()) return
        downloadInFlight = true
        when {
            entry.downloadFiles.isNotEmpty() -> {
                showProgress("正在下載「${entry.displayName}」…")
                installer.installFromFiles(
                    urls = entry.downloadFiles,
                    store = entry.toStoreInfo(),
                    onProgress = ::onDownloadProgress,
                    onDone = ::onInstallFinished
                )
            }
            entry.directDownload -> {
                showProgress("正在下載「${entry.displayName}」…")
                installer.installFromUrl(
                    url = entry.downloadUrl,
                    store = entry.toStoreInfo(),
                    onProgress = ::onDownloadProgress,
                    onDone = ::onInstallFinished
                )
            }
            else -> startActivity(StoreDownloadActivity.intent(this, entry.id))
        }
    }

    private fun onDownloadProgress(done: Long, total: Long) {
        installMessage?.text = if (total > 0) {
            "正在下載… ${done * 100 / total}%"
        } else {
            "正在下載… ${"%.1f".format(done / (1024.0 * 1024.0))} MB"
        }
    }

    private fun onInstallFinished(outcome: CharacterPackageInstaller.Outcome) {
        downloadInFlight = false
        installDialog?.dismiss()
        installDialog = null
        installMessage = null
        if (isFinishing || isDestroyed) return
        when (outcome) {
            // The installer already selected the character; go straight to it.
            is CharacterPackageInstaller.Outcome.Installed ->
                StoreDownloadActivity.openCharacterPage(this, outcome.manifest.displayName)
            is CharacterPackageInstaller.Outcome.Failed ->
                AlertDialog.Builder(this)
                    .setTitle("無法安裝")
                    .setMessage(outcome.error.userMessage)
                    .setPositiveButton("確定", null)
                    .show()
        }
    }

    private fun useInstalled(id: String) {
        val profile = Live2DCharacterProfiles.byId(id)
        Live2DProfileStore.save(this, profile)
        InstalledCharacterRegistry.lastInstalledId = id
        StoreDownloadActivity.openCharacterPage(this, profile.displayName, justInstalled = false)
    }

    private fun showProgress(message: String) {
        installDialog?.dismiss()
        val text = TextView(this).apply {
            this.text = message
            textSize = 16f
            setPadding(dp(24), dp(20), dp(24), dp(20))
        }
        installMessage = text
        installDialog = AlertDialog.Builder(this)
            .setView(text)
            .setCancelable(false)
            .show()
    }

    private fun storeCharacterId(entry: CharacterStoreEntry): String =
        "store_" + CharacterPackageInstaller.slug(entry.id)

    private fun CharacterStoreEntry.toStoreInfo() = CharacterPackageInstaller.StoreInfo(
        storeId = id,
        displayName = displayName,
        sourceUrl = sourceUrl
    )

    private fun roundedPanel(
        fill: Int,
        stroke: Int,
        strokeWidth: Int,
        radius: Float
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = radius
            setStroke(strokeWidth, stroke)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private val GOLD = Color.rgb(233, 184, 83)

    }
}
