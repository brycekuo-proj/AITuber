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
            "characters/xianxia_female/static/xianxia_female__static__master__2000px__v3.png",
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
            "characters/xianxia_female/static/xianxia_female__mouth__half__replacement__v5.png",
            half.assetPath
        )
        assertEquals(
            "characters/xianxia_female/static/xianxia_female__mouth__open__replacement__v5.png",
            open.assetPath
        )
    }

    @Test
    fun xianxiaStaticPngPackageDefinesMouthReplacementPatchBounds() {
        val patch = StaticPngCharacterPackage.XianxiaFemale.mouthReplacementPatch

        assertEquals(471, patch.x)
        assertEquals(252, patch.y)
        assertEquals(82, patch.width)
        assertEquals(48, patch.height)
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
    fun xianxiaStaticPngPackageDefinesFullCanvasEyeLayers() {
        val characterPackage = StaticPngCharacterPackage.XianxiaFemale

        val half = characterPackage.eyeLayers.getValue(StaticPngEyeShape.HALF)
        val closed = characterPackage.eyeLayers.getValue(StaticPngEyeShape.CLOSED)

        assertEquals(
            "characters/xianxia_female/static/xianxia_female__eyes__half__replacement__v3.png",
            half.assetPath
        )
        assertEquals(
            "characters/xianxia_female/static/xianxia_female__eyes__closed__replacement__v2.png",
            closed.assetPath
        )
    }

    @Test
    fun xianxiaStaticPngPackageDefinesEyeReplacementPatchBounds() {
        val patch = StaticPngCharacterPackage.XianxiaFemale.eyeReplacementPatch

        assertEquals(406, patch.x)
        assertEquals(198, patch.y)
        assertEquals(218, patch.width)
        assertEquals(38, patch.height)
    }

    @Test
    fun xianxiaEyeLayerAssetsAreFullCanvasRgbaPngs() {
        val characterPackage = StaticPngCharacterPackage.XianxiaFemale

        characterPackage.eyeLayers.values.forEach { layer ->
            val asset = File("src/main/assets/${layer.assetPath}")

            assertTrue("Missing eye layer asset: ${asset.path}", asset.isFile)
            val header = asset.readPngHeader()

            assertEquals(characterPackage.sourceWidthPx, header.width)
            assertEquals(characterPackage.sourceHeightPx, header.height)
            assertEquals(PNG_COLOR_TYPE_RGBA, header.colorType)
        }
    }

    @Test
    fun xianxiaStaticPngPackageDefinesHairFloatLayers() {
        val characterPackage = StaticPngCharacterPackage.XianxiaFemale

        val floatA = characterPackage.hairLayers.getValue(StaticPngHairShape.FLOAT_A)
        val floatB = characterPackage.hairLayers.getValue(StaticPngHairShape.FLOAT_B)

        assertEquals(
            "characters/xianxia_female/static/xianxia_female__hair__float_a__replacement__v7.png",
            floatA.assetPath
        )
        assertEquals(
            "characters/xianxia_female/static/xianxia_female__hair__float_b__replacement__v7.png",
            floatB.assetPath
        )
    }

    @Test
    fun xianxiaHairFloatLayerAssetsAreFullCanvasRgbaPngs() {
        val characterPackage = StaticPngCharacterPackage.XianxiaFemale

        characterPackage.hairLayers.values.forEach { layer ->
            val asset = File("src/main/assets/${layer.assetPath}")

            assertTrue("Missing hair layer asset: ${asset.path}", asset.isFile)
            val header = asset.readPngHeader()

            assertEquals(characterPackage.sourceWidthPx, header.width)
            assertEquals(characterPackage.sourceHeightPx, header.height)
            assertEquals(PNG_COLOR_TYPE_RGBA, header.colorType)
        }
    }

    @Test
    fun xianxiaStaticPngPackageDefinesChestPieceLayer() {
        val characterPackage = StaticPngCharacterPackage.XianxiaFemale
        val chestPiece = characterPackage.chestPiece

        assertEquals(
            "characters/xianxia_female/static/xianxia_female__chest__piece__v1.png",
            chestPiece.assetPath
        )
        assertEquals(350, chestPiece.bounds.x)
        assertEquals(355, chestPiece.bounds.y)
        assertEquals(325, chestPiece.bounds.width)
        assertEquals(315, chestPiece.bounds.height)
    }

    @Test
    fun xianxiaChestPieceAssetIsFullCanvasRgbaPng() {
        val characterPackage = StaticPngCharacterPackage.XianxiaFemale
        val asset = File("src/main/assets/${characterPackage.chestPiece.assetPath}")

        assertTrue("Missing chest piece asset: ${asset.path}", asset.isFile)
        val header = asset.readPngHeader()

        assertEquals(characterPackage.sourceWidthPx, header.width)
        assertEquals(characterPackage.sourceHeightPx, header.height)
        assertEquals(PNG_COLOR_TYPE_RGBA, header.colorType)
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
