package com.aituber.poc.character.staticpng

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticPngCharacterPackageTest {
    @Test
    fun xianxiaStaticPngAssetIsRgbaAndTwoThousandPixelsTall() {
        val asset = File("src/main/assets/${StaticPngCharacterPackage.XianxiaFemale.assetPath}")

        assertTrue("Missing static PNG asset: ${asset.path}", asset.isFile)
        val png = asset.readBytes()
        assertTrue("Invalid PNG signature", png.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE))
        val width = png.readPngInt(offset = 16)
        val height = png.readPngInt(offset = 20)
        val colorType = png[25].toInt() and 0xff

        assertEquals(1023, width)
        assertEquals(2000, height)
        assertEquals(PNG_COLOR_TYPE_RGBA, colorType)
    }

    @Test
    fun xianxiaStaticPngPackageUsesStaticAssetPath() {
        val characterPackage = StaticPngCharacterPackage.XianxiaFemale

        assertEquals("xianxia_female_static", characterPackage.id)
        assertEquals("Xianxia Static", characterPackage.displayName)
        assertEquals(
            "characters/xianxia_female/static/xianxia_female__static__master__2000px__v2.png",
            characterPackage.assetPath
        )
        assertEquals(2000, characterPackage.sourceHeightPx)
    }

    private fun ByteArray.readPngInt(offset: Int): Int =
        ((this[offset].toInt() and 0xff) shl 24) or
            ((this[offset + 1].toInt() and 0xff) shl 16) or
            ((this[offset + 2].toInt() and 0xff) shl 8) or
            (this[offset + 3].toInt() and 0xff)

    companion object {
        private val PNG_SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        private const val PNG_COLOR_TYPE_RGBA = 6
    }
}
