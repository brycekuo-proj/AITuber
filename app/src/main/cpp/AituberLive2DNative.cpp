#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <GLES2/gl2.h>

#include <algorithm>
#include <climits>
#include <cctype>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <memory>
#include <new>
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
#include <Utils/CubismJson.hpp>
#include <Rendering/OpenGL/CubismOffscreenManager_OpenGLES2.hpp>
#include <Rendering/OpenGL/CubismRenderer_OpenGLES2.hpp>
#include <Rendering/OpenGL/CubismShader_OpenGLES2.hpp>

#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"

namespace {

using namespace Live2D::Cubism::Framework;

constexpr const char* kLogTag = "AITuberLive2D";
constexpr const char* kDefaultModelAssetDir = "live2d/Haru";
constexpr const char* kDefaultModelName = "Haru";
constexpr const char* kDefaultModelJson = "Haru.model3.json";
constexpr const char* kFileSystemModelPrefix = "__AITUBER_FILE__:";
constexpr const char* kMouthParameterId = "ParamMouthOpenY";
constexpr const char* kLeftEyeParameterId = "ParamEyeLOpen";
constexpr const char* kRightEyeParameterId = "ParamEyeROpen";
constexpr const char* kBreathParameterId = "ParamBreath";
constexpr const char* kHeadXParameterId = "ParamAngleX";
constexpr const char* kHeadYParameterId = "ParamAngleY";
constexpr const char* kEarJiggleXParameterId = "physEarJiggleX";
constexpr const char* kEarJiggleYParameterId = "physEarJiggleY";
constexpr float kBreathIntensity = 0.30f;
constexpr const char* kShaderAssetDir = "live2d/framework/shaders";
constexpr const char* kIdleMotionGroup = "Idle";
constexpr int kPriorityIdle = 1;
constexpr csmFloat32 kMaxPhysicsDeltaSeconds = 0.1f;
// Limits for models installed from the store (untrusted files).
constexpr long kMaxModelFileBytes = 128L * 1024L * 1024L;
constexpr long kMaxJsonFileBytes = 16L * 1024L * 1024L;
constexpr int kMaxTextureEdge = 8192;
constexpr float kDefaultLayoutHeight = 2.0f;
constexpr float kDefaultLayoutOffsetY = -0.1f;

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
        // shouldCheckMotionConsistency: reject motions whose Meta counts do not match the data.
        return LoadMotion(buffer, size, nullptr, nullptr, nullptr, setting, group, index, true);
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
    std::string leftEyeParameterId = kLeftEyeParameterId;
    std::string rightEyeParameterId = kRightEyeParameterId;
    std::string breathParameterId = kBreathParameterId;
    bool headXParameterFound = false;
    bool headYParameterFound = false;
    float inputHeadX = 0.0f;
    float inputHeadY = 0.0f;
    bool earOutputsAvailable = false;
    float earJiggleX = 0.0f;
    float earJiggleY = 0.0f;
    std::string poseFile = "n/a";
    std::string lastTexturePath = "n/a";
    std::string lastTextureError = "n/a";
    std::string glTextureIds = "[]";
    std::string lastError;
};

struct Runtime {
    AAssetManager* assetManager = nullptr;
    bool modelFromFileSystem = false;
    std::string modelAssetDir = kDefaultModelAssetDir;
    std::string modelJsonFile = kDefaultModelJson;
    std::string modelName = kDefaultModelName;
    std::string mouthParameterId = kMouthParameterId;
    std::string leftEyeParameterId = kLeftEyeParameterId;
    std::string rightEyeParameterId = kRightEyeParameterId;
    std::string breathParameterId = kBreathParameterId;
    std::string headXParameterId = kHeadXParameterId;
    std::string headYParameterId = kHeadYParameterId;
    float layoutHeight = kDefaultLayoutHeight;
    float layoutOffsetY = kDefaultLayoutOffsetY;
    std::unique_ptr<SmokeModel> model;
    std::unique_ptr<CubismModelSettingJson> modelSetting;
    std::vector<GLuint> textures;
    const CubismId* mouthId = nullptr;
    const CubismId* leftEyeId = nullptr;
    const CubismId* rightEyeId = nullptr;
    const CubismId* breathId = nullptr;
    const CubismId* headXId = nullptr;
    const CubismId* headYId = nullptr;
    const CubismId* earJiggleXId = nullptr;
    const CubismId* earJiggleYId = nullptr;
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
// True once a model has been rendered in some GL context. The Cubism shader and
// offscreen singletons then hold objects of that context; a GLSurfaceView gets a
// new context after pause/resume or when another view takes over.
bool g_glObjectsMayBeStale = false;

// Drops Cubism's process-wide GL objects that belong to a previous context so
// they are recreated in the current one. Must run on the new context's thread.
void resetContextBoundSingletons() {
    if (!g_frameworkInitialized || !g_glObjectsMayBeStale) return;
    Rendering::CubismShader_OpenGLES2::GetInstance()->ReleaseInvalidShaderProgram();
    Rendering::CubismShader_OpenGLES2::DeleteInstance();
    Rendering::CubismOffscreenManager_OpenGLES2::ReleaseInstance();
    g_glObjectsMayBeStale = false;
}

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

bool readAsset(
    AAssetManager* manager,
    const std::string& path,
    std::vector<unsigned char>& out,
    std::string& error,
    long maxBytes = kMaxModelFileBytes
) {
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
    if (length > maxBytes) {
        AAsset_close(asset);
        error = "Asset too large: " + path;
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

bool readFileSystemFile(
    const std::string& path,
    std::vector<unsigned char>& out,
    std::string& error,
    long maxBytes = kMaxModelFileBytes
) {
    std::ifstream stream(path, std::ios::binary | std::ios::ate);
    if (!stream.is_open()) {
        error = "File not found: " + path;
        return false;
    }
    const std::streamsize length = stream.tellg();
    if (length <= 0) {
        error = "File empty: " + path;
        return false;
    }
    if (length > maxBytes) {
        error = "File too large: " + path;
        return false;
    }
    stream.seekg(0, std::ios::beg);
    out.resize(static_cast<size_t>(length));
    if (!stream.read(reinterpret_cast<char*>(out.data()), length)) {
        error = "File read failed: " + path;
        return false;
    }
    return true;
}

bool isPathWithinRoot(const std::string& root, const std::string& path) {
    char resolvedRoot[PATH_MAX];
    char resolvedPath[PATH_MAX];
    if (!realpath(root.c_str(), resolvedRoot) || !realpath(path.c_str(), resolvedPath)) {
        return false;
    }
    const std::string canonicalRoot(resolvedRoot);
    const std::string canonicalPath(resolvedPath);
    if (canonicalPath == canonicalRoot) return true;
    const std::string prefix = canonicalRoot + "/";
    return canonicalPath.compare(0, prefix.size(), prefix) == 0;
}

bool readModelFile(
    Runtime& runtime,
    const std::string& path,
    std::vector<unsigned char>& out,
    std::string& error,
    long maxBytes = kMaxModelFileBytes
) {
    if (runtime.modelFromFileSystem) {
        if (!isPathWithinRoot(runtime.modelAssetDir, path)) {
            error = "Model file escapes character directory: " + path;
            return false;
        }
        return readFileSystemFile(path, out, error, maxBytes);
    }
    return readAsset(runtime.assetManager, path, out, error, maxBytes);
}

// Reads a JSON file and checks that the Cubism parser accepts it. The SDK's
// setting / physics classes dereference the parse result without checking it,
// so a malformed file from a downloaded model would otherwise crash natively.
bool readModelJson(Runtime& runtime, const std::string& path, std::vector<unsigned char>& out, std::string& error) {
    if (!readModelFile(runtime, path, out, error, kMaxJsonFileBytes)) return false;
    Utils::CubismJson* json = Utils::CubismJson::Create(
        reinterpret_cast<const csmByte*>(out.data()),
        static_cast<csmSizeInt>(out.size())
    );
    if (!json) {
        error = "Invalid JSON: " + path;
        return false;
    }
    Utils::CubismJson::Delete(json);
    return true;
}

// Cubism allocates physics buffers from the Meta counts and indexes particles
// with Output.VertexIndex unchecked. Verify both against the actual data.
bool physicsJsonConsistent(const std::vector<unsigned char>& data, std::string& error) {
    Utils::CubismJson* json = Utils::CubismJson::Create(
        reinterpret_cast<const csmByte*>(data.data()),
        static_cast<csmSizeInt>(data.size())
    );
    if (!json) {
        error = "Invalid physics JSON";
        return false;
    }
    Utils::Value& root = json->GetRoot();
    Utils::Value& meta = root["Meta"];
    Utils::Value& settings = root["PhysicsSettings"];
    bool ok = settings.IsArray();
    int inputs = 0;
    int outputs = 0;
    int vertices = 0;
    const int settingCount = ok ? settings.GetSize() : 0;
    for (int i = 0; ok && i < settingCount; i++) {
        Utils::Value& setting = settings[i];
        // The SDK calls GetVector() on these; a missing or non-array value is a null dereference.
        if (!setting["Vertices"].IsArray() || !setting["Input"].IsArray() || !setting["Output"].IsArray()) {
            ok = false;
            break;
        }
        const int vertexCount = setting["Vertices"].GetSize();
        if (vertexCount <= 0) ok = false;
        const int outputCount = setting["Output"].GetSize();
        for (int j = 0; ok && j < outputCount; j++) {
            const int vertexIndex = setting["Output"][j]["VertexIndex"].ToInt(-1);
            if (vertexIndex < 0 || vertexIndex >= vertexCount) ok = false;
        }
        inputs += setting["Input"].GetSize();
        outputs += outputCount;
        vertices += vertexCount;
    }
    if (ok) {
        ok = meta["PhysicsSettingCount"].ToInt(-1) == settingCount &&
            meta["TotalInputCount"].ToInt(-1) == inputs &&
            meta["TotalOutputCount"].ToInt(-1) == outputs &&
            meta["VertexCount"].ToInt(-1) == vertices;
    }
    Utils::CubismJson::Delete(json);
    if (!ok) error = "Physics Meta counts do not match data";
    return ok;
}

// A parameter spec is "Id" or "IdA|IdB|IdC". Returns the first id the model
// has, or the first candidate when none exists (reported as NOT_FOUND later).
std::string resolveParameterSpec(CubismModel* model, const std::string& spec) {
    std::vector<std::string> candidates;
    size_t start = 0;
    while (start <= spec.size()) {
        const size_t end = spec.find('|', start);
        const std::string part = spec.substr(start, end == std::string::npos ? std::string::npos : end - start);
        if (!part.empty()) candidates.push_back(part);
        if (end == std::string::npos) break;
        start = end + 1;
    }
    if (candidates.empty()) return spec;
    if (model) {
        for (const auto& candidate : candidates) {
            const CubismId* id = CubismFramework::GetIdManager()->GetId(candidate.c_str());
            const csmInt32 index = model->GetParameterIndex(id);
            if (index >= 0 && index < model->GetParameterCount()) return candidate;
        }
    }
    return candidates.front();
}

bool isPowerOfTwo(int value) {
    return value > 0 && (value & (value - 1)) == 0;
}

// Halves an RGBA image with a 2x2 box filter.
void halveRgba(std::vector<unsigned char>& pixels, int& width, int& height) {
    const int newWidth = std::max(1, width / 2);
    const int newHeight = std::max(1, height / 2);
    std::vector<unsigned char> out(static_cast<size_t>(newWidth) * newHeight * 4);
    for (int y = 0; y < newHeight; y++) {
        for (int x = 0; x < newWidth; x++) {
            for (int c = 0; c < 4; c++) {
                int sum = 0;
                int count = 0;
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        const int sx = std::min(width - 1, x * 2 + dx);
                        const int sy = std::min(height - 1, y * 2 + dy);
                        sum += pixels[(static_cast<size_t>(sy) * width + sx) * 4 + c];
                        count++;
                    }
                }
                out[(static_cast<size_t>(y) * newWidth + x) * 4 + c] = static_cast<unsigned char>(sum / count);
            }
        }
    }
    pixels.swap(out);
    width = newWidth;
    height = newHeight;
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
    if (!readModelFile(runtime, path, png, error)) {
        runtime.status.lastTextureError = error;
        return false;
    }

    int width = 0;
    int height = 0;
    int channels = 0;
    // Check the size before decoding: a huge texture would exhaust memory.
    if (!stbi_info_from_memory(png.data(), static_cast<int>(png.size()), &width, &height, &channels) ||
        width <= 0 || height <= 0 || width > kMaxTextureEdge || height > kMaxTextureEdge) {
        error = "Texture unreadable or larger than " + std::to_string(kMaxTextureEdge) + "px: " + path;
        runtime.status.lastTextureError = error;
        return false;
    }
    GLint maxTextureSize = 0;
    glGetIntegerv(GL_MAX_TEXTURE_SIZE, &maxTextureSize);
    if (maxTextureSize <= 0) maxTextureSize = 2048;
    if (width > maxTextureSize * 2 || height > maxTextureSize * 2) {
        error = "Texture " + std::to_string(width) + "x" + std::to_string(height) +
            " is too large for this GPU (max " + std::to_string(maxTextureSize) + "): " + path;
        runtime.status.lastTextureError = error;
        return false;
    }
    stbi_uc* decoded = stbi_load_from_memory(
        png.data(),
        static_cast<int>(png.size()),
        &width,
        &height,
        &channels,
        STBI_rgb_alpha
    );
    if (!decoded || width <= 0 || height <= 0) {
        if (decoded) stbi_image_free(decoded);
        error = "PNG decode failed: " + path;
        runtime.status.lastTextureError = error;
        return false;
    }
    png.clear();
    png.shrink_to_fit();

    // Downscale textures the GPU cannot hold (UVs are normalized, so the model still maps correctly).
    std::unique_ptr<stbi_uc, void (*)(void*)> decodedOwner(decoded, stbi_image_free);
    std::vector<unsigned char> scaled;
    unsigned char* pixels = decoded;
    if (width > maxTextureSize || height > maxTextureSize) {
        scaled.assign(decoded, decoded + static_cast<size_t>(width) * height * 4);
        decodedOwner.reset();
        while (width > maxTextureSize || height > maxTextureSize) {
            halveRgba(scaled, width, height);
        }
        pixels = scaled.data();
    }

    glGenTextures(1, &textureId);
    if (textureId == 0) {
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
        error = "glTexImage2D failed for " + path + ": " + glErrorToString(uploadError);
        runtime.status.lastTextureError = error;
        textureId = 0;
        return false;
    }
    // OpenGL ES 2 cannot build mipmaps for non-power-of-two textures; keep LINEAR for those.
    if (isPowerOfTwo(width) && isPowerOfTwo(height)) {
        glGenerateMipmap(GL_TEXTURE_2D);
        const GLenum mipmapError = glGetError();
        if (mipmapError == GL_NO_ERROR) {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        } else {
            logError("glGenerateMipmap failed for " + path + ": " + glErrorToString(mipmapError) + " (using LINEAR)");
        }
    }
    glBindTexture(GL_TEXTURE_2D, 0);
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
    if (!readModelJson(runtime, motionPath, motionJson, error)) {
        // Stop retrying every frame (e.g. the character folder was replaced).
        runtime.status.idleMotionEnabled = false;
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
        runtime.status.idleMotionEnabled = false;
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
    if (!readModelJson(runtime, physicsPath, physicsJson, error)) {
        runtime.status.physicsStatus = "PHYSICS_NOT_FOUND";
        runtime.status.lastPhysicsError = error;
        logError("Physics unavailable: " + error);
        return;
    }

    if (!physicsJsonConsistent(physicsJson, error)) {
        runtime.status.physicsStatus = "PHYSICS_FAILED";
        runtime.status.lastPhysicsError = error + ": " + physicsPath;
        logError(runtime.status.lastPhysicsError);
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
    runtime.status.headXParameterFound = false;
    runtime.status.headYParameterFound = false;
    runtime.status.earOutputsAvailable = false;
    runtime.status.earJiggleX = 0.0f;
    runtime.status.earJiggleY = 0.0f;
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
    const std::string modelJsonPath = joinAssetPath(runtime.modelAssetDir, runtime.modelJsonFile);
    if (!readModelJson(runtime, modelJsonPath, modelJson, error)) {
        runtime.status.lastError = error;
        return false;
    }

    runtime.modelSetting.reset(new CubismModelSettingJson(
        reinterpret_cast<const csmByte*>(modelJson.data()),
        static_cast<csmSizeInt>(modelJson.size())
    ));
    if (!runtime.modelSetting->GetJsonPointer()) {
        runtime.modelSetting.reset();
        runtime.status.lastError = "model3.json parse failed";
        return false;
    }

    const char* mocFile = runtime.modelSetting->GetModelFileName();
    if (!mocFile || std::strlen(mocFile) == 0) {
        runtime.status.lastError = "model3.json has no moc file";
        return false;
    }
    std::vector<unsigned char> moc;
    if (!readModelFile(runtime, joinAssetPath(runtime.modelAssetDir, mocFile), moc, error)) {
        runtime.status.lastError = error;
        return false;
    }

    // shouldCheckMocConsistency: the Core validates the whole moc3 before using it.
    runtime.model->LoadModel(
        reinterpret_cast<const csmByte*>(moc.data()),
        static_cast<csmSizeInt>(moc.size()),
        true
    );
    if (!runtime.model->model()) {
        runtime.status.lastError = "Cubism model load failed (corrupt moc3 or newer than this runtime)";
        return false;
    }

    csmMap<csmString, csmFloat32> layout;
    runtime.modelSetting->GetLayoutMap(layout);
    if (runtime.model->matrix()) {
        runtime.model->matrix()->SetupFromLayout(layout);
        const float height = runtime.layoutHeight > 0.05f && runtime.layoutHeight < 20.0f
            ? runtime.layoutHeight
            : kDefaultLayoutHeight;
        runtime.model->matrix()->SetHeight(height);
        runtime.model->matrix()->TranslateY(runtime.layoutOffsetY);
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
        if (!textureFile || std::strlen(textureFile) == 0) {
            // Skipping would shift every later texture onto the wrong slot.
            runtime.status.lastError = "model3.json texture #" + std::to_string(i) + " has no file name";
            return false;
        }
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
        if (readModelFile(runtime, posePath, poseJson, error)) {
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

    CubismModel* loadedModel = runtime.model->model();
    const std::string mouthParam = resolveParameterSpec(loadedModel, runtime.mouthParameterId);
    const std::string leftEyeParam = resolveParameterSpec(loadedModel, runtime.leftEyeParameterId);
    const std::string rightEyeParam = resolveParameterSpec(loadedModel, runtime.rightEyeParameterId);
    const std::string breathParam = resolveParameterSpec(loadedModel, runtime.breathParameterId);
    const std::string headXParam = resolveParameterSpec(loadedModel, runtime.headXParameterId);
    const std::string headYParam = resolveParameterSpec(loadedModel, runtime.headYParameterId);
    runtime.status.mouthParameterId = mouthParam;
    runtime.status.leftEyeParameterId = leftEyeParam;
    runtime.status.rightEyeParameterId = rightEyeParam;
    runtime.status.breathParameterId = breathParam;
    runtime.mouthId = CubismFramework::GetIdManager()->GetId(mouthParam.c_str());
    runtime.leftEyeId = CubismFramework::GetIdManager()->GetId(leftEyeParam.c_str());
    runtime.rightEyeId = CubismFramework::GetIdManager()->GetId(rightEyeParam.c_str());
    runtime.breathId = CubismFramework::GetIdManager()->GetId(breathParam.c_str());
    runtime.headXId = CubismFramework::GetIdManager()->GetId(headXParam.c_str());
    runtime.headYId = CubismFramework::GetIdManager()->GetId(headYParam.c_str());
    runtime.earJiggleXId = CubismFramework::GetIdManager()->GetId(kEarJiggleXParameterId);
    runtime.earJiggleYId = CubismFramework::GetIdManager()->GetId(kEarJiggleYParameterId);
    const csmInt32 mouthIndex = runtime.model->model()->GetParameterIndex(runtime.mouthId);
    const bool found = mouthIndex >= 0 && mouthIndex < runtime.model->model()->GetParameterCount();
    runtime.status.mouthParameterFound = found;
    const csmInt32 headXIndex = runtime.model->model()->GetParameterIndex(runtime.headXId);
    const csmInt32 headYIndex = runtime.model->model()->GetParameterIndex(runtime.headYId);
    runtime.status.headXParameterFound = headXIndex >= 0 && headXIndex < runtime.model->model()->GetParameterCount();
    runtime.status.headYParameterFound = headYIndex >= 0 && headYIndex < runtime.model->model()->GetParameterCount();
    const csmInt32 earXIndex = runtime.model->model()->GetParameterIndex(runtime.earJiggleXId);
    const csmInt32 earYIndex = runtime.model->model()->GetParameterIndex(runtime.earJiggleYId);
    runtime.status.earOutputsAvailable =
        earXIndex >= 0 && earXIndex < runtime.model->model()->GetParameterCount() &&
        earYIndex >= 0 && earYIndex < runtime.model->model()->GetParameterCount();
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
        runtime.status.lastError = runtime.status.mouthParameterId + " not found (lip sync unavailable)";
    } else if (!poseError) {
        runtime.status.lastError = "";
    }
    g_glObjectsMayBeStale = true;
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
    runtime.headXId = nullptr;
    runtime.headYId = nullptr;
    runtime.earJiggleXId = nullptr;
    runtime.earJiggleYId = nullptr;
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

bool setParameterLocked(CubismModel* model, const CubismId* id, float value, float* appliedValue = nullptr) {
    if (!model || !id) return false;
    const csmInt32 index = model->GetParameterIndex(id);
    if (index < 0 || index >= model->GetParameterCount()) return false;
    const float minValue = model->GetParameterMinimumValue(static_cast<csmUint32>(index));
    const float maxValue = model->GetParameterMaximumValue(static_cast<csmUint32>(index));
    const float safeMin = std::min(minValue, maxValue);
    const float safeMax = std::max(minValue, maxValue);
    const float clamped = std::max(safeMin, std::min(safeMax, value));
    model->SetParameterValue(index, clamped, 1.0f);
    if (appliedValue) {
        *appliedValue = clamped;
    }
    return true;
}

bool readParameterLocked(CubismModel* model, const CubismId* id, float* value) {
    if (!model || !id || !value) return false;
    const csmInt32 index = model->GetParameterIndex(id);
    if (index < 0 || index >= model->GetParameterCount()) return false;
    *value = model->GetParameterValue(static_cast<csmUint32>(index));
    return true;
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

std::string readJString(JNIEnv* env, jstring value, const char* fallback) {
    if (!value) return fallback;
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars ? chars : fallback;
    if (chars) {
        env->ReleaseStringUTFChars(value, chars);
    }
    return result.empty() ? fallback : result;
}

} // namespace

namespace {

jboolean initializeRuntime(
    JNIEnv* env,
    jobject assetManager,
    jstring modelAssetDir,
    jstring modelName,
    jstring model3File,
    jstring mouthParameterId,
    jstring leftEyeParameterId,
    jstring rightEyeParameterId,
    jstring breathParameterId,
    jstring headXParameterId,
    jstring headYParameterId,
    jfloat layoutHeight,
    jfloat layoutOffsetY
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const std::string encodedModelDir = readJString(env, modelAssetDir, kDefaultModelAssetDir);
    const bool modelFromFileSystem = encodedModelDir.compare(0, std::strlen(kFileSystemModelPrefix), kFileSystemModelPrefix) == 0;
    const std::string modelDir = modelFromFileSystem
        ? encodedModelDir.substr(std::strlen(kFileSystemModelPrefix))
        : encodedModelDir;
    const std::string name = readJString(env, modelName, kDefaultModelName);
    const std::string jsonFile = readJString(env, model3File, kDefaultModelJson);
    const std::string mouthId = readJString(env, mouthParameterId, kMouthParameterId);
    const std::string leftEyeId = readJString(env, leftEyeParameterId, kLeftEyeParameterId);
    const std::string rightEyeId = readJString(env, rightEyeParameterId, kRightEyeParameterId);
    const std::string breathId = readJString(env, breathParameterId, kBreathParameterId);
    const std::string headXId = readJString(env, headXParameterId, kHeadXParameterId);
    const std::string headYId = readJString(env, headYParameterId, kHeadYParameterId);

    AAssetManager* manager = AAssetManager_fromJava(env, assetManager);
    if (!manager) {
        g_runtime.reset(new Runtime());
        g_runtime->status.lastError = "AAssetManager_fromJava failed";
        return JNI_FALSE;
    }

    g_runtime.reset(new Runtime());
    g_runtime->assetManager = manager;
    g_runtime->modelFromFileSystem = modelFromFileSystem;
    g_assetManager = manager;
    g_runtime->modelAssetDir = modelDir;
    g_runtime->modelName = name;
    g_runtime->modelJsonFile = jsonFile;
    g_runtime->mouthParameterId = mouthId;
    g_runtime->leftEyeParameterId = leftEyeId;
    g_runtime->rightEyeParameterId = rightEyeId;
    g_runtime->breathParameterId = breathId;
    g_runtime->headXParameterId = headXId;
    g_runtime->headYParameterId = headYId;
    g_runtime->layoutHeight = static_cast<float>(layoutHeight);
    g_runtime->layoutOffsetY = std::isfinite(layoutOffsetY) ? static_cast<float>(layoutOffsetY) : kDefaultLayoutOffsetY;
    g_runtime->status.modelName = name;
    g_runtime->status.mouthParameterId = mouthId;
    return JNI_TRUE;
}

} // namespace

// Lets Kotlin check that this library can load downloaded characters.
extern "C" JNIEXPORT jint JNICALL
Java_com_aituber_poc_character_live2d_Live2DNativeBridge_nativeApiVersion(JNIEnv*, jclass) {
    return 2;
}

// Current entry point: parameter ids may be "A|B|C" candidate lists.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_aituber_poc_character_live2d_Live2DNativeBridge_nativeInitializeV2(
    JNIEnv* env,
    jobject,
    jobject assetManager,
    jstring modelAssetDir,
    jstring modelName,
    jstring model3File,
    jstring mouthParameterId,
    jstring leftEyeParameterId,
    jstring rightEyeParameterId,
    jstring breathParameterId,
    jstring headXParameterId,
    jstring headYParameterId,
    jfloat layoutHeight,
    jfloat layoutOffsetY
) {
    return initializeRuntime(
        env, assetManager, modelAssetDir, modelName, model3File,
        mouthParameterId, leftEyeParameterId, rightEyeParameterId, breathParameterId,
        headXParameterId, headYParameterId, layoutHeight, layoutOffsetY
    );
}

// Original entry point, kept so older Kotlin callers keep working.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_aituber_poc_character_live2d_Live2DNativeBridge_nativeInitialize(
    JNIEnv* env,
    jobject,
    jobject assetManager,
    jstring modelAssetDir,
    jstring modelName,
    jstring model3File,
    jstring mouthParameterId,
    jstring leftEyeParameterId,
    jstring rightEyeParameterId,
    jstring breathParameterId
) {
    return initializeRuntime(
        env, assetManager, modelAssetDir, modelName, model3File,
        mouthParameterId, leftEyeParameterId, rightEyeParameterId, breathParameterId,
        nullptr, nullptr, kDefaultLayoutHeight, kDefaultLayoutOffsetY
    );
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_aituber_poc_character_live2d_Live2DNativeBridge_nativeOnSurfaceCreated(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_runtime) return JNI_FALSE;

    if (!validateGlContext(*g_runtime)) {
        return JNI_FALSE;
    }
    discardContextBoundResources(*g_runtime);
    resetContextBoundSingletons();
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
        // A C++ exception must not cross JNI: it would abort the whole app.
        try {
            if (loadModel(*g_runtime)) return JNI_TRUE;
        } catch (const std::bad_alloc&) {
            g_runtime->status.lastError = "Out of memory while loading the model";
        } catch (...) {
            g_runtime->status.lastError = "Unexpected error while loading the model";
        }
        g_runtime->status.modelLoaded = false;
        releaseTextures(*g_runtime);
        g_runtime->model.reset();
        g_runtime->modelSetting.reset();
        return JNI_FALSE;
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
    g_runtime->status.inputMouthOpen = value;
    if (!g_runtime->model || !g_runtime->model->model() || !g_runtime->mouthId) return;
    if (setParameterLocked(g_runtime->model->model(), g_runtime->mouthId, value, &g_runtime->status.appliedMouthOpen)) {
        g_runtime->status.mouthParameterFound = true;
    } else {
        g_runtime->status.mouthParameterFound = false;
        g_runtime->status.lastError = g_runtime->status.mouthParameterId + " not found";
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_aituber_poc_character_live2d_Live2DNativeBridge_nativeSetHeadInput(
    JNIEnv*,
    jobject,
    jfloat headX,
    jfloat headY
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_runtime) return;
    g_runtime->status.inputHeadX = headX;
    g_runtime->status.inputHeadY = headY;
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
    try {
        return startIdleMotionLocked(*g_runtime, true) ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        g_runtime->status.idleMotionEnabled = false;
        g_runtime->status.lastIdleMotionError = "Unexpected error while loading idle motion";
        return JNI_FALSE;
    }
}

namespace {

void drawFrameLocked() {
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
    if (!g_runtime->status.idleMotionEnabled) {
        g_runtime->status.headXParameterFound = setParameterLocked(model, g_runtime->headXId, g_runtime->status.inputHeadX);
        g_runtime->status.headYParameterFound = setParameterLocked(model, g_runtime->headYId, g_runtime->status.inputHeadY);
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
        float earX = 0.0f;
        float earY = 0.0f;
        const bool hasEarX = readParameterLocked(model, g_runtime->earJiggleXId, &earX);
        const bool hasEarY = readParameterLocked(model, g_runtime->earJiggleYId, &earY);
        g_runtime->status.earOutputsAvailable = hasEarX && hasEarY;
        g_runtime->status.earJiggleX = earX;
        g_runtime->status.earJiggleY = earY;
    }
    if (g_runtime->mouthId) {
        if (setParameterLocked(model, g_runtime->mouthId, g_runtime->status.inputMouthOpen, &g_runtime->status.appliedMouthOpen)) {
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

} // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_aituber_poc_character_live2d_Live2DNativeBridge_nativeOnDrawFrame(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    // A C++ exception must not cross JNI: it would abort the whole app.
    try {
        drawFrameLocked();
    } catch (...) {
        if (g_runtime) {
            g_runtime->status.lastError = "Unexpected error while drawing the model";
            g_runtime->status.idleMotionEnabled = false;
        }
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
        // Called on the owning GL thread, so these deletes hit the right context.
        Rendering::CubismOffscreenManager_OpenGLES2::ReleaseInstance();
        CubismFramework::Dispose();
        g_frameworkInitialized = false;
    }
    g_glObjectsMayBeStale = false;
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
        "(ZZZZFFLjava/lang/String;Ljava/lang/String;FFFFLjava/lang/String;FFFFFDJIILjava/lang/String;Ljava/lang/String;Ljava/lang/String;IILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZZLjava/lang/String;Ljava/lang/String;Ljava/lang/String;IZIJLjava/lang/String;ZLjava/lang/String;Ljava/lang/String;ZJFIILjava/lang/String;Ljava/lang/String;ZFF)V"
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
        toJString(env, status.lastPhysicsError),
        static_cast<jboolean>(status.earOutputsAvailable),
        status.earJiggleX,
        status.earJiggleY
    );
}
