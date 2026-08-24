package com.aituber.poc.character.live2d

import android.content.Context
import android.content.res.AssetManager
import com.aituber.poc.BuildConfig

class Live2DNativeBridge(
    private val profile: Live2DCharacterProfile = Live2DCharacterProfiles.Haru
) {
    @Volatile
    private var initialized = false

    val available: Boolean
        get() = libraryLoaded

    fun initialize(context: Context): Boolean {
        if (!libraryLoaded) return false
        return runCatching {
            initialized = nativeInitialize(
                context.assets,
                profile.assetDir,
                profile.displayName,
                profile.model3File,
                profile.parameterMapping.mouthOpen,
                profile.parameterMapping.eyeLeftOpen,
                profile.parameterMapping.eyeRightOpen,
                profile.parameterMapping.breath
            )
            initialized
        }.getOrElse {
            lastLoadError = it.message ?: it::class.java.simpleName
            false
        }
    }

    fun onSurfaceCreated(): Boolean {
        if (!initialized) return false
        return runCatching { nativeOnSurfaceCreated() }.getOrDefault(false)
    }

    fun onSurfaceChanged(width: Int, height: Int): Boolean {
        if (!initialized) return false
        return runCatching { nativeOnSurfaceChanged(width, height) }.getOrDefault(false)
    }

    fun drawFrame() {
        if (!initialized) return
        runCatching { nativeOnDrawFrame() }
    }

    fun setMouthOpen(value: Float): Boolean {
        if (!initialized) return false
        return runCatching {
            nativeSetMouthOpen(value.coerceIn(0f, 1f))
            true
        }.getOrDefault(false)
    }

    fun setEyeOpen(leftEyeOpen: Float, rightEyeOpen: Float): Boolean {
        if (!initialized) return false
        return runCatching {
            nativeSetEyeOpen(
                leftEyeOpen.coerceIn(0f, 1f),
                rightEyeOpen.coerceIn(0f, 1f)
            )
            true
        }.getOrDefault(false)
    }

    fun setBreath(normalized: Float, intensity: Float): Boolean {
        if (!initialized) return false
        return runCatching {
            nativeSetBreath(normalized.coerceIn(0f, 1f), intensity.coerceIn(0f, 1f))
            true
        }.getOrDefault(false)
    }

    fun startIdleMotion(): Boolean {
        if (!initialized) return false
        return runCatching { nativeStartIdleMotion() }.getOrDefault(false)
    }

    fun release() {
        if (!initialized) return
        runCatching { nativeRelease() }
        initialized = false
    }

    fun snapshot(): Live2DNativeSnapshot {
        if (!libraryLoaded) {
            return Live2DNativeSnapshot.unavailable(lastLoadError ?: "LIVE2D_NATIVE_LIBRARY_NOT_LOADED")
        }
        return runCatching { nativeSnapshot() }
            .getOrElse { Live2DNativeSnapshot.unavailable(it.message ?: it::class.java.simpleName) }
    }

    private external fun nativeInitialize(
        assetManager: AssetManager,
        modelAssetDir: String,
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
    private external fun nativeSetEyeOpen(leftEyeOpen: Float, rightEyeOpen: Float)
    private external fun nativeSetBreath(normalized: Float, intensity: Float)
    private external fun nativeStartIdleMotion(): Boolean
    private external fun nativeOnDrawFrame()
    private external fun nativeRelease()
    private external fun nativeSnapshot(): Live2DNativeSnapshot

    companion object {
        @Volatile
        private var lastLoadError: String? = null

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
