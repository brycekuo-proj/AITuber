package com.aituber.poc.character.live2d

import android.content.Context
import android.content.res.AssetManager
import com.aituber.poc.BuildConfig
import java.io.File

class Live2DNativeBridge(
    private val profile: Live2DCharacterProfile = Live2DCharacterProfiles.Haru
) {
    @Volatile
    private var initialized = false

    /**
     * The native Cubism runtime is process-wide: only the most recently
     * initialized bridge owns it. Older bridges (e.g. the home preview after
     * the overlay started) turn into no-ops instead of drawing, loading into
     * or releasing someone else's model.
     */
    @Volatile
    private var generation = 0L

    val available: Boolean
        get() = libraryLoaded

    /** True while this bridge owns the native runtime. */
    val ownsRuntime: Boolean
        get() = initialized && generation == activeGeneration

    private inline fun <T> ifOwner(fallback: T, block: () -> T): T = synchronized(ownerLock) {
        if (!ownsRuntime) fallback else runCatching(block).getOrDefault(fallback)
    }

    fun initialize(context: Context): Boolean = synchronized(ownerLock) {
        if (!libraryLoaded) return@synchronized false
        lastLoadError = null
        val ok = runCatching {
            val modelRoot = resolveModelRoot(context, profile)
            val mapping = profile.parameterMapping
            initialized = try {
                nativeInitializeV2(
                    context.assets,
                    modelRoot,
                    profile.displayName,
                    profile.model3File,
                    mapping.mouthOpen,
                    mapping.eyeLeftOpen,
                    mapping.eyeRightOpen,
                    mapping.breath,
                    mapping.headX ?: DEFAULT_HEAD_X,
                    mapping.headY ?: DEFAULT_HEAD_Y,
                    profile.layout.height,
                    profile.layout.offsetY
                )
            } catch (e: UnsatisfiedLinkError) {
                // A libaituber_live2d.so built before V2: it cannot read downloaded
                // characters and only understands single parameter ids.
                if (profile.modelSource == Live2DModelSource.APP_PRIVATE) {
                    throw IllegalStateException(NATIVE_LIBRARY_TOO_OLD)
                }
                nativeInitialize(
                    context.assets,
                    modelRoot,
                    profile.displayName,
                    profile.model3File,
                    firstCandidate(mapping.mouthOpen),
                    firstCandidate(mapping.eyeLeftOpen),
                    firstCandidate(mapping.eyeRightOpen),
                    firstCandidate(mapping.breath)
                )
            }
            initialized
        }.getOrElse {
            lastLoadError = it.message ?: it::class.java.simpleName
            false
        }
        if (ok) {
            generation = nextGeneration++
            activeGeneration = generation
        }
        ok
    }

    fun onSurfaceCreated(): Boolean = ifOwner(false) { nativeOnSurfaceCreated() }

    fun onSurfaceChanged(width: Int, height: Int): Boolean =
        ifOwner(false) { nativeOnSurfaceChanged(width, height) }

    /** Returns false when nothing was drawn (not the runtime owner). */
    fun drawFrame(): Boolean = ifOwner(false) {
        nativeOnDrawFrame()
        true
    }

    fun setMouthOpen(value: Float): Boolean = ifOwner(false) {
        nativeSetMouthOpen(value)
        true
    }

    fun setHeadInput(headX: Float, headY: Float): Boolean = ifOwner(false) {
        nativeSetHeadInput(headX, headY)
        true
    }

    fun setEyeOpen(leftEyeOpen: Float, rightEyeOpen: Float): Boolean = ifOwner(false) {
        nativeSetEyeOpen(leftEyeOpen.coerceIn(0f, 1f), rightEyeOpen.coerceIn(0f, 1f))
        true
    }

    fun setBreath(normalized: Float, intensity: Float): Boolean = ifOwner(false) {
        nativeSetBreath(normalized.coerceIn(0f, 1f), intensity.coerceIn(0f, 1f))
        true
    }

    fun startIdleMotion(): Boolean = ifOwner(false) { nativeStartIdleMotion() }

    /** Must run on this view's GL thread. Only the owner disposes the native runtime. */
    fun release() {
        synchronized(ownerLock) {
            if (ownsRuntime) {
                runCatching { nativeRelease() }
                activeGeneration = 0L
            }
            initialized = false
        }
    }

    fun snapshot(): Live2DNativeSnapshot {
        if (!libraryLoaded) {
            return Live2DNativeSnapshot.unavailable(lastLoadError ?: "LIVE2D_NATIVE_LIBRARY_NOT_LOADED")
        }
        if (!initialized) {
            // e.g. a downloaded character whose folder is missing: nativeInitialize never ran.
            lastLoadError?.let { return Live2DNativeSnapshot.unavailable(it) }
        } else if (!ownsRuntime) {
            return Live2DNativeSnapshot.unavailable(RUNTIME_OWNED_ELSEWHERE)
        }
        return runCatching { nativeSnapshot() }
            .getOrElse { Live2DNativeSnapshot.unavailable(it.message ?: it::class.java.simpleName) }
    }

    /** Parameter ids may be "A|B|C": native uses the first id the model has. */
    private external fun nativeInitializeV2(
        assetManager: AssetManager,
        modelRoot: String,
        modelName: String,
        model3File: String,
        mouthParameterId: String,
        leftEyeParameterId: String,
        rightEyeParameterId: String,
        breathParameterId: String,
        headXParameterId: String,
        headYParameterId: String,
        layoutHeight: Float,
        layoutOffsetY: Float
    ): Boolean

    /** Original entry point; still exported for older prebuilt libraries. */
    private external fun nativeInitialize(
        assetManager: AssetManager,
        modelRoot: String,
        modelName: String,
        model3File: String,
        mouthParameterId: String,
        leftEyeParameterId: String,
        rightEyeParameterId: String,
        breathParameterId: String
    ): Boolean
    private external fun nativeOnSurfaceCreated(): Boolean
    private external fun nativeOnSurfaceChanged(width: Int, height: Int): Boolean
    private external fun nativeSetMouthOpen(value: Float)
    private external fun nativeSetHeadInput(headX: Float, headY: Float)
    private external fun nativeSetEyeOpen(leftEyeOpen: Float, rightEyeOpen: Float)
    private external fun nativeSetBreath(normalized: Float, intensity: Float)
    private external fun nativeStartIdleMotion(): Boolean
    private external fun nativeOnDrawFrame()
    private external fun nativeRelease()
    private external fun nativeSnapshot(): Live2DNativeSnapshot

    companion object {
        @Volatile
        private var lastLoadError: String? = null

        internal const val FILE_SYSTEM_MODEL_PREFIX = "__AITUBER_FILE__:"
        internal const val RUNTIME_OWNED_ELSEWHERE = "LIVE2D_RUNTIME_OWNED_BY_ANOTHER_VIEW"

        private val ownerLock = Any()
        private var nextGeneration = 1L
        @Volatile
        private var activeGeneration = 0L

        /** "A|B": the native runtime uses the first id the model has. */
        internal const val DEFAULT_HEAD_X = "ParamAngleX|PARAM_ANGLE_X"
        internal const val DEFAULT_HEAD_Y = "ParamAngleY|PARAM_ANGLE_Y"

        internal const val NATIVE_LIBRARY_TOO_OLD =
            "LIVE2D_NATIVE_LIBRARY_TOO_OLD: rebuild libaituber_live2d.so to use downloaded characters"

        internal fun firstCandidate(spec: String): String = spec.substringBefore('|')

        /**
         * False when the bundled libaituber_live2d.so predates downloaded-character
         * support (no filesystem loading, no "A|B" parameter lists).
         */
        val supportsDownloadedCharacters: Boolean by lazy {
            libraryLoaded && runCatching { nativeApiVersion() >= 2 }.getOrDefault(false)
        }

        @JvmStatic
        private external fun nativeApiVersion(): Int

        internal fun resolveModelRoot(context: Context, profile: Live2DCharacterProfile): String =
            when (profile.modelSource) {
                Live2DModelSource.APK_ASSET -> profile.assetDir
                Live2DModelSource.APP_PRIVATE -> FILE_SYSTEM_MODEL_PREFIX +
                    resolvePrivateCharacterDir(context.filesDir, profile.assetDir)
            }

        internal fun resolvePrivateCharacterDir(filesDir: File, relativeDir: String): String {
            require(relativeDir.isNotBlank()) { "Private Live2D character directory is blank" }
            require(!File(relativeDir).isAbsolute) { "Private Live2D character directory must be relative" }

            val charactersRoot = File(filesDir, "characters").canonicalFile
            val requested = File(charactersRoot, relativeDir).canonicalFile
            val rootPrefix = charactersRoot.path + File.separator
            require(requested.path == charactersRoot.path || requested.path.startsWith(rootPrefix)) {
                "Private Live2D character directory escapes app storage"
            }
            require(requested.isDirectory) { "Private Live2D character directory not found: ${requested.path}" }
            return requested.absolutePath
        }

        val libraryLoaded: Boolean by lazy {
            if (!BuildConfig.LIVE2D_ENABLED) {
                lastLoadError = "LIVE2D_DISABLED_BY_LOCAL_CONFIGURATION"
                false
            } else {
                runCatching {
                    System.loadLibrary("aituber_live2d")
                    true
                }.getOrElse {
                    lastLoadError = it.message ?: it::class.java.simpleName
                    false
                }
            }
        }
    }
}
