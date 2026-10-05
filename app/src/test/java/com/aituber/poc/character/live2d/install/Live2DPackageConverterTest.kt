package com.aituber.poc.character.live2d.install

import java.io.File
import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class Live2DPackageConverterTest {

    private val converter = Live2DPackageConverter(clock = { 1_700_000_000_000L })

    private fun convert(zip: File, id: String = "store_test", name: String? = null): Pair<File, Live2DPackageConverter.Result> {
        val out = TestPackages.tempDir("converted")
        val result = PackageArchive.open(zip).use { archive ->
            converter.convert(archive, out, Live2DPackageConverter.Request(id, name))
        }
        return out to result
    }

    private fun readJson(file: File): JsonValue.Obj = MiniJson.parseObject(file.readBytes())

    @Test
    fun boothStylePackageIsNormalized() {
        val (out, result) = convert(TestPackages.boothStylePackage(), name = "ゴスロリ少女")
        val model = File(out, "model")

        // Only referenced files, renamed to ASCII.
        assertTrue(File(model, "model.moc3").isFile)
        assertTrue(File(model, "textures/texture_00.png").isFile)
        assertTrue(File(model, "model.physics3.json").isFile)
        assertTrue(File(model, "model.cdi3.json").isFile)
        assertTrue(File(model, "motions/motion_000.motion3.json").isFile)
        assertFalse(out.walkTopDown().any { it.name.endsWith(".psd") })

        // model3.json points at the new names; "idle" became "Idle"; the missing Tap motion was dropped.
        val model3 = readJson(File(model, "model.model3.json"))
        val refs = model3.obj("FileReferences")!!
        assertEquals("model.moc3", refs.str("Moc"))
        assertEquals("textures/texture_00.png", (refs.arr("Textures")!!.items[0] as JsonValue.Str).value)
        val motions = refs.obj("Motions")!!
        assertEquals(setOf("Idle"), motions.fields.keys)
        val idle = motions.arr("Idle")!!.items.single() as JsonValue.Obj
        assertEquals("motions/motion_000.motion3.json", idle.str("File"))
        assertEquals(0.5, idle.double("FadeInTime")!!, 0.0)
        assertTrue(model3.arr("Groups") != null)

        // Manifest.
        val manifest = result.manifest
        assertEquals("store_test", manifest.id)
        assertEquals("ゴスロリ少女", manifest.displayName)
        assertTrue(manifest.capabilities.idleMotion)
        assertTrue(manifest.capabilities.physics)
        assertTrue(manifest.conversion.lipSyncFound)
        assertEquals(3, manifest.conversion.mocVersion)
        assertTrue(manifest.parameters.mouthOpen.startsWith("ParamMouthOpenY"))
        assertTrue(manifest.parameters.eyeLeftOpen.startsWith("ParamEyeLOpen"))
        assertTrue(manifest.parameters.eyeRightOpen.startsWith("ParamEyeROpen"))
        assertTrue(manifest.parameters.headX!!.startsWith("ParamAngleX"))
        assertTrue(result.warnings.any { "missing.motion3.json" in it })

        // Creator's license file is kept for credit.
        assertTrue(File(out, "docs/creator_doc_00.txt").isFile)

        // Manifest round-trips and becomes an APP_PRIVATE profile.
        val reread = CharacterManifest.fromJson(readJson(File(out, CharacterManifest.FILE_NAME)))
        assertEquals(manifest.parameters, reread.parameters)
        val profile = reread.toProfile()
        assertEquals("store_test/model", profile.assetDir)
        assertEquals("model.model3.json", profile.model3File)
    }

    @Test
    fun motionMetaCountsAreRepaired() {
        val (out, _) = convert(TestPackages.boothStylePackage())
        val meta = readJson(File(out, "model/motions/motion_000.motion3.json")).obj("Meta")!!
        assertEquals(2, meta.int("CurveCount"))
        assertEquals(4, meta.int("TotalSegmentCount"))
        assertEquals(8, meta.int("TotalPointCount"))
        assertEquals(0, meta.int("UserDataCount"))
        // Values the SDK does not size arrays from are untouched.
        assertTrue(MiniJson.write(meta).contains("\"Duration\": 2.0"))
    }

    @Test
    fun physicsMetaCountsAreRepaired() {
        val (out, result) = convert(TestPackages.boothStylePackage())
        val meta = readJson(File(out, "model/model.physics3.json")).obj("Meta")!!
        assertEquals(1, meta.int("PhysicsSettingCount"))
        assertEquals(1, meta.int("TotalInputCount"))
        assertEquals(2, meta.int("TotalOutputCount"))
        assertEquals(3, meta.int("VertexCount"))
        assertTrue(result.warnings.any { "物理" in it })
    }

    @Test
    fun exponentNumbersAreWrittenAsPlainDecimals() {
        val motion = String(TestPackages.MOTION_WITH_BAD_META).replace("[0, 0, 2, 1, 1, 3, 2, 0]", "[0, 0, 2, 1, 1e-05, 3, 2, 2.5E2]")
        val zip = TestPackages.zip(
            linkedMapOf(
                "m.model3.json" to TestPackages.json("""{"Version":3,"FileReferences":{"Moc":"m.moc3","Textures":["t.png"],"Motions":{"Idle":[{"File":"a.motion3.json"}]}}}"""),
                "m.moc3" to TestPackages.moc(),
                "t.png" to TestPackages.png(),
                "a.motion3.json" to motion.toByteArray()
            )
        )
        val (out, _) = convert(zip)
        val text = File(out, "model/motions/motion_000.motion3.json").readText()
        assertTrue(text, text.contains("0.00001,\n"))
        assertTrue(text, text.contains("250\n"))
        assertFalse(text.contains("e-05"))
    }

    @Test
    fun physicsWithOutOfRangeVertexIndexIsDisabled() {
        val badPhysics = String(TestPackages.PHYSICS_WITH_BAD_META).replace("\"VertexIndex\": 2", "\"VertexIndex\": 7")
        val zip = TestPackages.zip(
            linkedMapOf(
                "m.model3.json" to TestPackages.json("""{"Version":3,"FileReferences":{"Moc":"m.moc3","Textures":["t.png"],"Physics":"p.physics3.json"}}"""),
                "m.moc3" to TestPackages.moc(),
                "t.png" to TestPackages.png(),
                "p.physics3.json" to badPhysics.toByteArray()
            )
        )
        val (out, result) = convert(zip)
        assertFalse(File(out, "model/model.physics3.json").exists())
        assertFalse(result.manifest.capabilities.physics)
        assertNull(readJson(File(out, "model/model.model3.json")).obj("FileReferences")!!.str("Physics"))
    }

    @Test
    fun physicsWithoutInputArrayIsDisabled() {
        val noInput = String(TestPackages.PHYSICS_WITH_BAD_META).replace(Regex("\"Input\": \\[.*?\\],", RegexOption.DOT_MATCHES_ALL), "")
        assertFalse(noInput.contains("\"Input\""))
        val zip = TestPackages.zip(
            linkedMapOf(
                "m.model3.json" to TestPackages.json("""{"Version":3,"FileReferences":{"Moc":"m.moc3","Textures":["t.png"],"Physics":"p.physics3.json"}}"""),
                "m.moc3" to TestPackages.moc(),
                "t.png" to TestPackages.png(),
                "p.physics3.json" to noInput.toByteArray()
            )
        )
        val (_, result) = convert(zip)
        assertFalse(result.manifest.capabilities.physics)
    }

    @Test
    fun brokenMotionIsDroppedNotFatal() {
        val zip = TestPackages.zip(
            linkedMapOf(
                "m.model3.json" to TestPackages.json("""{"Version":3,"FileReferences":{"Moc":"m.moc3","Textures":["t.png"],"Motions":{"Idle":[{"File":"a.motion3.json"},{"File":"b.motion3.json"}]}}}"""),
                "m.moc3" to TestPackages.moc(),
                "t.png" to TestPackages.png(),
                "a.motion3.json" to TestPackages.json("""{"Meta":{},"Curves":[{"Target":"Parameter","Id":"X","Segments":[0,0,9,1,1]}]}"""),
                "b.motion3.json" to TestPackages.MOTION_WITH_BAD_META
            )
        )
        val (out, result) = convert(zip)
        val idle = readJson(File(out, "model/model.model3.json")).obj("FileReferences")!!.obj("Motions")!!.arr("Idle")!!
        assertEquals(1, idle.items.size)
        assertTrue(result.manifest.capabilities.idleMotion)
        assertTrue(result.warnings.any { "a.motion3.json" in it })
    }

    @Test
    fun vtubeStudioMappingWinsAndSuppliesIdleAnimation() {
        val zip = TestPackages.zip(
            linkedMapOf(
                "Kitsune/kitsune.model3.json" to TestPackages.json("""{"Version":3,"FileReferences":{"Moc":"kitsune.moc3","Textures":["kitsune.4096/texture_00.png"]}}"""),
                "Kitsune/kitsune.moc3" to TestPackages.moc(parameterIds = listOf("MouthOpenCustom", "PARAM_EYE_L_OPEN", "PARAM_EYE_R_OPEN")),
                "Kitsune/kitsune.4096/texture_00.png" to TestPackages.png(),
                "Kitsune/idle_loop.motion3.json" to TestPackages.MOTION_WITH_BAD_META,
                "Kitsune/kitsune.vtube.json" to TestPackages.json(
                    """
                    {"FileReferences": {"IdleAnimation": "idle_loop.motion3.json"},
                     "ParameterSettings": [
                       {"Input": "MouthOpen", "OutputLive2D": "MouthOpenCustom"},
                       {"Input": "FaceAngleX", "OutputLive2D": "HeadTurn"}
                     ]}
                    """
                )
            )
        )
        val (_, result) = convert(zip)
        val params = result.manifest.parameters
        assertTrue(params.mouthOpen, params.mouthOpen.startsWith("MouthOpenCustom|"))
        assertTrue(params.headX!!.startsWith("HeadTurn|"))
        // Cubism 2-style ids found inside the moc3 come before the Cubism 3 defaults.
        assertTrue(params.eyeLeftOpen, params.eyeLeftOpen.startsWith("PARAM_EYE_L_OPEN"))
        assertTrue(result.manifest.capabilities.idleMotion)
        assertTrue(result.manifest.conversion.lipSyncFound)
    }

    @Test
    fun missingMouthParameterStillInstallsWithWarning() {
        val zip = TestPackages.zip(
            linkedMapOf(
                "m.model3.json" to TestPackages.json("""{"Version":3,"FileReferences":{"Moc":"m.moc3","Textures":["t.png"]}}"""),
                "m.moc3" to TestPackages.moc(parameterIds = listOf("SomethingElse")),
                "t.png" to TestPackages.png()
            )
        )
        val (_, result) = convert(zip)
        assertFalse(result.manifest.conversion.lipSyncFound)
        assertTrue(result.warnings.any { "嘴巴" in it })
    }

    @Test
    fun choosesTheCompleteModelWhenSeveralExist() {
        val zip = TestPackages.zip(
            linkedMapOf(
                "broken/b.model3.json" to TestPackages.json("""{"Version":3,"FileReferences":{"Moc":"b.moc3","Textures":["missing.png"]}}"""),
                "broken/b.moc3" to TestPackages.moc(),
                "good/deep/g.model3.json" to TestPackages.json("""{"Version":3,"FileReferences":{"Moc":"g.moc3","Textures":["g.png"]}}"""),
                "good/deep/g.moc3" to TestPackages.moc(),
                "good/deep/g.png" to TestPackages.png()
            )
        )
        val (_, result) = convert(zip)
        assertEquals("good/deep/g.model3.json", result.manifest.conversion.originalModel3Path)
        assertEquals("g", result.manifest.displayName)
    }

    @Test
    fun gbkPackageResolvesReferences() {
        val zip = TestPackages.zip(
            linkedMapOf(
                "小狐狸/小狐狸.model3.json" to TestPackages.json("""{"Version":3,"FileReferences":{"Moc":"小狐狸.moc3","Textures":["贴图/纹理_00.png"]}}"""),
                "小狐狸/小狐狸.moc3" to TestPackages.moc(),
                "小狐狸/贴图/纹理_00.png" to TestPackages.png()
            ),
            Charset.forName("GBK")
        )
        val (out, _) = convert(zip)
        assertTrue(File(out, "model/model.moc3").isFile)
        assertTrue(File(out, "model/textures/texture_00.png").isFile)
    }

    @Test
    fun unsupportedFormatsAreIdentified() {
        assertError(PackageError.CUBISM2_MODEL, TestPackages.zip(mapOf("old/model.json" to "{}".toByteArray(), "old/model.moc" to ByteArray(10))))
        assertError(PackageError.EDITOR_SOURCE_ONLY, TestPackages.zip(mapOf("garnet.cmo3" to ByteArray(10), "garnet.psd" to ByteArray(10))))
        assertError(PackageError.ENCRYPTED_LPK, TestPackages.zip(mapOf("model.lpk" to ByteArray(10))))
        assertError(PackageError.OTHER_FORMAT, TestPackages.zip(mapOf("avatar.vrm" to ByteArray(10))))
        assertError(PackageError.NO_MODEL, TestPackages.zip(mapOf("readme.txt" to ByteArray(10))))
    }

    @Test
    fun requiredFilesAreChecked() {
        fun pkg(moc: ByteArray?, png: ByteArray?, textures: String = "[\"t.png\"]") = TestPackages.zip(
            linkedMapOf<String, ByteArray>().apply {
                put("m.model3.json", TestPackages.json("""{"Version":3,"FileReferences":{"Moc":"m.moc3","Textures":$textures}}"""))
                moc?.let { put("m.moc3", it) }
                png?.let { put("t.png", it) }
            }
        )
        assertError(PackageError.MOC_MISSING, pkg(null, TestPackages.png()))
        assertError(PackageError.MOC_INVALID, pkg("not a moc".toByteArray() + ByteArray(100), TestPackages.png()))
        assertError(PackageError.MOC_TOO_NEW, pkg(TestPackages.moc(version = 9), TestPackages.png()))
        assertError(PackageError.TEXTURE_MISSING, pkg(TestPackages.moc(), null))
        assertError(PackageError.TEXTURE_MISSING, pkg(TestPackages.moc(), TestPackages.png(), "[]"))
        assertError(PackageError.TEXTURE_INVALID, pkg(TestPackages.moc(), "jpeg?".toByteArray() + ByteArray(40)))
        assertError(PackageError.TEXTURE_TOO_LARGE, pkg(TestPackages.moc(), TestPackages.png(9000, 16)))
        assertError(PackageError.MODEL_JSON_INVALID, TestPackages.zip(mapOf("m.model3.json" to "{\"Version\":3,".toByteArray())))
    }

    @Test
    fun referencesCannotEscapeThePackage() {
        val zip = TestPackages.zip(
            linkedMapOf(
                "a/m.model3.json" to TestPackages.json("""{"Version":3,"FileReferences":{"Moc":"../../../../data/m.moc3","Textures":["t.png"]}}"""),
                "a/t.png" to TestPackages.png()
            )
        )
        assertError(PackageError.MOC_MISSING, zip)
    }

    private fun assertError(expected: PackageError, zip: File) {
        try {
            convert(zip)
            fail("Expected $expected")
        } catch (e: PackageException) {
            assertEquals(e.message, expected, e.error)
        }
    }
}
