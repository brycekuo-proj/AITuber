#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <GLES2/gl2.h>

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

#include <CubismDefaultParameterId.hpp>
#include <CubismFramework.hpp>
#include <CubismModelSettingJson.hpp>
#include <Effect/CubismPose.hpp>
#include <ICubismAllocator.hpp>
#include <Id/CubismIdManager.hpp>
#include <Math/CubismMatrix44.hpp>
#include <Model/CubismUserModel.hpp>
#include <Motion/ACubismMotion.hpp>
#include <Motion/CubismMotionQueueEntry.hpp>
#include <Physics/CubismPhysics.hpp>
#include <Rendering/OpenGL/CubismRenderer_OpenGLES2.hpp>

#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"

namespace {

using namespace Live2D::Cubism::Framework;

constexpr const char* kLogTag = "AITuberLive2D";
constexpr const char* kDefaultModelAssetDir = "live2d/Haru";
constexpr const char* kDefaultModelName = "Haru";
constexpr const char* kDefaultModelJson = "Haru.model3.json";
constexpr const char* kMouthParameterId = "ParamMouthOpenY";
constexpr const char* kLeftEyeParameterId = "ParamEyeLOpen";
constexpr const char* kRightEyeParameterId = "ParamEyeROpen";
constexpr const char* kBreathParameterId = "ParamBreath";
constexpr float kBreathIntensity = 0.30f;
constexpr const char* kShaderAssetDir = "live2d/framework/shaders";
constexpr const char* kIdleMotionGroup = "Idle";
constexpr int kPriorityIdle = 1;
constexpr csmFloat32 kMaxPhysicsDeltaSeconds = 0.1f;

class AituberCubismAllocator : public ICubismAllocator {
public:
    void* Allocate(const csmSizeType size) override {
        return std::malloc(size);
    }

    void Deallocate(void* memory) override {
        std::free(memory);
    }

    void* AllocateAligned(const csmSizeType size, const csmUint32 alignment) override {
        const size_t offset = alignment - 1 + sizeof(void*);
        void* allocation = Allocate(size + static_cast<csmUint32>(offset));
        if (!allocation) return nullptr;

        size_t alignedAddress = reinterpret_cast<size_t>(allocation) + sizeof(void*);
        const size_t shift = alignedAddress % alignment;
        if (shift) {
            alignedAddress += alignment - shift;
        }

        void** preamble = reinterpret_cast<void**>(alignedAddress);
        preamble[-1] = allocation;
        return reinterpret_cast<void*>(alignedAddress);
    }

    void DeallocateAligned(void* alignedMemory) override {
        if (!alignedMemory) return;
        void** preamble = static_cast<void**>(alignedMemory);
        Deallocate(preamble[-1]);
    }
};

class SmokeModel : public CubismUserModel {
public:
    CubismModel* model() { return _model; }
    CubismModelMatrix* matrix() { return _modelMatrix; }

    void loadPose(const csmByte* buffer, csmSizeInt size) {
        LoadPose(buffer, size);
        if (_pose && _model) {
            _pose->Reset(_model);
        }
    }

    bool poseLoaded() const { return _pose != nullptr; }

    void updatePose(csmFloat32 deltaTimeSeconds) {
        if (_pose && _model) {
            _pose->UpdateParameters(_model, deltaTimeSeconds);
        }
    }

    void loadPhysics(const csmByte* buffer, csmSizeInt size) {
        LoadPhysics(buffer, size);
    }

    bool physicsLoaded() const { return _physics != nullptr; }

    void updatePhysics(csmFloat32 deltaTimeSeconds) {
        if (_physics && _model) {
            _physics->Evaluate(_model, deltaTimeSeconds);
        }
    }

    bool motionFinished() const {
        return !_motionManager || _motionManager->IsFinished();
    }

    bool updateMotion(csmFloat32 deltaTimeSeconds) {
        return _motionManager && _motionManager->UpdateMotion(_model, deltaTimeSeconds);
    }

    ACubismMotion* loadMotion(
        const csmByte* buffer,
        csmSizeInt size,
        CubismModelSettingJson* setting,
        const csmChar* group,
        csmInt32 index
    ) {
        return LoadMotion(buffer, size, nullptr, nullptr, nullptr, setting, group, index);
    }

    CubismMotionQueueEntryHandle startMotion(ACubismMotion* motion, csmInt32 priority) {
        return _motionManager
            ? _motionManager->StartMotionPriority(motion, true, priority)
            : InvalidMotionQueueEntryHandleValue;
    }
};

struct RuntimeStatus {
    bool runtimeLoaded = false;
    bool coreLoaded = false;
    bool modelLoaded = false;
    bool mouthParameterFound = false;
    float inputMouthOpen = 0.0f;
    float appliedMouthOpen = 0.0f;
    bool leftEyeParameterFound = false;
    bool rightEyeParameterFound = false;
    float inputLeftEyeOpen = 1.0f;
    float inputRightEyeOpen = 1.0f;
    float appliedLeftEyeOpen = 1.0f;
    float appliedRightEyeOpen = 1.0f;
    bool breathParameterFound = false;
    float inputBreathNormalized = 0.5f;
    float inputBreathIntensity = kBreathIntensity;
    float appliedBreathValue = 0.5f;
    float breathMin = 0.0f;
    float breathMax = 1.0f;
    float breathDefault = 0.5f;
    double renderFps = 0.0;
    long frameCount = 0;
    int surfaceWidth = 0;
    int surfaceHeight = 0;
    int textureCount = 0;
    int texturesLoaded = 0;
    bool poseLoaded = false;
    bool poseActive = false;
    bool idleMotionEnabled = false;
    std::string idleMotionStatus = "UNAVAILABLE";
    std::string idleMotionGroup = kIdleMotionGroup;
    std::string idleMotionFile = "n/a";
    int idleMotionIndex = -1;
    bool idleMotionPlaying = false;
    int idleMotionCount = 0;
    long idleMotionPlayCount = 0;
    std::string lastIdleMotionError = "n/a";
    bool physicsEnabled = false;
    std::string physicsStatus = "UNAVAILABLE";
    std::string physicsFile = "n/a";
    bool physicsLoaded = false;
    long physicsUpdateCount = 0;
    float physicsLastDeltaMs = 0.0f;
    int physicsInputCount = 0;
    int physicsOutputCount = 0;
    std::string physicsOutputParameterIds = "[]";
    std::string lastPhysicsError = "n/a";
    std::string modelName = kDefaultModelName;
    std::string mouthParameterId = kMouthParameterId;
    std::string poseFile = "n/a";
    std::string lastTexturePath = "n/a";
    std::string lastTextureError = "n/a";
    std::string glTextureIds = "[]";
    std::string lastError;
};

struct Runtime {
    AAssetManager* assetManager = nullptr;
    std::string modelAssetDir = kDefaultModelAssetDir;
    std::unique_ptr<SmokeModel> model;
    std::unique_ptr<CubismModelSettingJson> modelSetting;
    std::vector<GLuint> textures;
    const CubismId* mouthId = nullptr;
    const CubismId* leftEyeId = nullptr;
    const CubismId* rightEyeId = nullptr;
    const CubismId* breathId = nullptr;
    int nextIdleMotionIndex = 0;
    RuntimeStatus status;
    long lastFpsFrame = 0;
    double lastFpsMs = 0.0;
    double lastFrameMs = 0.0;
};

AituberCubismAllocator g_allocator;
CubismFramework::Option g_cubismOption;
AAssetManager* g_assetManager = nullptr;
std::mutex g_mutex;
std::unique_ptr<Runtime> g_runtime;
bool g_frameworkStarted = false;
bool g_frameworkInitialized = false;

double nowMs() {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<double>(ts.tv_sec) * 1000.0 + static_cast<double>(ts.tv_nsec) / 1000000.0;
}

void logInfo(const std::string& message) {
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "%s", message.c_str());
}

void logError(const std::string& message) {
    __android_log_print(ANDROID_LOG_ERROR, kLogTag, "%s", message.c_str());
}

std::string glErrorToString(const GLenum error) {
    if (error == GL_NO_ERROR) return "GL_NO_ERROR";
    std::ostringstream stream;
    stream << "GL_ERROR_0x" << std::hex << error;
    return stream.str();
}

std::string joinAssetPath(const std::string& dir, const std::string& file) {
    if (dir.empty()) return file;
    if (dir[dir.size() - 1] == '/') return dir + file;
    return dir + "/" + file;
}

std::string baseName(const std::string& path) {
    const size_t index = path.find_last_of("/\\");
    return index == std::string::npos ? path : path.substr(index + 1);
}

int extractJsonInt(const std::string& json, const std::string& key, int fallback = 0) {
    const std::string marker = "\"" + key + "\"";
    const size_t keyIndex = json.find(marker);
    if (keyIndex == std::string::npos) return fallback;
    const size_t colonIndex = json.find(':', keyIndex + marker.size());
    if (colonIndex == std::string::npos) return fallback;
    size_t valueIndex = colonIndex + 1;
    while (valueIndex < json.size() && std::isspace(static_cast<unsigned char>(json[valueIndex]))) {
        valueIndex++;
    }
    size_t endIndex = valueIndex;
    while (endIndex < json.size() && std::isdigit(static_cast<unsigned char>(json[endIndex]))) {
        endIndex++;
    }
    if (endIndex == valueIndex) return fallback;
    return std::atoi(json.substr(valueIndex, endIndex - valueIndex).c_str());
}

std::string extractPhysicsOutputIds(const std::string& json) {
    std::vector<std::string> ids;
    size_t cursor = 0;
    while (true) {
        const size_t destinationIndex = json.find("\"Destination\"", cursor);
        if (destinationIndex == std::string::npos) break;
        const size_t outputEnd = json.find("\"Vertices\"", destinationIndex);
        const size_t idIndex = json.find("\"Id\"", destinationIndex);
        if (idIndex != std::string::npos && (outputEnd == std::string::npos || idIndex < outputEnd)) {
            const size_t colonIndex = json.find(':', idIndex);
            const size_t quoteStart = colonIndex == std::string::npos ? std::string::npos : json.find('"', colonIndex + 1);
            const size_t quoteEnd = quoteStart == std::string::npos ? std::string::npos : json.find('"', quoteStart + 1);
            if (quoteStart != std::string::npos && quoteEnd != std::string::npos) {
                const std::string id = json.substr(quoteStart + 1, quoteEnd - quoteStart - 1);
                if (std::find(ids.begin(), ids.end(), id) == ids.end()) {
                    ids.push_back(id);
                }
                cursor = quoteEnd + 1;
                continue;
            }
        }
        cursor = destinationIndex + 1;
    }

    std::ostringstream stream;
    stream << "[";
    for (size_t i = 0; i < ids.size(); i++) {
        if (i > 0) stream << ",";
        stream << ids[i];
    }
    stream << "]";
    return stream.str();
}

bool readAsset(AAssetManager* manager, const std::string& path, std::vector<unsigned char>& out, std::string& error) {
    if (!manager) {
        error = "AssetManager unavailable";
        return false;
    }
    AAsset* asset = AAssetManager_open(manager, path.c_str(), AASSET_MODE_BUFFER);
    if (!asset) {
        error = "Asset not found: " + path;
        return false;
    }
    const off_t length = AAsset_getLength(asset);
    if (length <= 0) {
        AAsset_close(asset);
        error = "Asset empty: " + path;
        return false;
    }
    out.resize(static_cast<size_t>(length));
    const int read = AAsset_read(asset, out.data(), static_cast<size_t>(length));
    AAsset_close(asset);
    if (read != length) {
        error = "Asset read failed: " + path;
        return false;
    }
    return true;
}

csmByte* loadCubismAssetFile(const std::string filePath, csmSizeInt* outSize) {
    if (!outSize) return nullptr;
    *outSize = 0;

    std::string error;
    std::vector<unsigned char> data;
    const std::string shaderPath = joinAssetPath(kShaderAssetDir, baseName(filePath));
    if (!readAsset(g_assetManager, shaderPath, data, error)) {
        logError("Cubism asset load failed: " + shaderPath + " (" + error + ")");
        return nullptr;
    }

    auto* bytes = static_cast<csmByte*>(std::malloc(data.size()));
    if (!bytes) {
        logError("Cubism asset allocation failed: " + shaderPath);
        return nullptr;
    }
    std::memcpy(bytes, data.data(), data.size());
    *outSize = static_cast<csmSizeInt>(data.size());
    return bytes;
}

void releaseCubismAssetBytes(csmByte* byteData) {
    std::free(byteData);
}

bool ensureFramework(Runtime& runtime) {
    if (!g_frameworkStarted) {
        g_cubismOption.LogFunction = [](const char* message) {
            __android_log_print(ANDROID_LOG_INFO, kLogTag, "%s", message);
        };
        g_cubismOption.LoggingLevel = CubismFramework::Option::LogLevel_Warning;
        g_cubismOption.LoadFileFunction = loadCubismAssetFile;
        g_cubismOption.ReleaseBytesFunction = releaseCubismAssetBytes;
        if (!CubismFramework::StartUp(&g_allocator, &g_cubismOption)) {
            runtime.status.lastError = "CubismFramework StartUp failed";
            return false;
        }
        g_frameworkStarted = true;
    }
    if (!g_frameworkInitialized) {
        CubismFramework::Initialize();
        g_frameworkInitialized = true;
    }
    runtime.status.runtimeLoaded = true;
    runtime.status.coreLoaded = true;
    return true;
}

bool validateGlContext(Runtime& runtime) {
    const char* version = reinterpret_cast<const char*>(glGetString(GL_VERSION));
    const char* vendor = reinterpret_cast<const char*>(glGetString(GL_VENDOR));
    const char* renderer = reinterpret_cast<const char*>(glGetString(GL_RENDERER));
    const char* shadingLanguage = reinterpret_cast<const char*>(glGetString(GL_SHADING_LANGUAGE_VERSION));
    if (!version || !vendor || !renderer || !shadingLanguage) {
        runtime.status.lastError = "No valid OpenGL ES context";
        logError(runtime.status.lastError);
        return false;
    }
    logInfo(
        std::string("GL context: version=") + version +
        " vendor=" + vendor +
        " renderer=" + renderer +
        " shadingLanguage=" + shadingLanguage
    );
    return true;
}

void releaseTextures(Runtime& runtime) {
    if (!runtime.textures.empty()) {
        glDeleteTextures(static_cast<GLsizei>(runtime.textures.size()), runtime.textures.data());
        runtime.textures.clear();
    }
}

bool loadTexture(Runtime& runtime, const std::string& path, GLuint& textureId, std::string& error) {
    runtime.status.lastTexturePath = path;
    std::vector<unsigned char> png;
    if (!readAsset(runtime.assetManager, path, png, error)) {
        runtime.status.lastTextureError = error;
        return false;
    }

    int width = 0;
    int height = 0;
    int channels = 0;
    stbi_uc* pixels = stbi_load_from_memory(
        png.data(),
        static_cast<int>(png.size()),
        &width,
        &height,
        &channels,
        STBI_rgb_alpha
    );
    if (!pixels || width <= 0 || height <= 0) {
        if (pixels) stbi_image_free(pixels);
        error = "PNG decode failed: " + path;
        runtime.status.lastTextureError = error;
        return false;
    }

    glGenTextures(1, &textureId);
    if (textureId == 0) {
        stbi_image_free(pixels);
        error = "glGenTextures returned 0: " + glErrorToString(glGetError());
        runtime.status.lastTextureError = error;
        return false;
    }

    glBindTexture(GL_TEXTURE_2D, textureId);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    const GLenum uploadError = glGetError();
    if (uploadError != GL_NO_ERROR) {
        glDeleteTextures(1, &textureId);
        glBindTexture(GL_TEXTURE_2D, 0);
        stbi_image_free(pixels);
        error = "glTexImage2D failed for " + path + ": " + glErrorToString(uploadError);
        runtime.status.lastTextureError = error;
        textureId = 0;
        return false;
    }
    glGenerateMipmap(GL_TEXTURE_2D);
    const GLenum mipmapError = glGetError();
    if (mipmapError != GL_NO_ERROR) {
        glDeleteTextures(1, &textureId);
        glBindTexture(GL_TEXTURE_2D, 0);
        stbi_image_free(pixels);
        error = "glGenerateMipmap failed for " + path + ": " + glErrorToString(mipmapError);
        runtime.status.lastTextureError = error;
        textureId = 0;
        return false;
    }
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
    glBindTexture(GL_TEXTURE_2D, 0);
    stbi_image_free(pixels);
    runtime.status.lastTextureError = "n/a";
    return true;
}

std::string textureIdsToString(const std::vector<GLuint>& textureIds) {
    std::ostringstream stream;
    stream << "[";
    for (size_t i = 0; i < textureIds.size(); i++) {
        if (i > 0) stream << ",";
        stream << textureIds[i];
    }
    stream << "]";
    return stream.str();
}

bool startIdleMotionLocked(Runtime& runtime, bool forceNext) {
    if (!runtime.model || !runtime.model->model() || !runtime.modelSetting) {
        runtime.status.idleMotionStatus = "UNAVAILABLE";
        runtime.status.lastIdleMotionError = "Model not ready";
        return false;
    }

    const csmInt32 count = runtime.modelSetting->GetMotionCount(kIdleMotionGroup);
    runtime.status.idleMotionGroup = kIdleMotionGroup;
    runtime.status.idleMotionCount = count;
    runtime.status.idleMotionEnabled = count > 0;
    if (count <= 0) {
        runtime.status.idleMotionStatus = "UNSUPPORTED";
        runtime.status.lastIdleMotionError = "Idle motion group empty";
        return false;
    }

    if (!forceNext && !runtime.model->motionFinished()) {
        runtime.status.idleMotionPlaying = true;
        runtime.status.idleMotionStatus = "PLAYING";
        return true;
    }

    const csmInt32 motionIndex = runtime.nextIdleMotionIndex % count;
    const char* fileName = runtime.modelSetting->GetMotionFileName(kIdleMotionGroup, motionIndex);
    if (!fileName || std::strlen(fileName) == 0) {
        runtime.status.idleMotionStatus = "FAILED";
        runtime.status.lastIdleMotionError = "Idle motion file missing in model3.json";
        runtime.status.idleMotionPlaying = false;
        return false;
    }

    std::string error;
    std::vector<unsigned char> motionJson;
    const std::string motionPath = joinAssetPath(runtime.modelAssetDir, fileName);
    if (!readAsset(runtime.assetManager, motionPath, motionJson, error)) {
        runtime.status.idleMotionStatus = "FAILED";
        runtime.status.lastIdleMotionError = error;
        runtime.status.idleMotionFile = fileName;
        runtime.status.idleMotionIndex = motionIndex;
        runtime.status.idleMotionPlaying = false;
        logError("Idle motion unavailable: " + error);
        return false;
    }

    ACubismMotion* motion = runtime.model->loadMotion(
        reinterpret_cast<const csmByte*>(motionJson.data()),
        static_cast<csmSizeInt>(motionJson.size()),
        runtime.modelSetting.get(),
        kIdleMotionGroup,
        motionIndex
    );
    if (!motion) {
        runtime.status.idleMotionStatus = "FAILED";
        runtime.status.lastIdleMotionError = "Idle motion parse failed: " + motionPath;
        runtime.status.idleMotionFile = fileName;
        runtime.status.idleMotionIndex = motionIndex;
        runtime.status.idleMotionPlaying = false;
        logError(runtime.status.lastIdleMotionError);
        return false;
    }

    const CubismMotionQueueEntryHandle handle = runtime.model->startMotion(motion, kPriorityIdle);
    if (handle == InvalidMotionQueueEntryHandleValue) {
        ACubismMotion::Delete(motion);
        runtime.status.idleMotionStatus = "FAILED";
        runtime.status.lastIdleMotionError = "Idle motion start rejected";
        runtime.status.idleMotionFile = fileName;
        runtime.status.idleMotionIndex = motionIndex;
        runtime.status.idleMotionPlaying = false;
        return false;
    }

    runtime.status.idleMotionFile = fileName;
    runtime.status.idleMotionIndex = motionIndex;
    runtime.status.idleMotionPlaying = true;
    runtime.status.idleMotionStatus = "PLAYING";
    runtime.status.lastIdleMotionError = "n/a";
    runtime.status.idleMotionPlayCount += 1L;
    runtime.nextIdleMotionIndex = (motionIndex + 1) % count;
    logInfo("Idle motion started: " + motionPath);
    return true;
}

void loadPhysicsLocked(Runtime& runtime) {
    runtime.status.physicsEnabled = false;
    runtime.status.physicsStatus = "UNSUPPORTED";
    runtime.status.physicsFile = "n/a";
    runtime.status.physicsLoaded = false;
    runtime.status.physicsUpdateCount = 0;
    runtime.status.physicsLastDeltaMs = 0.0f;
    runtime.status.physicsInputCount = 0;
    runtime.status.physicsOutputCount = 0;
    runtime.status.physicsOutputParameterIds = "[]";
    runtime.status.lastPhysicsError = "Physics file not referenced";

    if (!runtime.model || !runtime.model->model() || !runtime.modelSetting) {
        runtime.status.physicsStatus = "UNAVAILABLE";
        runtime.status.lastPhysicsError = "Model not ready";
        return;
    }

    const char* physicsFile = runtime.modelSetting->GetPhysicsFileName();
    if (!physicsFile || std::strlen(physicsFile) == 0) {
        return;
    }

    runtime.status.physicsFile = physicsFile;
    std::string error;
    std::vector<unsigned char> physicsJson;
    const std::string physicsPath = joinAssetPath(runtime.modelAssetDir, physicsFile);
    if (!readAsset(runtime.assetManager, physicsPath, physicsJson, error)) {
        runtime.status.physicsStatus = "PHYSICS_NOT_FOUND";
        runtime.status.lastPhysicsError = error;
        logError("Physics unavailable: " + error);
        return;
    }

    const std::string physicsText(reinterpret_cast<const char*>(physicsJson.data()), physicsJson.size());
    runtime.status.physicsInputCount = extractJsonInt(physicsText, "TotalInputCount", 0);
    runtime.status.physicsOutputCount = extractJsonInt(physicsText, "TotalOutputCount", 0);
    runtime.status.physicsOutputParameterIds = extractPhysicsOutputIds(physicsText);
    runtime.model->loadPhysics(
        reinterpret_cast<const csmByte*>(physicsJson.data()),
        static_cast<csmSizeInt>(physicsJson.size())
    );
    runtime.status.physicsLoaded = runtime.model->physicsLoaded();
    runtime.status.physicsEnabled = runtime.status.physicsLoaded;
    runtime.status.physicsStatus = runtime.status.physicsLoaded ? "READY" : "PHYSICS_FAILED";
    runtime.status.lastPhysicsError = runtime.status.physicsLoaded ? "n/a" : "Physics parse returned null";
    if (runtime.status.physicsLoaded) {
        logInfo("Live2D physics loaded: " + physicsPath);
    } else {
        logError("Physics parse returned null: " + physicsPath);
    }
}

bool loadModel(Runtime& runtime) {
    if (!ensureFramework(runtime)) {
        return false;
    }

    releaseTextures(runtime);
    runtime.status.modelLoaded = false;
    runtime.status.mouthParameterFound = false;
    runtime.status.textureCount = 0;
    runtime.status.texturesLoaded = 0;
    runtime.status.lastTexturePath = "n/a";
    runtime.status.lastTextureError = "n/a";
    runtime.status.glTextureIds = "[]";
    runtime.model.reset(new SmokeModel());
    runtime.modelSetting.reset();
    runtime.mouthId = nullptr;

    std::string error;
    std::vector<unsigned char> modelJson;
    const std::string modelJsonPath = joinAssetPath(runtime.modelAssetDir, kDefaultModelJson);
    if (!readAsset(runtime.assetManager, modelJsonPath, modelJson, error)) {
        runtime.status.lastError = error;
        return false;
    }

    runtime.modelSetting.reset(new CubismModelSettingJson(
        reinterpret_cast<const csmByte*>(modelJson.data()),
        static_cast<csmSizeInt>(modelJson.size())
    ));

    const char* mocFile = runtime.modelSetting->GetModelFileName();
    if (!mocFile || std::strlen(mocFile) == 0) {
        runtime.status.lastError = "model3.json has no moc file";
        return false;
    }
    std::vector<unsigned char> moc;
    if (!readAsset(runtime.assetManager, joinAssetPath(runtime.modelAssetDir, mocFile), moc, error)) {
        runtime.status.lastError = error;
        return false;
    }

    runtime.model->LoadModel(
        reinterpret_cast<const csmByte*>(moc.data()),
        static_cast<csmSizeInt>(moc.size())
    );
    if (!runtime.model->model()) {
        runtime.status.lastError = "Cubism model load failed";
        return false;
    }

    csmMap<csmString, csmFloat32> layout;
    runtime.modelSetting->GetLayoutMap(layout);
    if (runtime.model->matrix()) {
        runtime.model->matrix()->SetupFromLayout(layout);
        runtime.model->matrix()->SetHeight(2.0f);
        runtime.model->matrix()->TranslateY(-0.1f);
    }
    runtime.model->model()->SaveParameters();

    runtime.model->CreateRenderer(
        static_cast<csmUint32>(std::max(runtime.status.surfaceWidth, 1)),
        static_cast<csmUint32>(std::max(runtime.status.surfaceHeight, 1))
    );
    auto* renderer = runtime.model->GetRenderer<Rendering::CubismRenderer_OpenGLES2>();
    if (!renderer) {
        runtime.status.lastError = "OpenGL renderer creation failed";
        return false;
    }
    renderer->IsPremultipliedAlpha(false);

    const csmInt32 textureCount = runtime.modelSetting->GetTextureCount();
    runtime.status.textureCount = textureCount;
    for (csmInt32 i = 0; i < textureCount; i++) {
        const char* textureFile = runtime.modelSetting->GetTextureFileName(i);
        if (!textureFile || std::strlen(textureFile) == 0) continue;
        GLuint textureId = 0;
        const std::string texturePath = joinAssetPath(runtime.modelAssetDir, textureFile);
        if (!loadTexture(runtime, texturePath, textureId, error)) {
            runtime.status.lastError = error;
            return false;
        }
        runtime.textures.push_back(textureId);
        renderer->BindTexture(static_cast<csmUint32>(i), textureId);
        runtime.status.texturesLoaded = static_cast<int>(runtime.textures.size());
        runtime.status.glTextureIds = textureIdsToString(runtime.textures);
        logInfo("Bound Live2D texture index=" + std::to_string(i) + " id=" + std::to_string(textureId) + " path=" + texturePath);
    }

    runtime.status.poseFile = "n/a";
    runtime.status.poseLoaded = false;
    runtime.status.poseActive = false;
    runtime.status.idleMotionGroup = kIdleMotionGroup;
    runtime.status.idleMotionCount = runtime.modelSetting->GetMotionCount(kIdleMotionGroup);
    runtime.status.idleMotionEnabled = runtime.status.idleMotionCount > 0;
    runtime.status.idleMotionStatus = runtime.status.idleMotionEnabled ? "READY" : "UNSUPPORTED";
    runtime.status.idleMotionFile = "n/a";
    runtime.status.idleMotionIndex = -1;
    runtime.status.idleMotionPlaying = false;
    runtime.status.lastIdleMotionError = runtime.status.idleMotionEnabled ? "n/a" : "Idle motion group empty";
    runtime.nextIdleMotionIndex = 0;
    loadPhysicsLocked(runtime);
    bool poseError = false;
    const char* poseFile = runtime.modelSetting->GetPoseFileName();
    if (poseFile && std::strlen(poseFile) > 0) {
        runtime.status.poseFile = poseFile;
        std::vector<unsigned char> poseJson;
        const std::string posePath = joinAssetPath(runtime.modelAssetDir, poseFile);
        if (readAsset(runtime.assetManager, posePath, poseJson, error)) {
            runtime.model->loadPose(
                reinterpret_cast<const csmByte*>(poseJson.data()),
                static_cast<csmSizeInt>(poseJson.size())
            );
            runtime.status.poseLoaded = runtime.model->poseLoaded();
            runtime.status.poseActive = runtime.status.poseLoaded;
            if (runtime.status.poseLoaded) {
                logInfo("Live2D pose loaded: " + posePath);
            } else {
                runtime.status.lastError = "Pose parse returned null: " + posePath;
                poseError = true;
                logError(runtime.status.lastError);
            }
        } else {
            runtime.status.lastError = "Pose unavailable: " + error;
            poseError = true;
            logError(runtime.status.lastError);
        }
    }

    runtime.mouthId = CubismFramework::GetIdManager()->GetId(kMouthParameterId);
    runtime.leftEyeId = CubismFramework::GetIdManager()->GetId(kLeftEyeParameterId);
    runtime.rightEyeId = CubismFramework::GetIdManager()->GetId(kRightEyeParameterId);
    runtime.breathId = CubismFramework::GetIdManager()->GetId(kBreathParameterId);
    const csmInt32 mouthIndex = runtime.model->model()->GetParameterIndex(runtime.mouthId);
    const bool found = mouthIndex >= 0 && mouthIndex < runtime.model->model()->GetParameterCount();
    runtime.status.mouthParameterFound = found;
    const csmInt32 leftEyeIndex = runtime.model->model()->GetParameterIndex(runtime.leftEyeId);
    runtime.status.leftEyeParameterFound = leftEyeIndex >= 0 && leftEyeIndex < runtime.model->model()->GetParameterCount();
    const csmInt32 rightEyeIndex = runtime.model->model()->GetParameterIndex(runtime.rightEyeId);
    runtime.status.rightEyeParameterFound = rightEyeIndex >= 0 && rightEyeIndex < runtime.model->model()->GetParameterCount();
    const csmInt32 breathIndex = runtime.model->model()->GetParameterIndex(runtime.breathId);
    runtime.status.breathParameterFound = breathIndex >= 0 && breathIndex < runtime.model->model()->GetParameterCount();
    if (runtime.status.breathParameterFound) {
        runtime.status.breathMin = runtime.model->model()->GetParameterMinimumValue(static_cast<csmUint32>(breathIndex));
        runtime.status.breathMax = runtime.model->model()->GetParameterMaximumValue(static_cast<csmUint32>(breathIndex));
        runtime.status.breathDefault = runtime.model->model()->GetParameterDefaultValue(static_cast<csmUint32>(breathIndex));
        runtime.status.appliedBreathValue = runtime.status.breathDefault;
    }
    runtime.status.modelLoaded = true;
    if (!found) {
        runtime.status.lastError = "ParamMouthOpenY not found";
    } else if (!poseError) {
        runtime.status.lastError = "";
    }
    logInfo("Live2D model loaded");
    return true;
}

void discardContextBoundResources(Runtime& runtime) {
    runtime.textures.clear();
    runtime.model.reset();
    runtime.modelSetting.reset();
    runtime.mouthId = nullptr;
    runtime.leftEyeId = nullptr;
    runtime.rightEyeId = nullptr;
    runtime.breathId = nullptr;
    runtime.status.modelLoaded = false;
    runtime.status.mouthParameterFound = false;
    runtime.status.breathParameterFound = false;
    runtime.status.texturesLoaded = 0;
    runtime.status.glTextureIds = "[]";
    runtime.status.poseLoaded = false;
    runtime.status.poseActive = false;
    runtime.status.idleMotionEnabled = false;
    runtime.status.idleMotionStatus = "UNAVAILABLE";
    runtime.status.idleMotionFile = "n/a";
    runtime.status.idleMotionIndex = -1;
    runtime.status.idleMotionPlaying = false;
    runtime.status.idleMotionCount = 0;
    runtime.status.physicsEnabled = false;
    runtime.status.physicsStatus = "UNAVAILABLE";
    runtime.status.physicsFile = "n/a";
    runtime.status.physicsLoaded = false;
    runtime.status.physicsUpdateCount = 0;
    runtime.status.physicsLastDeltaMs = 0.0f;
    runtime.status.physicsInputCount = 0;
    runtime.status.physicsOutputCount = 0;
    runtime.status.physicsOutputParameterIds = "[]";
    runtime.status.lastPhysicsError = "n/a";
}

RuntimeStatus copyStatusLocked() {
    if (!g_runtime) {
        RuntimeStatus empty;
        empty.lastError = "Runtime not initialized";
        return empty;
    }
    return g_runtime->status;
}

float mapBreathValue(float normalized, float minValue, float maxValue, float defaultValue, float intensity) {
    const float safeMin = std::min(minValue, maxValue);
    const float safeMax = std::max(minValue, maxValue);
    const float safeDefault = std::max(safeMin, std::min(safeMax, defaultValue));
    const float safeNormalized = std::max(0.0f, std::min(1.0f, normalized));
    const float safeIntensity = std::max(0.0f, std::min(1.0f, intensity));
    const float lower = (safeDefault - safeMin) * safeIntensity;
    const float upper = (safeMax - safeDefault) * safeIntensity;
    const float mapped = safeNormalized < 0.5f
        ? safeDefault - lower * ((0.5f - safeNormalized) / 0.5f)
        : safeDefault + upper * ((safeNormalized - 0.5f) / 0.5f);
    return std::max(safeMin, std::min(safeMax, mapped));
}

void applyBreathLocked(float normalized, float intensity) {
    if (!g_runtime) return;
    g_runtime->status.inputBreathNormalized = std::max(0.0f, std::min(1.0f, normalized));
    g_runtime->status.inputBreathIntensity = std::max(0.0f, std::min(1.0f, intensity));
    if (!g_runtime->model || !g_runtime->model->model() || !g_runtime->breathId) return;
    auto* model = g_runtime->model->model();
    const csmInt32 index = model->GetParameterIndex(g_runtime->breathId);
    if (index >= 0 && index < model->GetParameterCount()) {
        g_runtime->status.breathMin = model->GetParameterMinimumValue(static_cast<csmUint32>(index));
        g_runtime->status.breathMax = model->GetParameterMaximumValue(static_cast<csmUint32>(index));
        g_runtime->status.breathDefault = model->GetParameterDefaultValue(static_cast<csmUint32>(index));
        const float value = mapBreathValue(
            g_runtime->status.inputBreathNormalized,
            g_runtime->status.breathMin,
            g_runtime->status.breathMax,
            g_runtime->status.breathDefault,
            g_runtime->status.inputBreathIntensity
        );
        model->SetParameterValue(index, value, 1.0f);
        g_runtime->status.appliedBreathValue = value;
        g_runtime->status.breathParameterFound = true;
    } else {
        g_runtime->status.breathParameterFound = false;
    }
}

void throwIfNeeded(JNIEnv* env, const std::string& message) {
    if (message.empty()) return;
    jclass exceptionClass = env->FindClass("java/lang/IllegalStateException");
    if (exceptionClass) {
        env->ThrowNew(exceptionClass, message.c_str());
    }
}

jstring toJString(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_aituber_poc_character_live2d_Live2DNativeBridge_nativeInitialize(
    JNIEnv* env,
    jobject,
    jobject assetManager,
    jstring modelAssetDir
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const char* modelDirChars = env->GetStringUTFChars(modelAssetDir, nullptr);
    std::string modelDir = modelDirChars ? modelDirChars : kDefaultModelAssetDir;
    env->ReleaseStringUTFChars(modelAssetDir, modelDirChars);

    AAssetManager* manager = AAssetManager_fromJava(env, assetManager);
    if (!manager) {
        g_runtime.reset(new Runtime());
        g_runtime->status.lastError = "AAssetManager_fromJava failed";
        return JNI_FALSE;
    }

    g_runtime.reset(new Runtime());
    g_runtime->assetManager = manager;
    g_assetManager = manager;
    g_runtime->modelAssetDir = modelDir.empty() ? kDefaultModelAssetDir : modelDir;
    g_runtime->status.modelName = kDefaultModelName;
    g_runtime->status.mouthParameterId = kMouthParameterId;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_aituber_poc_character_live2d_Live2DNativeBridge_nativeOnSurfaceCreated(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_runtime) return JNI_FALSE;

    if (!validateGlContext(*g_runtime)) {
        return JNI_FALSE;
    }
    discardContextBoundResources(*g_runtime);
    glEnable(GL_BLEND);
    glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    if (!ensureFramework(*g_runtime)) {
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_aituber_poc_character_live2d_Live2DNativeBridge_nativeOnSurfaceChanged(
    JNIEnv*,
    jobject,
    jint width,
    jint height
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_runtime) return JNI_FALSE;
    if (width <= 0 || height <= 0) {
        g_runtime->status.lastError = "Invalid GL surface size";
        return JNI_FALSE;
    }
    if (!validateGlContext(*g_runtime)) {
        return JNI_FALSE;
    }
    g_runtime->status.surfaceWidth = width;
    g_runtime->status.surfaceHeight = height;
    glViewport(0, 0, width, height);

    if (!g_runtime->model || !g_runtime->status.modelLoaded) {
        return loadModel(*g_runtime) ? JNI_TRUE : JNI_FALSE;
    } else {
        g_runtime->model->DeleteRenderer();
        g_runtime->model->CreateRenderer(static_cast<csmUint32>(std::max(width, 1)), static_cast<csmUint32>(std::max(height, 1)));
        auto* renderer = g_runtime->model->GetRenderer<Rendering::CubismRenderer_OpenGLES2>();
        if (!renderer) {
            g_runtime->status.lastError = "OpenGL renderer recreation failed";
            return JNI_FALSE;
        }
        renderer->IsPremultipliedAlpha(false);
        for (csmUint32 i = 0; i < g_runtime->textures.size(); i++) {
            renderer->BindTexture(i, g_runtime->textures[i]);
        }
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_aituber_poc_character_live2d_Live2DNativeBridge_nativeSetMouthOpen(
    JNIEnv*,
    jobject,
    jfloat value
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_runtime) return;
    const float clamped = std::max(0.0f, std::min(1.0f, value));
    g_runtime->status.inputMouthOpen = clamped;
    if (!g_runtime->model || !g_runtime->model->model() || !g_runtime->mouthId) return;
    const csmInt32 index = g_runtime->model->model()->GetParameterIndex(g_runtime->mouthId);
    if (index >= 0 && index < g_runtime->model->model()->GetParameterCount()) {
        g_runtime->model->model()->SetParameterValue(index, clamped, 1.0f);
        g_runtime->status.appliedMouthOpen = clamped;
        g_runtime->status.mouthParameterFound = true;
    } else {
        g_runtime->status.mouthParameterFound = false;
        g_runtime->status.lastError = "ParamMouthOpenY not found";
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_aituber_poc_character_live2d_Live2DNativeBridge_nativeSetEyeOpen(
    JNIEnv*,
    jobject,
    jfloat leftEyeOpen,
    jfloat rightEyeOpen
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_runtime) return;
    g_runtime->status.inputLeftEyeOpen = std::max(0.0f, std::min(1.0f, leftEyeOpen));
    g_runtime->status.inputRightEyeOpen = std::max(0.0f, std::min(1.0f, rightEyeOpen));
    if (!g_runtime->model || !g_runtime->model->model()) return;

    auto* model = g_runtime->model->model();
    if (g_runtime->leftEyeId) {
        const csmInt32 index = model->GetParameterIndex(g_runtime->leftEyeId);
        if (index >= 0 && index < model->GetParameterCount()) {
            model->SetParameterValue(index, g_runtime->status.inputLeftEyeOpen, 1.0f);
            g_runtime->status.appliedLeftEyeOpen = g_runtime->status.inputLeftEyeOpen;
            g_runtime->status.leftEyeParameterFound = true;
        } else {
            g_runtime->status.leftEyeParameterFound = false;
        }
    }
    if (g_runtime->rightEyeId) {
        const csmInt32 index = model->GetParameterIndex(g_runtime->rightEyeId);
        if (index >= 0 && index < model->GetParameterCount()) {
            model->SetParameterValue(index, g_runtime->status.inputRightEyeOpen, 1.0f);
            g_runtime->status.appliedRightEyeOpen = g_runtime->status.inputRightEyeOpen;
            g_runtime->status.rightEyeParameterFound = true;
        } else {
            g_runtime->status.rightEyeParameterFound = false;
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_aituber_poc_character_live2d_Live2DNativeBridge_nativeSetBreath(
    JNIEnv*,
    jobject,
    jfloat normalized,
    jfloat intensity
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    applyBreathLocked(normalized, intensity);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_aituber_poc_character_live2d_Live2DNativeBridge_nativeStartIdleMotion(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_runtime) return JNI_FALSE;
    return startIdleMotionLocked(*g_runtime, true) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_aituber_poc_character_live2d_Live2DNativeBridge_nativeOnDrawFrame(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_runtime || !g_runtime->model || !g_runtime->model->model()) {
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        return;
    }

    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    glClearDepthf(1.0f);

    const double currentMs = nowMs();
    const csmFloat32 deltaTimeSeconds = g_runtime->lastFrameMs == 0.0
        ? 1.0f / 30.0f
        : static_cast<csmFloat32>(std::max(0.0, currentMs - g_runtime->lastFrameMs) / 1000.0);
    g_runtime->lastFrameMs = currentMs;

    auto* model = g_runtime->model->model();
    model->LoadParameters();
    if (g_runtime->status.idleMotionEnabled) {
        if (g_runtime->model->motionFinished()) {
            startIdleMotionLocked(*g_runtime, true);
        } else {
            g_runtime->model->updateMotion(deltaTimeSeconds);
            g_runtime->status.idleMotionPlaying = !g_runtime->model->motionFinished();
            g_runtime->status.idleMotionStatus = g_runtime->status.idleMotionPlaying ? "PLAYING" : "FINISHED";
        }
    }
    if (!g_runtime->status.idleMotionPlaying || g_runtime->status.inputBreathIntensity > kBreathIntensity + 0.001f) {
        applyBreathLocked(g_runtime->status.inputBreathNormalized, g_runtime->status.inputBreathIntensity);
    }
    if (g_runtime->status.physicsLoaded) {
        const csmFloat32 boundedPhysicsDelta = std::max(
            0.0f,
            std::min(deltaTimeSeconds, kMaxPhysicsDeltaSeconds)
        );
        g_runtime->model->updatePhysics(boundedPhysicsDelta);
        g_runtime->status.physicsEnabled = true;
        g_runtime->status.physicsStatus = "ACTIVE";
        g_runtime->status.physicsUpdateCount += 1L;
        g_runtime->status.physicsLastDeltaMs = boundedPhysicsDelta * 1000.0f;
    }
    if (g_runtime->mouthId) {
        const csmInt32 index = model->GetParameterIndex(g_runtime->mouthId);
        if (index >= 0 && index < model->GetParameterCount()) {
            model->SetParameterValue(index, g_runtime->status.inputMouthOpen, 1.0f);
            g_runtime->status.appliedMouthOpen = g_runtime->status.inputMouthOpen;
            g_runtime->status.mouthParameterFound = true;
        }
    }
    if (g_runtime->leftEyeId) {
        const csmInt32 index = model->GetParameterIndex(g_runtime->leftEyeId);
        if (index >= 0 && index < model->GetParameterCount()) {
            model->SetParameterValue(index, g_runtime->status.inputLeftEyeOpen, 1.0f);
            g_runtime->status.appliedLeftEyeOpen = g_runtime->status.inputLeftEyeOpen;
            g_runtime->status.leftEyeParameterFound = true;
        } else {
            g_runtime->status.leftEyeParameterFound = false;
        }
    }
    if (g_runtime->rightEyeId) {
        const csmInt32 index = model->GetParameterIndex(g_runtime->rightEyeId);
        if (index >= 0 && index < model->GetParameterCount()) {
            model->SetParameterValue(index, g_runtime->status.inputRightEyeOpen, 1.0f);
            g_runtime->status.appliedRightEyeOpen = g_runtime->status.inputRightEyeOpen;
            g_runtime->status.rightEyeParameterFound = true;
        } else {
            g_runtime->status.rightEyeParameterFound = false;
        }
    }
    model->SaveParameters();
    g_runtime->model->updatePose(deltaTimeSeconds);
    g_runtime->status.poseActive = g_runtime->model->poseLoaded();
    model->Update();

    CubismMatrix44 projection;
    const int width = std::max(g_runtime->status.surfaceWidth, 1);
    const int height = std::max(g_runtime->status.surfaceHeight, 1);
    const float aspect = static_cast<float>(width) / static_cast<float>(height);
    projection.Scale(1.0f / aspect, 1.0f);
    if (g_runtime->model->matrix()) {
        projection.MultiplyByMatrix(g_runtime->model->matrix());
    }

    auto* renderer = g_runtime->model->GetRenderer<Rendering::CubismRenderer_OpenGLES2>();
    if (renderer) {
        renderer->SetMvpMatrix(&projection);
        renderer->DrawModel();
    }

    g_runtime->status.frameCount += 1;
    if (g_runtime->lastFpsMs == 0.0) {
        g_runtime->lastFpsMs = currentMs;
        g_runtime->lastFpsFrame = g_runtime->status.frameCount;
    } else if (currentMs - g_runtime->lastFpsMs >= 1000.0) {
        const long frames = g_runtime->status.frameCount - g_runtime->lastFpsFrame;
        g_runtime->status.renderFps = static_cast<double>(frames) * 1000.0 / (currentMs - g_runtime->lastFpsMs);
        g_runtime->lastFpsMs = currentMs;
        g_runtime->lastFpsFrame = g_runtime->status.frameCount;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_aituber_poc_character_live2d_Live2DNativeBridge_nativeRelease(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_runtime) {
        releaseTextures(*g_runtime);
        g_runtime->model.reset();
        g_runtime->modelSetting.reset();
        g_runtime.reset();
    }
    if (g_frameworkInitialized) {
        CubismFramework::Dispose();
        g_frameworkInitialized = false;
    }
    if (g_frameworkStarted) {
        CubismFramework::CleanUp();
        g_frameworkStarted = false;
    }
    g_assetManager = nullptr;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_aituber_poc_character_live2d_Live2DNativeBridge_nativeSnapshot(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    RuntimeStatus status = copyStatusLocked();
    jclass clazz = env->FindClass("com/aituber/poc/character/live2d/Live2DNativeSnapshot");
    if (!clazz) return nullptr;
    jmethodID ctor = env->GetMethodID(
        clazz,
        "<init>",
        "(ZZZZFFLjava/lang/String;Ljava/lang/String;FFFFLjava/lang/String;FFFFFDJIILjava/lang/String;Ljava/lang/String;Ljava/lang/String;IILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZZLjava/lang/String;Ljava/lang/String;Ljava/lang/String;IZIJLjava/lang/String;ZLjava/lang/String;Ljava/lang/String;ZJFIILjava/lang/String;Ljava/lang/String;)V"
    );
    if (!ctor) return nullptr;
    return env->NewObject(
        clazz,
        ctor,
        static_cast<jboolean>(status.runtimeLoaded),
        static_cast<jboolean>(status.coreLoaded),
        static_cast<jboolean>(status.modelLoaded),
        static_cast<jboolean>(status.mouthParameterFound),
        status.inputMouthOpen,
        status.appliedMouthOpen,
        toJString(env, status.leftEyeParameterFound ? "APPLIED" : "LEFT_EYE_NOT_FOUND"),
        toJString(env, status.rightEyeParameterFound ? "APPLIED" : "RIGHT_EYE_NOT_FOUND"),
        status.inputLeftEyeOpen,
        status.inputRightEyeOpen,
        status.appliedLeftEyeOpen,
        status.appliedRightEyeOpen,
        toJString(env, status.breathParameterFound ? "APPLIED" : "BREATH_NOT_FOUND"),
        status.inputBreathNormalized,
        status.appliedBreathValue,
        status.breathMin,
        status.breathMax,
        status.breathDefault,
        status.renderFps,
        static_cast<jlong>(status.frameCount),
        static_cast<jint>(status.surfaceWidth),
        static_cast<jint>(status.surfaceHeight),
        toJString(env, status.modelName),
        toJString(env, status.mouthParameterId),
        toJString(env, status.lastError),
        static_cast<jint>(status.textureCount),
        static_cast<jint>(status.texturesLoaded),
        toJString(env, status.lastTexturePath),
        toJString(env, status.lastTextureError),
        toJString(env, status.glTextureIds),
        toJString(env, status.poseFile),
        static_cast<jboolean>(status.poseLoaded),
        static_cast<jboolean>(status.poseActive),
        static_cast<jboolean>(status.idleMotionEnabled),
        toJString(env, status.idleMotionStatus),
        toJString(env, status.idleMotionGroup),
        toJString(env, status.idleMotionFile),
        static_cast<jint>(status.idleMotionIndex),
        static_cast<jboolean>(status.idleMotionPlaying),
        static_cast<jint>(status.idleMotionCount),
        static_cast<jlong>(status.idleMotionPlayCount),
        toJString(env, status.lastIdleMotionError),
        static_cast<jboolean>(status.physicsEnabled),
        toJString(env, status.physicsStatus),
        toJString(env, status.physicsFile),
        static_cast<jboolean>(status.physicsLoaded),
        static_cast<jlong>(status.physicsUpdateCount),
        status.physicsLastDeltaMs,
        static_cast<jint>(status.physicsInputCount),
        static_cast<jint>(status.physicsOutputCount),
        toJString(env, status.physicsOutputParameterIds),
        toJString(env, status.lastPhysicsError)
    );
}
