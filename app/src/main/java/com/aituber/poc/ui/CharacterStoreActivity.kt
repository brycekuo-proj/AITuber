package com.aituber.poc.ui

import android.app.Activity
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class CharacterStoreActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(43, 8, 18)
        window.navigationBarColor = Color.rgb(33, 8, 14)
        setContentView(buildStore())
    }

    private fun buildStore(): View {
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
                text = "精選免費角色"
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
                text = "角色檔案由原作者網站提供"
                textSize = 13f
                setTextColor(0xFFECCFB1.toInt())
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(12))
            }
        )

        CharacterStoreCatalog.entries.chunked(2).forEach { rowEntries ->
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

            addView(
                TextView(this@CharacterStoreActivity).apply {
                    text = "下載"
                    textSize = 18f
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setTextColor(Color.rgb(86, 43, 0))
                    background = roundedPanel(
                        fill = Color.rgb(244, 193, 85),
                        stroke = Color.rgb(255, 226, 142),
                        strokeWidth = dp(2),
                        radius = dp(12).toFloat()
                    )
                    isClickable = true
                    isFocusable = true
                    contentDescription = "下載 " + entry.displayName
                    setOnClickListener { openDownload(entry) }
                    installBroadwayPressFeedback()
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(46)
                ).apply {
                    topMargin = dp(8)
                }
            )
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

    private fun openDownload(entry: CharacterStoreEntry) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(entry.downloadUrl)))
        }.onFailure {
            Toast.makeText(this, "無法開啟下載頁面", Toast.LENGTH_SHORT).show()
        }
    }

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
