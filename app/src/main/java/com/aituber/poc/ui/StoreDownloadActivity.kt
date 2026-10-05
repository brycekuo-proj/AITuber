package com.aituber.poc.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.aituber.poc.character.live2d.Live2DNativeBridge
import com.aituber.poc.character.live2d.install.CharacterPackageInstaller
import java.net.URL

/**
 * In-app download page for store characters that live on creator sites
 * (BOOTH, itch.io, Live2D). The user never handles a file:
 *
 *  1. The creator page opens here, inside AITuber.
 *  2. On BOOTH the free-download link is picked and opened automatically; the
 *     first time, BOOTH asks the user to log in (their own account, kept by the
 *     WebView afterwards). On other sites the user taps the site's download button.
 *  3. The download is intercepted, converted and installed, and the app jumps
 *     straight to the home screen with the new character selected.
 *
 * The file is downloaded with the page's own cookies, so it is the user's own
 * download from the creator, not a copy hosted by AITuber.
 */
class StoreDownloadActivity : Activity() {

    private lateinit var entry: CharacterStoreEntry
    private lateinit var installer: CharacterPackageInstaller
    private lateinit var webView: WebView
    private lateinit var statusText: TextView
    private lateinit var busyOverlay: TextView
    private var installing = false
    private var autoStartAttempts = 0
    /** Set once a login page was shown; after login we return to the item page by ourselves. */
    private var sawLoginPage = false
    private var returnedAfterLogin = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val found = intent.getStringExtra(EXTRA_ENTRY_ID)?.let { id ->
            CharacterStoreCatalog.entries.firstOrNull { it.id == id }
        }
        if (found == null) {
            finish()
            return
        }
        entry = found
        installer = CharacterPackageInstaller(this)
        window.statusBarColor = Color.rgb(43, 8, 18)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportMultipleWindows(false)
            webViewClient = PageClient()
            setDownloadListener { url, userAgent, _, _, _ -> onDownloadStarted(url, userAgent) }
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        statusText = TextView(this).apply {
            text = "正在開啟作者頁面…"
            textSize = 13f
            setTextColor(Color.rgb(236, 207, 177))
            setPadding(dp(14), dp(6), dp(14), dp(8))
        }
        busyOverlay = TextView(this).apply {
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(220, 30, 4, 10))
            visibility = View.GONE
            isClickable = true // blocks taps on the page while installing
        }

        val content = FrameLayout(this).apply {
            addView(webView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(busyOverlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.rgb(39, 3, 13))
                addView(header())
                addView(statusText)
                addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            }
        )

        if (!Live2DNativeBridge.supportsDownloadedCharacters) {
            AlertDialog.Builder(this)
                .setTitle("暫時無法安裝")
                .setMessage("這個版本的 Live2D 元件太舊，無法載入下載的角色。需要重新編譯 App 的 Live2D 元件後才能使用。")
                .setPositiveButton("確定") { _, _ -> finish() }
                .setCancelable(false)
                .show()
            return
        }
        if (savedInstanceState != null && webView.restoreState(savedInstanceState) != null) return
        webView.loadUrl(entry.downloadUrl)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::webView.isInitialized) webView.saveState(outState)
    }

    override fun onBackPressed() {
        if (installing) return
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    private inner class PageClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            // Stay inside this page for web links; ignore intent:// and app deep links.
            val scheme = request.url.scheme ?: return true
            return scheme != "https" && scheme != "http"
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (installing || isFinishing) return
            val host = runCatching { URL(url).host }.getOrDefault("")
            val isLoginPage = host.endsWith("pixiv.net") || "sign_in" in url || "/login" in url
            if (isLoginPage) sawLoginPage = true
            when {
                host.endsWith("booth.pm") && "/items/" in url -> pickBoothDownload()
                host.endsWith("live2d.com") -> pickNamedDownload()
                // Logged in but BOOTH landed on another page: go back to the item once.
                host.endsWith("booth.pm") && !isLoginPage && sawLoginPage && !returnedAfterLogin -> {
                    returnedAfterLogin = true
                    statusText.text = "登入完成，正在回到角色頁面…"
                    view.loadUrl(entry.downloadUrl)
                }
                host.endsWith("booth.pm") || host.endsWith("pixiv.net") ->
                    statusText.text = "請登入 BOOTH（只需要一次），登入後會自動開始下載。\n若 Google 登入被擋，請改用 pixiv ID 或 Email 登入。"
                else ->
                    statusText.text = "請在頁面上按下載，下載的檔案會自動安裝成角色。"
            }
        }
    }

    /** Finds the item's free-download link, preferring the Live2D / VTube Studio ZIP. */
    private fun pickBoothDownload() {
        webView.evaluateJavascript(BOOTH_PICK_DOWNLOAD_JS) { result ->
            if (installing || isFinishing) return@evaluateJavascript
            // evaluateJavascript returns a JSON value; decode it properly.
            val link = runCatching { org.json.JSONArray("[$result]").optString(0) }.getOrDefault("")
            if (link.startsWith("https://") && autoStartAttempts < MAX_AUTO_START) {
                autoStartAttempts += 1
                statusText.text = "正在向作者網站取得檔案…"
                webView.loadUrl(link)
            } else if (autoStartAttempts >= MAX_AUTO_START) {
                statusText.text = "請在頁面上按「無料ダウンロード」，下載後會自動安裝。"
            } else {
                statusText.text = "請先登入 BOOTH（只需要一次），登入後會自動開始下載。"
            }
        }
    }

    /**
     * Pages listing many models (the Live2D sample collection): pick the ZIP link
     * closest to this character's name, avoiding Cubism 2 downloads.
     */
    private fun pickNamedDownload() {
        val name = org.json.JSONObject.quote(entry.displayName)
        webView.evaluateJavascript("($NAMED_PICK_DOWNLOAD_JS)($name)") { result ->
            if (installing || isFinishing) return@evaluateJavascript
            val link = runCatching { org.json.JSONArray("[$result]").optString(0) }.getOrDefault("")
            if (link.startsWith("https://") && autoStartAttempts < MAX_AUTO_START) {
                autoStartAttempts += 1
                statusText.text = "正在向 Live2D 官方取得檔案…"
                webView.loadUrl(link)
            } else {
                statusText.text = "請在頁面上找到「${entry.displayName}」並按下載，下載後會自動安裝。"
            }
        }
    }

    private fun onDownloadStarted(url: String, userAgent: String?) {
        if (installing) return
        if (!url.startsWith("https://")) {
            showFailure("這個下載連結不安全（不是 HTTPS），無法安裝。")
            return
        }
        installing = true
        webView.stopLoading()
        showBusy("正在下載「${entry.displayName}」…")
        installer.installFromUrl(
            url = url,
            store = CharacterPackageInstaller.StoreInfo(
                storeId = entry.id,
                displayName = entry.displayName,
                sourceUrl = entry.sourceUrl
            ),
            headers = { hop -> requestHeaders(hop, userAgent) },
            onProgress = { done, total ->
                showBusy(
                    if (total > 0) "正在下載「${entry.displayName}」… ${done * 100 / total}%"
                    else "正在下載「${entry.displayName}」… ${done / (1024 * 1024)} MB"
                )
            },
            onDone = { outcome ->
                if (isFinishing || isDestroyed) return@installFromUrl
                when (outcome) {
                    is CharacterPackageInstaller.Outcome.Installed -> {
                        showBusy("正在轉換角色…")
                        openCharacterPage(this, outcome.manifest.displayName)
                    }
                    is CharacterPackageInstaller.Outcome.Failed -> {
                        installing = false
                        busyOverlay.visibility = View.GONE
                        showFailure(outcome.error.userMessage)
                    }
                }
            }
        )
    }

    /** The page's cookies and user agent, so the creator site serves the logged-in download. */
    private fun requestHeaders(url: URL, userAgent: String?): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        userAgent?.takeIf { it.isNotBlank() }?.let { headers["User-Agent"] = it }
        CookieManager.getInstance().getCookie(url.toString())?.takeIf { it.isNotBlank() }?.let { headers["Cookie"] = it }
        headers["Referer"] = entry.downloadUrl
        return headers
    }

    private fun showBusy(message: String) {
        busyOverlay.text = message
        busyOverlay.visibility = View.VISIBLE
    }

    private fun showFailure(message: String) {
        AlertDialog.Builder(this)
            .setTitle("無法安裝「${entry.displayName}」")
            .setMessage(message)
            .setPositiveButton("確定", null)
            .show()
    }

    private fun header(): View = FrameLayout(this).apply {
        minimumHeight = dp(56)
        addView(
            TextView(this@StoreDownloadActivity).apply {
                text = "下載「${entry.displayName}」"
                textSize = 20f
                setTextColor(Color.rgb(233, 184, 83))
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                maxLines = 1
            },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(56)).apply {
                leftMargin = dp(56)
                rightMargin = dp(56)
            }
        )
        addView(
            TextView(this@StoreDownloadActivity).apply {
                text = "‹"
                textSize = 40f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                contentDescription = "返回商城"
                setOnClickListener { if (!installing) finish() }
            },
            FrameLayout.LayoutParams(dp(56), dp(56)).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val EXTRA_ENTRY_ID = "store_entry_id"
        private const val MAX_AUTO_START = 2

        /**
         * Picks the best "/downloadables/" link on a BOOTH item page: VTube Studio /
         * Live2D ZIPs first, PSDs, videos and RAR/7z last. Returns "" when the page
         * shows no download link (usually: not logged in yet).
         */
        private const val BOOTH_PICK_DOWNLOAD_JS = """
            (function () {
              var links = Array.prototype.slice.call(document.querySelectorAll('a[href*="/downloadables/"]'));
              if (!links.length) return "";
              function score(a) {
                var row = a.closest('li') || a.parentElement || a;
                var t = ((row.innerText || '') + ' ' + a.href).toLowerCase();
                var s = 0;
                if (t.indexOf('vts') >= 0 || t.indexOf('vtube') >= 0) s += 3;
                if (t.indexOf('live2d') >= 0 || t.indexOf('moc3') >= 0) s += 2;
                if (t.indexOf('.zip') >= 0) s += 2;
                if (t.indexOf('model') >= 0) s += 1;
                if (t.indexOf('.psd') >= 0 || t.indexOf('.clip') >= 0) s -= 5;
                if (t.indexOf('.mp4') >= 0 || t.indexOf('.png') >= 0 || t.indexOf('.jpg') >= 0) s -= 4;
                if (t.indexOf('.rar') >= 0 || t.indexOf('.7z') >= 0) s -= 3;
                return s;
              }
              links.sort(function (a, b) { return score(b) - score(a); });
              return links[0].href;
            })()
        """

        /** function(name) -> best ZIP link near that name on the page, or "". */
        private const val NAMED_PICK_DOWNLOAD_JS = """
            function (name) {
              var full = name.toLowerCase();
              var short = full.replace(/\s*\(.*\)\s*/, '').trim();
              var links = Array.prototype.slice.call(document.querySelectorAll('a[href]')).filter(function (a) {
                return /\.zip(\?|#|$)/i.test(a.href);
              });
              var best = "", bestScore = -1e9;
              links.forEach(function (a) {
                var el = a, depth = 0, hit = -1;
                while (el && depth < 8) {
                  var t = (el.innerText || '').toLowerCase();
                  if (t.indexOf(full) >= 0) { hit = depth; break; }
                  if (short && short !== full && t.indexOf(short) >= 0) { hit = depth + 0.5; break; }
                  el = el.parentElement; depth++;
                }
                if (hit < 0) return;
                var text = ((a.innerText || '') + ' ' + a.href).toLowerCase();
                var score = -hit * 10;
                if (/2\.1|cubism ?2|cubism2/.test(text)) score -= 20;
                if (score > bestScore) { bestScore = score; best = a.href; }
              });
              return best;
            }
        """

        fun intent(context: Context, entryId: String): Intent =
            Intent(context, StoreDownloadActivity::class.java).putExtra(EXTRA_ENTRY_ID, entryId)

        /** Home screen on top, showing the character that was just installed or picked. */
        fun openCharacterPage(activity: Activity, displayName: String, justInstalled: Boolean = true) {
            val message = if (justInstalled) "已加入「$displayName」" else "已切換到「$displayName」"
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            activity.startActivity(
                Intent(activity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            activity.finish()
        }
    }
}
