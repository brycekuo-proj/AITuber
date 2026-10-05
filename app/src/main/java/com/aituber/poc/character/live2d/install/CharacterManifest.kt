package com.aituber.poc.character.live2d.install

import com.aituber.poc.character.live2d.Live2DCharacterCapabilities
import com.aituber.poc.character.live2d.Live2DCharacterProfile
import com.aituber.poc.character.live2d.Live2DModelLayout
import com.aituber.poc.character.live2d.Live2DModelSource
import com.aituber.poc.character.live2d.Live2DMouthTuning
import com.aituber.poc.character.live2d.Live2DParameterMapping

/**
 * The unified AITuber character format written by the converter:
 *
 *   filesDir/characters/<id>/aituber_character.json   (this manifest)
 *   filesDir/characters/<id>/model/model.model3.json  (rewritten, ASCII file names)
 *   filesDir/characters/<id>/model/...                (moc3, textures/, motions/, ...)
 *   filesDir/characters/<id>/docs/                    (creator readme / license files, if any)
 *
 * Parameter ids may be "A|B|C": the native runtime uses the first id that
 * exists in the model.
 */
data class CharacterManifest(
    val schemaVersion: Int = SCHEMA_VERSION,
    val id: String,
    val displayName: String,
    val modelDir: String = MODEL_DIR,
    val model3File: String = MODEL3_FILE,
    val parameters: Live2DParameterMapping,
    val mouthTuning: Live2DMouthTuning = Live2DMouthTuning(),
    val capabilities: Live2DCharacterCapabilities,
    val layout: Live2DModelLayout = Live2DModelLayout(),
    val source: CharacterSource = CharacterSource(),
    val conversion: ConversionInfo = ConversionInfo()
) {
    fun toProfile(): Live2DCharacterProfile = Live2DCharacterProfile(
        id = id,
        displayName = displayName,
        assetDir = "$id/$modelDir",
        model3File = model3File,
        modelSource = Live2DModelSource.APP_PRIVATE,
        parameterMapping = parameters,
        mouthTuning = mouthTuning,
        capabilities = capabilities,
        layout = layout
    )

    fun toJson(): JsonValue.Obj = JsonValue.Obj().apply {
        this["SchemaVersion"] = JsonValue.of(schemaVersion)
        this["Id"] = JsonValue.of(id)
        this["DisplayName"] = JsonValue.of(displayName)
        this["Renderer"] = JsonValue.of("live2d")
        this["ModelDir"] = JsonValue.of(modelDir)
        this["Model3File"] = JsonValue.of(model3File)
        this["Parameters"] = JsonValue.Obj().apply {
            this["MouthOpen"] = JsonValue.of(parameters.mouthOpen)
            this["EyeLeftOpen"] = JsonValue.of(parameters.eyeLeftOpen)
            this["EyeRightOpen"] = JsonValue.of(parameters.eyeRightOpen)
            this["Breath"] = JsonValue.of(parameters.breath)
            parameters.headX?.let { this["HeadX"] = JsonValue.of(it) }
            parameters.headY?.let { this["HeadY"] = JsonValue.of(it) }
            parameters.headZ?.let { this["HeadZ"] = JsonValue.of(it) }
        }
        this["MouthTuning"] = JsonValue.Obj().apply {
            this["OutputMin"] = JsonValue.of(mouthTuning.outputMin.toDouble())
            this["OutputMax"] = JsonValue.of(mouthTuning.outputMax.toDouble())
            this["Exponent"] = JsonValue.of(mouthTuning.exponent.toDouble())
        }
        this["Capabilities"] = JsonValue.Obj().apply {
            this["IdleMotion"] = JsonValue.of(capabilities.idleMotion)
            this["Physics"] = JsonValue.of(capabilities.physics)
            this["Pose"] = JsonValue.of(capabilities.pose)
            this["Expressions"] = JsonValue.of(capabilities.expressions)
            this["LipSync"] = JsonValue.of(conversion.lipSyncFound)
        }
        this["Layout"] = JsonValue.Obj().apply {
            this["Height"] = JsonValue.of(layout.height.toDouble())
            this["OffsetY"] = JsonValue.of(layout.offsetY.toDouble())
        }
        this["Source"] = JsonValue.Obj().apply {
            this["StoreId"] = JsonValue.of(source.storeId)
            this["SourceUrl"] = JsonValue.of(source.sourceUrl)
            this["License"] = JsonValue.of(source.license)
            this["Credit"] = JsonValue.of(source.credit)
            this["OriginalFileName"] = JsonValue.of(source.originalFileName)
        }
        this["Conversion"] = JsonValue.Obj().apply {
            this["ConverterVersion"] = JsonValue.of(conversion.converterVersion)
            this["InstalledAtMs"] = JsonValue.Num(conversion.installedAtMs.toString())
            this["MocVersion"] = JsonValue.of(conversion.mocVersion)
            this["OriginalModel3Path"] = JsonValue.of(conversion.originalModel3Path)
            this["NameCharset"] = JsonValue.of(conversion.nameCharset)
            this["LipSyncFound"] = JsonValue.of(conversion.lipSyncFound)
            this["Warnings"] = JsonValue.Arr(conversion.warnings.map { JsonValue.of(it) }.toMutableList())
        }
    }

    companion object {
        const val SCHEMA_VERSION = 1
        const val FILE_NAME = "aituber_character.json"
        const val MODEL_DIR = "model"
        const val MODEL3_FILE = "model.model3.json"

        private val ID_PATTERN = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")

        fun isValidId(id: String): Boolean = ID_PATTERN.matches(id)

        fun fromJson(root: JsonValue.Obj): CharacterManifest {
            val schema = root.int("SchemaVersion") ?: error("SchemaVersion missing")
            require(schema <= SCHEMA_VERSION) { "Manifest schema $schema is newer than supported $SCHEMA_VERSION" }
            val id = root.str("Id") ?: error("Id missing")
            require(isValidId(id)) { "Invalid character id: $id" }
            val modelDir = root.str("ModelDir") ?: MODEL_DIR
            val model3File = root.str("Model3File") ?: MODEL3_FILE
            require(PackageArchive.normalizePath(modelDir) == modelDir) { "Invalid ModelDir" }
            require(PackageArchive.normalizePath(model3File) == model3File && '/' !in model3File) { "Invalid Model3File" }
            val params = root.obj("Parameters") ?: error("Parameters missing")
            val tuning = root.obj("MouthTuning")
            val caps = root.obj("Capabilities")
            val layout = root.obj("Layout")
            val source = root.obj("Source")
            val conversion = root.obj("Conversion")
            return CharacterManifest(
                schemaVersion = schema,
                id = id,
                displayName = root.str("DisplayName")?.takeIf { it.isNotBlank() } ?: id,
                modelDir = modelDir,
                model3File = model3File,
                parameters = Live2DParameterMapping(
                    mouthOpen = params.str("MouthOpen") ?: ParameterMapper.DEFAULT_MOUTH,
                    eyeLeftOpen = params.str("EyeLeftOpen") ?: ParameterMapper.DEFAULT_EYE_L,
                    eyeRightOpen = params.str("EyeRightOpen") ?: ParameterMapper.DEFAULT_EYE_R,
                    breath = params.str("Breath") ?: ParameterMapper.DEFAULT_BREATH,
                    headX = params.str("HeadX"),
                    headY = params.str("HeadY"),
                    headZ = params.str("HeadZ")
                ),
                mouthTuning = Live2DMouthTuning(
                    outputMin = tuning?.double("OutputMin")?.toFloat() ?: 0f,
                    outputMax = tuning?.double("OutputMax")?.toFloat() ?: 1f,
                    exponent = tuning?.double("Exponent")?.toFloat() ?: 1f
                ),
                capabilities = Live2DCharacterCapabilities(
                    idleMotion = caps?.bool("IdleMotion") ?: false,
                    physics = caps?.bool("Physics") ?: false,
                    pose = caps?.bool("Pose") ?: false,
                    expressions = caps?.bool("Expressions") ?: false
                ),
                layout = Live2DModelLayout(
                    height = layout?.double("Height")?.toFloat() ?: Live2DModelLayout.DEFAULT_HEIGHT,
                    offsetY = layout?.double("OffsetY")?.toFloat() ?: Live2DModelLayout.DEFAULT_OFFSET_Y
                ),
                source = CharacterSource(
                    storeId = source?.str("StoreId") ?: "",
                    sourceUrl = source?.str("SourceUrl") ?: "",
                    license = source?.str("License") ?: "",
                    credit = source?.str("Credit") ?: "",
                    originalFileName = source?.str("OriginalFileName") ?: ""
                ),
                conversion = ConversionInfo(
                    converterVersion = conversion?.int("ConverterVersion") ?: 0,
                    installedAtMs = conversion?.double("InstalledAtMs")?.toLong() ?: 0L,
                    mocVersion = conversion?.int("MocVersion") ?: 0,
                    originalModel3Path = conversion?.str("OriginalModel3Path") ?: "",
                    nameCharset = conversion?.str("NameCharset") ?: "",
                    lipSyncFound = conversion?.bool("LipSyncFound") ?: false,
                    warnings = conversion?.arr("Warnings")?.items
                        ?.mapNotNull { (it as? JsonValue.Str)?.value } ?: emptyList()
                )
            )
        }
    }
}

data class CharacterSource(
    val storeId: String = "",
    val sourceUrl: String = "",
    val license: String = "",
    val credit: String = "",
    val originalFileName: String = ""
)

data class ConversionInfo(
    val converterVersion: Int = Live2DPackageConverter.CONVERTER_VERSION,
    val installedAtMs: Long = 0L,
    val mocVersion: Int = 0,
    val originalModel3Path: String = "",
    val nameCharset: String = "",
    /** False when no mouth parameter could be identified; the model still installs. */
    val lipSyncFound: Boolean = false,
    val warnings: List<String> = emptyList()
)
