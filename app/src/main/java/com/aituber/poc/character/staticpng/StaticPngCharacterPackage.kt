package com.aituber.poc.character.staticpng

data class StaticPngCharacterPackage(
    val id: String,
    val displayName: String,
    val assetPath: String,
    val sourceHeightPx: Int
) {
    companion object {
        val XianxiaFemale = StaticPngCharacterPackage(
            id = "xianxia_female_static",
            displayName = "Xianxia Static",
            assetPath = "characters/xianxia_female/static/xianxia_female__static__master__2000px__v2.png",
            sourceHeightPx = 2000
        )
    }
}
