package com.aituber.poc.character.staticpng

data class StaticPngCharacterPackage(
    val id: String,
    val displayName: String,
    val assetPath: String,
    val sourceWidthPx: Int,
    val sourceHeightPx: Int,
    val mouthReplacementPatch: StaticPngMouthPatch,
    val mouthLayers: Map<StaticPngMouthShape, StaticPngMouthLayer>,
    val eyeReplacementPatch: StaticPngEyePatch,
    val eyeLayers: Map<StaticPngEyeShape, StaticPngEyeLayer>,
    val hairLayers: Map<StaticPngHairShape, StaticPngHairLayer>
) {
    companion object {
        val XianxiaFemale = StaticPngCharacterPackage(
            id = "xianxia_female_static",
            displayName = "Xianxia Static",
            assetPath = "characters/xianxia_female/static/xianxia_female__static__master__2000px__v3.png",
            sourceWidthPx = 1023,
            sourceHeightPx = 2000,
            mouthReplacementPatch = StaticPngMouthPatch(
                x = 471,
                y = 252,
                width = 82,
                height = 48
            ),
            mouthLayers = mapOf(
                StaticPngMouthShape.HALF to StaticPngMouthLayer(
                    assetPath = "characters/xianxia_female/static/xianxia_female__mouth__half__replacement__v5.png"
                ),
                StaticPngMouthShape.OPEN to StaticPngMouthLayer(
                    assetPath = "characters/xianxia_female/static/xianxia_female__mouth__open__replacement__v5.png"
                )
            ),
            eyeReplacementPatch = StaticPngEyePatch(
                x = 406,
                y = 198,
                width = 218,
                height = 38
            ),
            eyeLayers = mapOf(
                StaticPngEyeShape.HALF to StaticPngEyeLayer(
                    assetPath = "characters/xianxia_female/static/xianxia_female__eyes__half__replacement__v3.png"
                ),
                StaticPngEyeShape.CLOSED to StaticPngEyeLayer(
                    assetPath = "characters/xianxia_female/static/xianxia_female__eyes__closed__replacement__v2.png"
                )
            ),
            hairLayers = mapOf(
                StaticPngHairShape.FLOAT_A to StaticPngHairLayer(
                    assetPath = "characters/xianxia_female/static/xianxia_female__hair__float_a__replacement__v6.png"
                ),
                StaticPngHairShape.FLOAT_B to StaticPngHairLayer(
                    assetPath = "characters/xianxia_female/static/xianxia_female__hair__float_b__replacement__v6.png"
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

data class StaticPngEyePatch(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

data class StaticPngEyeLayer(
    val assetPath: String
)

data class StaticPngHairLayer(
    val assetPath: String
)

enum class StaticPngEyeShape {
    OPEN,
    HALF,
    CLOSED
}

enum class StaticPngHairShape {
    BASE,
    FLOAT_A,
    FLOAT_B
}

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
