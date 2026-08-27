package com.aituber.poc.character.staticpng

data class StaticPngCharacterPackage(
    val id: String,
    val displayName: String,
    val assetPath: String,
    val sourceWidthPx: Int,
    val sourceHeightPx: Int,
    val mouthReplacementPatch: StaticPngMouthPatch,
    val mouthLayers: Map<StaticPngMouthShape, StaticPngMouthLayer>
) {
    companion object {
        val XianxiaFemale = StaticPngCharacterPackage(
            id = "xianxia_female_static",
            displayName = "Xianxia Static",
            assetPath = "characters/xianxia_female/static/xianxia_female__static__master__2000px__v2.png",
            sourceWidthPx = 1023,
            sourceHeightPx = 2000,
            mouthReplacementPatch = StaticPngMouthPatch(
                x = 459,
                y = 246,
                width = 107,
                height = 57
            ),
            mouthLayers = mapOf(
                StaticPngMouthShape.HALF to StaticPngMouthLayer(
                    assetPath = "characters/xianxia_female/static/xianxia_female__mouth__half__replacement__v4.png"
                ),
                StaticPngMouthShape.OPEN to StaticPngMouthLayer(
                    assetPath = "characters/xianxia_female/static/xianxia_female__mouth__open__replacement__v4.png"
                )
            )
        )
    }
}

data class StaticPngMouthPatch(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

data class StaticPngMouthLayer(
    val assetPath: String
)

enum class StaticPngMouthShape {
    CLOSED,
    HALF,
    OPEN;

    companion object {
        fun debugRatio(shape: StaticPngMouthShape): Float = when (shape) {
            CLOSED -> 0f
            HALF -> 0.45f
            OPEN -> 1f
        }

        fun fromRatio(ratio: Float): StaticPngMouthShape {
            val clamped = ratio.coerceIn(0f, 1f)
            return when {
                clamped <= 0.25f -> CLOSED
                clamped <= 0.65f -> HALF
                else -> OPEN
            }
        }
    }
}
