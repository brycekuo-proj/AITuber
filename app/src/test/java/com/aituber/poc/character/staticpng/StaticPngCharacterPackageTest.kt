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
        val header = asset.readPngHeader()

        assertEquals(1023, header.width)
        assertEquals(2000, header.height)
        assertEquals(PNG_COLOR_TYPE_RGBA, header.colorType)
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
        assertEquals(1023, characterPackage.sourceWidthPx)
        assertEquals(2000, characterPackage.sourceHeightPx)
    }

    @Test
    fun xianxiaStaticPngPackageDefinesFullCanvasMouthLayers() {
        val characterPackage = StaticPngCharacterPackage.XianxiaFemale

        val half = characterPackage.mouthLayers.getValue(StaticPngMouthShape.HALF)
        val open = characterPackage.mouthLayers.getValue(StaticPngMouthShape.OPEN)

        assertEquals(
            "characters/xianxia_female/static/xianxia_female__mouth__half__replacement__v3.png",
            half.assetPath
        )
        assertEquals(
            "characters/xianxia_female/static/xianxia_female__mouth__open__replacement__v3.png",
            open.assetPath
        )
    }

    @Test
    fun xianxiaStaticPngPackageDefinesMouthReplacementPatchBounds() {
        val patch = StaticPngCharacterPackage.XianxiaFemale.mouthReplacementPatch

        assertEquals(456, patch.x)
        assertEquals(246, patch.y)
        assertEquals(112, patch.width)
        assertEquals(68, patch.height)
    }

    @Test
    fun xianxiaMouthLayerAssetsAreFullCanvasRgbaPngs() {
        val characterPackage = StaticPngCharacterPackage.XianxiaFemale

        StaticPngCharacterPackage.XianxiaFemale.mouthLayers.values.forEach { layer ->
            val asset = File("src/main/assets/${layer.assetPath}")

            assertTrue("Missing mouth layer asset: ${asset.path}", asset.isFile)
            val header = asset.readPngHeader()

            assertEquals(characterPackage.sourceWidthPx, header.width)
            assertEquals(characterPackage.sourceHeightPx, header.height)
            assertEquals(PNG_COLOR_TYPE_RGBA, header.colorType)
        }
    }

    @Test
    fun mouthShapeThresholdsMatchPocRules() {
        assertEquals(StaticPngMouthShape.CLOSED, StaticPngMouthShape.fromRatio(0.00f))
        assertEquals(StaticPngMouthShape.CLOSED, StaticPngMouthShape.fromRatio(0.25f))
        assertEquals(StaticPngMouthShape.HALF, StaticPngMouthShape.fromRatio(0.251f))
        assertEquals(StaticPngMouthShape.HALF, StaticPngMouthShape.fromRatio(0.65f))
        assertEquals(StaticPngMouthShape.OPEN, StaticPngMouthShape.fromRatio(0.651f))
        assertEquals(StaticPngMouthShape.OPEN, StaticPngMouthShape.fromRatio(1.00f))
    }

    private fun File.readPngHeader(): PngHeader {
        val png = readBytes()
        assertTrue("Invalid PNG signature", png.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE))
        return PngHeader(
            width = png.readPngInt(offset = 16),
            height = png.readPngInt(offset = 20),
            colorType = png[25].toInt() and 0xff
        )
    }

    private fun ByteArray.readPngInt(offset: Int): Int =
        ((this[offset].toInt() and 0xff) shl 24) or
            ((this[offset + 1].toInt() and 0xff) shl 16) or
            ((this[offset + 2].toInt() and 0xff) shl 8) or
            (this[offset + 3].toInt() and 0xff)

    data class PngHeader(
        val width: Int,
        val height: Int,
        val colorType: Int
    )

    companion object {
        private val PNG_SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        private const val PNG_COLOR_TYPE_RGBA = 6
    }
}
