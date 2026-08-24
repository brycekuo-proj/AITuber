import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) {
        file.inputStream().use(::load)
    }
}

val live2dSdkDir = localProperties.getProperty("live2d.sdk.dir")?.takeIf { it.isNotBlank() }
val live2dModelDir = localProperties.getProperty("live2d.test.model.dir")?.takeIf { it.isNotBlank() }
val live2dDogModelDir = localProperties.getProperty("live2d.dog.model.dir")?.takeIf { it.isNotBlank() }
    ?: rootProject.file("../models/third-party/duokhay-loaf-corgi/model/duokhay mascot - loaf dog").absolutePath
val live2dEnabled = live2dSdkDir != null && live2dModelDir != null &&
    file(live2dSdkDir).isDirectory &&
    file("$live2dSdkDir/Core/lib/android/arm64-v8a/libLive2DCubismCore.a").isFile &&
    file(live2dModelDir).isDirectory
val live2dDogAvailable = file(live2dDogModelDir).isDirectory

android {
    namespace = "com.aituber.poc"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aituber.poc"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-alpha2"
        buildConfigField("Boolean", "LIVE2D_ENABLED", live2dEnabled.toString())

        if (live2dEnabled) {
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
            externalNativeBuild {
                cmake {
                    arguments += listOf(
                        "-DLIVE2D_SDK_DIR=$live2dSdkDir",
                        "-DLIVE2D_MODEL_ASSET_DIR=live2d/Haru"
                    )
                }
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    if (live2dEnabled) {
        ndkVersion = "26.3.11579264"
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
        sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/live2dAssets"))
    }
}

kotlin {
    jvmToolchain(17)
}

if (live2dEnabled) {
    val stageLive2DModelAssets by tasks.registering(Sync::class) {
        from(live2dModelDir)
        include(
            "*.model3.json",
            "*.moc3",
            "*.pose3.json",
            "*.physics3.json",
            "Haru.2048/**",
            "motions/haru_g_idle.motion3.json",
            "motions/haru_g_m15.motion3.json"
        )
        into(layout.buildDirectory.dir("generated/live2dAssets/live2d/Haru"))
    }
    val stageLive2DDogAssets by tasks.registering(Sync::class) {
        onlyIf { live2dDogAvailable }
        from(live2dDogModelDir)
        include(
            "*.model3.json",
            "*.moc3",
            "*.physics3.json",
            "*.cdi3.json",
            "bread dog chonk.2048/**"
        )
        into(layout.buildDirectory.dir("generated/live2dAssets/live2d/duokhay-loaf-dog"))
    }
    val stageLive2DShaderAssets by tasks.registering(Sync::class) {
        from("$live2dSdkDir/Framework/src/Rendering/OpenGL/Shaders/StandardES")
        include("*.vert", "*.frag")
        into(layout.buildDirectory.dir("generated/live2dAssets/live2d/framework/shaders"))
    }
    tasks.named("preBuild") {
        dependsOn(stageLive2DModelAssets)
        dependsOn(stageLive2DDogAssets)
        dependsOn(stageLive2DShaderAssets)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
