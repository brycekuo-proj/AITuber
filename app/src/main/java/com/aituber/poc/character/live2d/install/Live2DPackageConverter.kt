package com.aituber.poc.character.live2d.install

import com.aituber.poc.character.live2d.Live2DCharacterCapabilities
import java.io.File

/**
 * Converts a creator's Live2D package (ZIP) into AITuber's unified character
 * format (see [CharacterManifest]).
 *
 * What "conversion" means here: creator packages are already Cubism 3+
 * runtime files, but every author lays them out differently. The converter
 *  - picks the model3.json to use (packages often contain several),
 *  - resolves every reference (backslashes, wrong case, Japanese names),
 *  - validates the moc3 header, PNG textures and every JSON file,
 *  - repairs motion3 / physics3 "Meta" counts, which the Cubism SDK trusts
 *    for memory allocation (a wrong count is a native crash),
 *  - renames the "idle" motion group to the "Idle" group the runtime plays,
 *  - copies only referenced files, renamed to plain ASCII names, and
 *    rewrites model3.json to match,
 *  - maps mouth / eye / breath / head parameters ([ParameterMapper]),
 *  - writes the manifest.
 *
 * Pure JVM: no Android APIs, so it runs in unit tests.
 */
class Live2DPackageConverter(
    private val limits: PackageLimits = PackageLimits(),
    private val clock: () -> Long = System::currentTimeMillis
) {
    data class Request(
        val characterId: String,
        val displayName: String? = null,
        val source: CharacterSource = CharacterSource()
    )

    data class Result(val manifest: CharacterManifest, val warnings: List<String>)

    fun convert(archive: PackageArchive, outputDir: File, request: Request): Result {
        require(CharacterManifest.isValidId(request.characterId)) { "Invalid id ${request.characterId}" }
        val warnings = ArrayList<String>()
        val model3Entry = chooseModel3(archive, warnings)
        val baseDir = model3Entry.directory
        val model3 = try {
            MiniJson.parseObject(archive.readBytes(model3Entry, limits.maxJsonBytes))
        } catch (e: JsonParseException) {
            throw PackageException(PackageError.MODEL_JSON_INVALID, "${model3Entry.path}: ${e.message}")
        }
        val refs = model3.obj("FileReferences")
            ?: throw PackageException(PackageError.MODEL_JSON_INVALID, "FileReferences missing")

        val modelOut = File(outputDir, CharacterManifest.MODEL_DIR)
        if (!modelOut.mkdirs() && !modelOut.isDirectory) {
            throw PackageException(PackageError.STORAGE_FAILED, modelOut.path)
        }
        val writer = OutputWriter(modelOut, limits)
        val newRefs = JsonValue.Obj()

        // --- moc3 (required) ---
        val mocEntry = refs.str("Moc")?.let { findRef(archive, baseDir, it) }
            ?: throw PackageException(PackageError.MOC_MISSING, refs.str("Moc") ?: "(none)")
        val moc = archive.readBytes(mocEntry)
        val mocVersion = checkMoc(moc)
        writer.write("model.moc3", moc)
        newRefs["Moc"] = JsonValue.of("model.moc3")

        // --- textures (required) ---
        val textureRefs = refs.arr("Textures")?.items?.map { (it as? JsonValue.Str)?.value }.orEmpty()
        if (textureRefs.isEmpty()) throw PackageException(PackageError.TEXTURE_MISSING, "Textures empty")
        val newTextures = JsonValue.Arr()
        textureRefs.forEachIndexed { index, ref ->
            if (ref.isNullOrBlank()) throw PackageException(PackageError.TEXTURE_MISSING, "texture #$index blank")
            val entry = findRef(archive, baseDir, ref)
                ?: throw PackageException(PackageError.TEXTURE_MISSING, ref)
            val png = archive.readBytes(entry)
            val (w, h) = PngInfo.size(png) ?: throw PackageException(PackageError.TEXTURE_INVALID, ref)
            if (w > MAX_TEXTURE_EDGE || h > MAX_TEXTURE_EDGE) {
                throw PackageException(PackageError.TEXTURE_TOO_LARGE, "$ref ${w}x$h")
            }
            if (w > 4096 || h > 4096) warnings += "貼圖 $ref 為 ${w}x$h，低階手機會自動縮小顯示"
            if (!isPowerOfTwo(w) || !isPowerOfTwo(h)) warnings += "貼圖 $ref 尺寸不是 2 的次方（${w}x$h）"
            val name = "textures/texture_%02d.png".format(index)
            writer.write(name, png)
            newTextures.items += JsonValue.of(name)
        }
        newRefs["Textures"] = newTextures

        val knownIds = HashSet<String>()

        // --- physics ---
        var hasPhysics = false
        refs.str("Physics")?.let { ref ->
            val json = readOptionalJson(archive, baseDir, ref, "物理", warnings) ?: return@let
            if (PhysicsRepair.repair(json, warnings)) {
                PhysicsRepair.collectIds(json, knownIds)
                writer.writeJson("model.physics3.json", json)
                newRefs["Physics"] = JsonValue.of("model.physics3.json")
                hasPhysics = true
            } else {
                warnings += "物理設定 $ref 結構錯誤，已停用物理"
            }
        }

        // --- pose ---
        var hasPose = false
        refs.str("Pose")?.let { ref ->
            val json = readOptionalJson(archive, baseDir, ref, "Pose", warnings) ?: return@let
            writer.writeJson("model.pose3.json", json)
            newRefs["Pose"] = JsonValue.of("model.pose3.json")
            hasPose = true
        }

        // --- display info (parameter names) ---
        refs.str("DisplayInfo")?.let { ref ->
            val json = readOptionalJson(archive, baseDir, ref, "DisplayInfo", warnings) ?: return@let
            json.arr("Parameters")?.items?.forEach { p -> (p as? JsonValue.Obj)?.str("Id")?.let(knownIds::add) }
            writer.writeJson("model.cdi3.json", json)
            newRefs["DisplayInfo"] = JsonValue.of("model.cdi3.json")
        }

        // --- user data ---
        refs.str("UserData")?.let { ref ->
            val json = readOptionalJson(archive, baseDir, ref, "UserData", warnings) ?: return@let
            writer.writeJson("model.userdata3.json", json)
            newRefs["UserData"] = JsonValue.of("model.userdata3.json")
        }

        // --- expressions ---
        val newExpressions = JsonValue.Arr()
        refs.arr("Expressions")?.items?.forEachIndexed { index, item ->
            val obj = item as? JsonValue.Obj ?: return@forEachIndexed
            val ref = obj.str("File") ?: return@forEachIndexed
            val json = readOptionalJson(archive, baseDir, ref, "表情", warnings) ?: return@forEachIndexed
            json.arr("Parameters")?.items?.forEach { p -> (p as? JsonValue.Obj)?.str("Id")?.let(knownIds::add) }
            val name = "expressions/expression_%02d.exp3.json".format(index)
            writer.writeJson(name, json)
            newExpressions.items += JsonValue.Obj().apply {
                this["Name"] = JsonValue.of(obj.str("Name") ?: "expression_$index")
                this["File"] = JsonValue.of(name)
            }
        }
        if (newExpressions.items.isNotEmpty()) newRefs["Expressions"] = newExpressions

        // --- VTube Studio settings (not part of model3.json) ---
        val vtube = readVtubeStudio(archive, model3Entry, warnings)

        // --- motions ---
        val motionGroups = LinkedHashMap<String, MutableList<Pair<String, JsonValue.Obj?>>>()
        refs.obj("Motions")?.fields?.forEach { (group, list) ->
            (list as? JsonValue.Arr)?.items?.forEach { item ->
                val obj = item as? JsonValue.Obj ?: return@forEach
                val file = obj.str("File") ?: return@forEach
                motionGroups.getOrPut(group) { ArrayList() } += file to obj
            }
        }
        val idleGroup = pickIdleGroup(motionGroups.keys)
        if (idleGroup == null) {
            vtube.idleAnimation?.let { motionGroups[IDLE_GROUP] = mutableListOf(it to null) }
        }
        val newMotions = JsonValue.Obj()
        var motionCounter = 0
        motionGroups.forEach { (group, files) ->
            val targetGroup = if (group == idleGroup) IDLE_GROUP else group
            val arr = (newMotions[targetGroup] as? JsonValue.Arr) ?: JsonValue.Arr().also { newMotions[targetGroup] = it }
            files.forEach { (ref, original) ->
                val json = readOptionalJson(archive, baseDir, ref, "動作", warnings) ?: return@forEach
                if (!MotionRepair.repair(json)) {
                    warnings += "動作 $ref 結構錯誤，已略過"
                    return@forEach
                }
                MotionRepair.collectIds(json, knownIds)
                val name = "motions/motion_%03d.motion3.json".format(motionCounter++)
                writer.writeJson(name, json)
                arr.items += JsonValue.Obj().apply {
                    this["File"] = JsonValue.of(name)
                    original?.get("FadeInTime")?.let { this["FadeInTime"] = it }
                    original?.get("FadeOutTime")?.let { this["FadeOutTime"] = it }
                }
            }
            if (arr.items.isEmpty()) newMotions.fields.remove(targetGroup)
        }
        if (newMotions.fields.isNotEmpty()) newRefs["Motions"] = newMotions
        val hasIdle = (newMotions[IDLE_GROUP] as? JsonValue.Arr)?.items?.isNotEmpty() == true

        // --- rewritten model3.json ---
        val newModel3 = JsonValue.Obj().apply {
            this["Version"] = model3["Version"] ?: JsonValue.of(3)
            this["FileReferences"] = newRefs
            model3["Groups"]?.let { this["Groups"] = it }
            model3["HitAreas"]?.let { this["HitAreas"] = it }
            model3["Layout"]?.let { this["Layout"] = it }
        }
        writer.writeJson(CharacterManifest.MODEL3_FILE, newModel3)

        // --- parameter mapping ---
        val groups = model3.arr("Groups")?.items?.mapNotNull { it as? JsonValue.Obj }.orEmpty()
        fun groupIds(name: String) = groups.filter { it.str("Name") == name }
            .flatMap { g -> g.arr("Ids")?.items?.mapNotNull { (it as? JsonValue.Str)?.value }.orEmpty() }
        ParameterMapper.STANDARD_IDS.filterTo(knownIds) { ParameterMapper.mocContainsId(moc, it) }
        val mapped = ParameterMapper.map(
            ParameterMapper.Evidence(
                knownIds = knownIds,
                lipSyncIds = groupIds("LipSync"),
                eyeBlinkIds = groupIds("EyeBlink"),
                vtubeStudio = vtube.parameters
            )
        )
        if (!mapped.lipSyncFound) warnings += "找不到嘴巴參數，角色可以顯示但可能不會動嘴"

        // --- creator docs (license / readme) ---
        copyCreatorDocs(archive, File(outputDir, "docs"))

        val displayName = request.displayName?.takeIf { it.isNotBlank() }
            ?: model3Entry.fileName.removeSuffix(".model3.json").ifBlank { request.characterId }
        val manifest = CharacterManifest(
            id = request.characterId,
            displayName = displayName,
            parameters = mapped.mapping,
            capabilities = Live2DCharacterCapabilities(
                idleMotion = hasIdle,
                physics = hasPhysics,
                pose = hasPose,
                expressions = newExpressions.items.isNotEmpty()
            ),
            source = request.source,
            conversion = ConversionInfo(
                installedAtMs = clock(),
                mocVersion = mocVersion,
                originalModel3Path = model3Entry.path,
                nameCharset = archive.nameCharset,
                lipSyncFound = mapped.lipSyncFound,
                warnings = warnings.toList()
            )
        )
        File(outputDir, CharacterManifest.FILE_NAME).writeText(MiniJson.write(manifest.toJson()))
        return Result(manifest, warnings.toList())
    }

    // ------------------------------------------------------------------

    private fun chooseModel3(archive: PackageArchive, warnings: MutableList<String>): PackageArchive.Entry {
        val candidates = archive.entries.filter { it.lowerPath.endsWith(".model3.json") }
        if (candidates.isEmpty()) throw PackageException(detectOtherFormat(archive))
        if (candidates.size == 1) return candidates[0]

        val scored = candidates.map { entry -> entry to scoreModel3(archive, entry) }
            .sortedWith(compareByDescending<Pair<PackageArchive.Entry, Int>> { it.second }
                .thenBy { it.first.path.count { c -> c == '/' } }
                .thenBy { it.first.path })
        val best = scored.first()
        if (best.second < 0) throw PackageException(PackageError.MOC_MISSING, "no usable model3.json")
        warnings += "壓縮檔內有 ${candidates.size} 個模型，已安裝 ${best.first.fileName}"
        return best.first
    }

    /** Higher is better; negative means unusable. */
    private fun scoreModel3(archive: PackageArchive, entry: PackageArchive.Entry): Int {
        val json = runCatching { MiniJson.parseObject(archive.readBytes(entry, limits.maxJsonBytes)) }
            .getOrNull() ?: return -1
        val refs = json.obj("FileReferences") ?: return -1
        val moc = refs.str("Moc")?.let { findRef(archive, entry.directory, it) } ?: return -1
        if (moc.size <= 0) return -1
        val textures = refs.arr("Textures")?.items.orEmpty()
        if (textures.isEmpty()) return -1
        val texturesFound = textures.count { t -> (t as? JsonValue.Str)?.value?.let { findRef(archive, entry.directory, it) } != null }
        if (texturesFound < textures.size) return -1
        var score = 100
        if (refs.str("Physics") != null) score += 5
        score += (refs.obj("Motions")?.fields?.size ?: 0).coerceAtMost(10)
        return score
    }

    private fun detectOtherFormat(archive: PackageArchive): PackageError {
        val names = archive.entries.map { it.lowerPath }
        return when {
            names.any { it.endsWith(".moc") } || names.any { it.endsWith(".model.json") } -> PackageError.CUBISM2_MODEL
            names.any { it.endsWith(".cmo3") || it.endsWith(".can3") } -> PackageError.EDITOR_SOURCE_ONLY
            names.any { it.endsWith(".lpk") } -> PackageError.ENCRYPTED_LPK
            names.any { it.endsWith(".vrm") || it.endsWith(".inp") || it.endsWith(".inx") || it.endsWith(".skel") } ->
                PackageError.OTHER_FORMAT
            else -> PackageError.NO_MODEL
        }
    }

    private fun findRef(archive: PackageArchive, baseDir: String, ref: String): PackageArchive.Entry? {
        val resolved = PackageArchive.resolve(baseDir, ref) ?: return null
        archive.find(resolved)?.let { return it }
        // Some packages reference files relative to the archive root instead.
        return PackageArchive.normalizePath(ref)?.let(archive::find)
    }

    private fun readOptionalJson(
        archive: PackageArchive,
        baseDir: String,
        ref: String,
        label: String,
        warnings: MutableList<String>
    ): JsonValue.Obj? {
        val entry = findRef(archive, baseDir, ref)
        if (entry == null) {
            warnings += "找不到$label 檔 $ref，已略過"
            return null
        }
        return try {
            MiniJson.parseObject(archive.readBytes(entry, limits.maxJsonBytes))
        } catch (e: JsonParseException) {
            warnings += "$label 檔 $ref 格式錯誤，已略過"
            null
        } catch (e: PackageException) {
            warnings += "$label 檔 $ref 太大，已略過"
            null
        }
    }

    /** Returns the moc3 version byte. */
    private fun checkMoc(moc: ByteArray): Int {
        if (moc.size < 64 ||
            moc[0] != 'M'.code.toByte() || moc[1] != 'O'.code.toByte() ||
            moc[2] != 'C'.code.toByte() || moc[3] != '3'.code.toByte()
        ) {
            throw PackageException(PackageError.MOC_INVALID, "bad header")
        }
        val version = moc[4].toInt() and 0xFF
        if (version == 0) throw PackageException(PackageError.MOC_INVALID, "version 0")
        if (version > MAX_SUPPORTED_MOC_VERSION) {
            throw PackageException(PackageError.MOC_TOO_NEW, "moc3 version $version > $MAX_SUPPORTED_MOC_VERSION")
        }
        return version
    }

    private fun pickIdleGroup(groups: Collection<String>): String? {
        groups.firstOrNull { it == IDLE_GROUP }?.let { return it }
        groups.firstOrNull { it.equals("idle", ignoreCase = true) }?.let { return it }
        groups.firstOrNull { it.lowercase().startsWith("idle") }?.let { return it }
        return groups.firstOrNull { it in IDLE_ALIASES }
    }

    private class VtubeStudio(val parameters: Map<String, String>, val idleAnimation: String?)

    private fun readVtubeStudio(
        archive: PackageArchive,
        model3Entry: PackageArchive.Entry,
        warnings: MutableList<String>
    ): VtubeStudio {
        val candidates = archive.entries.filter { it.lowerPath.endsWith(".vtube.json") }
        val entry = candidates.firstOrNull { it.directory == model3Entry.directory } ?: candidates.firstOrNull()
            ?: return VtubeStudio(emptyMap(), null)
        val json = runCatching { MiniJson.parseObject(archive.readBytes(entry, limits.maxJsonBytes)) }.getOrNull()
        if (json == null) {
            warnings += "VTube Studio 設定檔格式錯誤，已略過"
            return VtubeStudio(emptyMap(), null)
        }
        val parameters = LinkedHashMap<String, String>()
        json.arr("ParameterSettings")?.items?.forEach { item ->
            val obj = item as? JsonValue.Obj ?: return@forEach
            val input = obj.str("Input") ?: return@forEach
            val output = obj.str("OutputLive2D") ?: return@forEach
            if (input.isNotBlank() && output.isNotBlank()) parameters.putIfAbsent(input, output)
        }
        // IdleAnimation is relative to the model3.json folder in VTS packages.
        val idle = json.obj("FileReferences")?.str("IdleAnimation")?.takeIf { it.isNotBlank() }
        return VtubeStudio(parameters, idle)
    }

    private fun copyCreatorDocs(archive: PackageArchive, docsDir: File) {
        val docs = archive.entries.filter { entry ->
            val name = entry.fileName.lowercase()
            val ext = name.substringAfterLast('.', "")
            ext in DOC_EXTENSIONS && entry.size in 1..MAX_DOC_BYTES &&
                DOC_KEYWORDS.any { it in name }
        }.take(MAX_DOCS)
        if (docs.isEmpty()) return
        docsDir.mkdirs()
        docs.forEachIndexed { index, entry ->
            val ext = entry.fileName.substringAfterLast('.', "txt").lowercase()
            runCatching {
                File(docsDir, "creator_doc_%02d.%s".format(index, ext))
                    .writeBytes(archive.readBytes(entry, MAX_DOC_BYTES))
            }
        }
    }

    private class OutputWriter(private val root: File, private val limits: PackageLimits) {
        private var total = 0L

        fun write(relative: String, bytes: ByteArray) {
            total += bytes.size
            if (total > limits.maxTotalExtractedBytes) {
                throw PackageException(PackageError.ARCHIVE_TOO_LARGE, "extracted > ${limits.maxTotalExtractedBytes}")
            }
            val file = File(root, relative)
            file.parentFile?.mkdirs()
            try {
                file.writeBytes(bytes)
            } catch (e: java.io.IOException) {
                throw PackageException(PackageError.STORAGE_FAILED, e.message ?: relative)
            }
        }

        fun writeJson(relative: String, json: JsonValue) =
            write(relative, MiniJson.write(plainNumbers(json)).toByteArray(Charsets.UTF_8))

        /**
         * The Cubism SDK JSON parser does not understand exponent notation
         * ("1e-05"), which files touched by scripts often contain. Rewrite
         * those numbers as plain decimals; everything else keeps its text.
         */
        private fun plainNumbers(value: JsonValue): JsonValue = when (value) {
            is JsonValue.Num ->
                if ('e' in value.raw || 'E' in value.raw) {
                    JsonValue.Num(java.math.BigDecimal(value.raw).stripTrailingZeros().toPlainString())
                } else {
                    value
                }
            is JsonValue.Arr -> {
                value.items.replaceAll(::plainNumbers)
                value
            }
            is JsonValue.Obj -> {
                value.fields.replaceAll { _, v -> plainNumbers(v) }
                value
            }
            else -> value
        }
    }

    companion object {
        const val CONVERTER_VERSION = 1
        const val IDLE_GROUP = "Idle"
        /** moc3 header version byte: 1=3.0 2=3.3 3=4.0 4=4.2 5=5.0 6=5.3 (Cubism 5 SDK R5). */
        const val MAX_SUPPORTED_MOC_VERSION = 6
        const val MAX_TEXTURE_EDGE = 8192
        private val IDLE_ALIASES = setOf("待機", "待机", "taiki", "Taiki", "wait", "Wait", "loop", "Loop")
        private val DOC_EXTENSIONS = setOf("txt", "md", "pdf", "html", "htm")
        private val DOC_KEYWORDS = listOf("readme", "license", "licence", "terms", "規約", "利用", "kiyaku", "説明", "说明", "注意", "credit")
        private const val MAX_DOCS = 8
        private const val MAX_DOC_BYTES = 5L * 1024 * 1024

        private fun isPowerOfTwo(v: Int) = v > 0 && (v and (v - 1)) == 0
    }
}

/** PNG signature + IHDR size, without decoding. */
object PngInfo {
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    fun size(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < 24) return null
        for (i in SIGNATURE.indices) if (bytes[i] != SIGNATURE[i]) return null
        if (String(bytes, 12, 4, Charsets.US_ASCII) != "IHDR") return null
        val w = readInt(bytes, 16)
        val h = readInt(bytes, 20)
        return if (w > 0 && h > 0) w to h else null
    }

    private fun readInt(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or
            ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)
}

/**
 * motion3.json: the SDK sizes its arrays from Meta.CurveCount /
 * TotalSegmentCount / TotalPointCount / UserDataCount. Recompute them from the
 * actual data (same counting rules as CubismMotionJson::HasConsistency).
 */
internal object MotionRepair {
    fun repair(json: JsonValue.Obj): Boolean {
        val curves = json.arr("Curves") ?: return false
        var totalSegments = 0
        var totalPoints = 0
        for (curveValue in curves.items) {
            val curve = curveValue as? JsonValue.Obj ?: return false
            if (curve.str("Id").isNullOrEmpty()) return false
            val segments = curve.arr("Segments") ?: return false
            val values = segments.items.map { (it as? JsonValue.Num)?.toDouble() ?: return false }
            if (values.size < 2) return false
            var pos = 2
            totalPoints += 1
            if (pos >= values.size) return false
            while (pos < values.size) {
                when (values[pos].toInt()) {
                    0, 2, 3 -> {
                        if (pos + 3 > values.size) return false
                        totalPoints += 1
                        pos += 3
                    }
                    1 -> {
                        if (pos + 7 > values.size) return false
                        totalPoints += 3
                        pos += 7
                    }
                    else -> return false
                }
                totalSegments += 1
            }
        }
        val meta = json.obj("Meta") ?: JsonValue.Obj().also { json["Meta"] = it }
        meta["CurveCount"] = JsonValue.of(curves.items.size)
        meta["TotalSegmentCount"] = JsonValue.of(totalSegments)
        meta["TotalPointCount"] = JsonValue.of(totalPoints)
        val userData = json.arr("UserData")?.items.orEmpty()
        meta["UserDataCount"] = JsonValue.of(userData.size)
        // Not used for allocation by the SDK; only filled in when missing.
        if (meta["TotalUserDataSize"] !is JsonValue.Num) {
            meta["TotalUserDataSize"] = JsonValue.of(
                userData.sumOf { ((it as? JsonValue.Obj)?.str("Value")?.length ?: 0) + 1 }
            )
        }
        if (meta["Duration"] !is JsonValue.Num) meta["Duration"] = JsonValue.of(1)
        if (meta["Fps"] !is JsonValue.Num) meta["Fps"] = JsonValue.of(30)
        return true
    }

    fun collectIds(json: JsonValue.Obj, out: MutableSet<String>) {
        json.arr("Curves")?.items?.forEach { c ->
            val curve = c as? JsonValue.Obj ?: return@forEach
            if (curve.str("Target") == "Parameter") curve.str("Id")?.let(out::add)
        }
    }
}

/**
 * physics3.json: the SDK sizes its arrays from Meta.PhysicsSettingCount /
 * TotalInputCount / TotalOutputCount / VertexCount and indexes particles with
 * Output.VertexIndex without bounds checks.
 */
internal object PhysicsRepair {
    fun repair(json: JsonValue.Obj, warnings: MutableList<String>): Boolean {
        val settings = json.arr("PhysicsSettings") ?: return false
        var inputs = 0
        var outputs = 0
        var vertices = 0
        for (settingValue in settings.items) {
            val setting = settingValue as? JsonValue.Obj ?: return false
            // The SDK reads these as arrays unconditionally; missing ones crash it.
            val vertexList = setting.arr("Vertices")?.items ?: return false
            if (vertexList.isEmpty()) return false
            val inputList = setting.arr("Input")?.items ?: return false
            val outputList = setting.arr("Output")?.items ?: return false
            for (o in outputList) {
                val index = (o as? JsonValue.Obj)?.int("VertexIndex") ?: return false
                if (index < 0 || index >= vertexList.size) return false
            }
            inputs += inputList.size
            outputs += outputList.size
            vertices += vertexList.size
        }
        val meta = json.obj("Meta") ?: JsonValue.Obj().also { json["Meta"] = it }
        val before = listOf(meta.int("PhysicsSettingCount"), meta.int("TotalInputCount"), meta.int("TotalOutputCount"), meta.int("VertexCount"))
        meta["PhysicsSettingCount"] = JsonValue.of(settings.items.size)
        meta["TotalInputCount"] = JsonValue.of(inputs)
        meta["TotalOutputCount"] = JsonValue.of(outputs)
        meta["VertexCount"] = JsonValue.of(vertices)
        if (before != listOf(settings.items.size, inputs, outputs, vertices)) {
            warnings += "已修正物理設定的數量資訊"
        }
        return true
    }

    fun collectIds(json: JsonValue.Obj, out: MutableSet<String>) {
        json.arr("PhysicsSettings")?.items?.forEach { s ->
            val setting = s as? JsonValue.Obj ?: return@forEach
            setting.arr("Input")?.items?.forEach { i ->
                (i as? JsonValue.Obj)?.obj("Source")?.str("Id")?.let(out::add)
            }
            setting.arr("Output")?.items?.forEach { o ->
                (o as? JsonValue.Obj)?.obj("Destination")?.str("Id")?.let(out::add)
            }
        }
    }
}
