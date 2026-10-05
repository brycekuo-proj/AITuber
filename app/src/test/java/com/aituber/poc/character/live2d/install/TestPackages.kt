package com.aituber.poc.character.live2d.install

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/** Builds synthetic creator packages for converter tests. */
internal object TestPackages {

    fun tempDir(prefix: String): File = Files.createTempDirectory(prefix).toFile()

    /** Writes a ZIP. With a non-UTF-8 [charset], names are stored without the UTF-8 flag (like Japanese Windows zips). */
    fun zip(entries: Map<String, ByteArray>, charset: Charset = Charsets.UTF_8): File {
        val file = File.createTempFile("package", ".zip")
        ZipOutputStream(file.outputStream(), charset).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    /** Header "MOC3" + version byte, padding, and parameter ids in zero-padded 64-byte slots. */
    fun moc(version: Int = 3, parameterIds: List<String> = listOf("ParamAngleX", "ParamMouthOpenY", "ParamEyeLOpen", "ParamEyeROpen", "ParamBreath")): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("MOC3".toByteArray())
        out.write(version)
        out.write(ByteArray(128 - 5))
        parameterIds.forEach { id ->
            val slot = ByteArray(64)
            val bytes = id.toByteArray()
            System.arraycopy(bytes, 0, slot, 0, bytes.size)
            out.write(slot)
        }
        return out.toByteArray()
    }

    fun png(width: Int = 64, height: Int = 64): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    fun json(text: String): ByteArray = text.trimIndent().toByteArray(Charsets.UTF_8)

    /** A valid motion whose Meta counts are deliberately wrong. */
    val MOTION_WITH_BAD_META = json(
        """
        {
          "Version": 3,
          "Meta": {"Duration": 2.0, "Fps": 30.0, "Loop": true, "CurveCount": 99, "TotalSegmentCount": 1, "TotalPointCount": 1, "UserDataCount": 0, "TotalUserDataSize": 0},
          "Curves": [
            {"Target": "Parameter", "Id": "ParamAngleX", "Segments": [0, 0, 0, 1, 10, 1, 1.2, 10, 1.6, 0, 2, 0]},
            {"Target": "Parameter", "Id": "ParamBreath", "Segments": [0, 0, 2, 1, 1, 3, 2, 0]}
          ]
        }
        """
    )

    /** One setting, one input, two outputs, three vertices; Meta counts wrong. */
    val PHYSICS_WITH_BAD_META = json(
        """
        {
          "Version": 3,
          "Meta": {"PhysicsSettingCount": 5, "TotalInputCount": 1, "TotalOutputCount": 1, "VertexCount": 1, "EffectiveForces": {"Gravity": {"X": 0, "Y": -1}, "Wind": {"X": 0, "Y": 0}}},
          "PhysicsSettings": [
            {
              "Id": "PhysicsSetting1",
              "Input": [{"Source": {"Target": "Parameter", "Id": "ParamAngleX"}, "Weight": 60, "Type": "X", "Reflect": false}],
              "Output": [
                {"Destination": {"Target": "Parameter", "Id": "ParamHairFront"}, "VertexIndex": 1, "Scale": 1, "Weight": 100, "Type": "Angle", "Reflect": false},
                {"Destination": {"Target": "Parameter", "Id": "ParamHairBack"}, "VertexIndex": 2, "Scale": 1, "Weight": 100, "Type": "Angle", "Reflect": false}
              ],
              "Vertices": [
                {"Position": {"X": 0, "Y": 0}, "Mobility": 1, "Delay": 1, "Acceleration": 1, "Radius": 0},
                {"Position": {"X": 0, "Y": 3}, "Mobility": 0.95, "Delay": 0.9, "Acceleration": 1.5, "Radius": 3},
                {"Position": {"X": 0, "Y": 6}, "Mobility": 0.95, "Delay": 0.9, "Acceleration": 1.5, "Radius": 3}
              ],
              "Normalization": {"Position": {"Minimum": -10, "Default": 0, "Maximum": 10}, "Angle": {"Minimum": -10, "Default": 0, "Maximum": 10}}
            }
          ]
        }
        """
    )

    /**
     * A typical BOOTH package: Japanese folder and file names, Windows
     * backslashes and wrong letter case in model3.json, a lower-case "idle"
     * group, broken Meta counts, plus junk (PSD, __MACOSX).
     */
    fun boothStylePackage(charset: Charset = Charset.forName("Shift_JIS")): File {
        val root = "ゴスロリ少女_vts/"
        val model3 = json(
            """
            {
              "Version": 3,
              "FileReferences": {
                "Moc": "ゴスロリ少女.moc3",
                "Textures": ["ゴスロリ少女.2048\\Texture_00.PNG"],
                "Physics": "ゴスロリ少女.physics3.json",
                "DisplayInfo": "ゴスロリ少女.cdi3.json",
                "Motions": {
                  "idle": [{"File": "motions/待機.motion3.json", "FadeInTime": 0.5}],
                  "Tap": [{"File": "motions/missing.motion3.json"}]
                }
              },
              "Groups": [
                {"Target": "Parameter", "Name": "LipSync", "Ids": ["ParamMouthOpenY"]},
                {"Target": "Parameter", "Name": "EyeBlink", "Ids": ["ParamEyeLOpen", "ParamEyeROpen"]}
              ]
            }
            """
        )
        val cdi = json(
            """
            {"Version": 3, "Parameters": [{"Id": "ParamAngleX", "GroupId": "", "Name": "角度 X"}, {"Id": "ParamMouthOpenY", "GroupId": "", "Name": "口 開閉"}]}
            """
        )
        return zip(
            linkedMapOf(
                root + "ゴスロリ少女.model3.json" to model3,
                root + "ゴスロリ少女.moc3" to moc(),
                root + "ゴスロリ少女.2048/texture_00.png" to png(128, 128),
                root + "ゴスロリ少女.physics3.json" to PHYSICS_WITH_BAD_META,
                root + "ゴスロリ少女.cdi3.json" to cdi,
                root + "motions/待機.motion3.json" to MOTION_WITH_BAD_META,
                root + "利用規約.txt" to "商用利用可。再配布禁止。".toByteArray(charset),
                "ゴスロリ少女.psd" to ByteArray(10),
                "__MACOSX/._x" to ByteArray(3)
            ),
            charset
        )
    }
}
