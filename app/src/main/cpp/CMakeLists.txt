cmake_minimum_required(VERSION 3.22.1)

project(aituber_live2d)

if(NOT DEFINED LIVE2D_SDK_DIR)
    message(FATAL_ERROR "LIVE2D_SDK_DIR is required for the optional Live2D native build")
endif()

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(CORE_PATH ${LIVE2D_SDK_DIR}/Core)
set(FRAMEWORK_PATH ${LIVE2D_SDK_DIR}/Framework)
set(THIRD_PARTY_PATH ${LIVE2D_SDK_DIR}/Samples/OpenGL/thirdParty)
set(STB_PATH ${THIRD_PARTY_PATH}/stb)

add_library(Live2DCubismCore STATIC IMPORTED)
set_target_properties(Live2DCubismCore
    PROPERTIES
        IMPORTED_LOCATION ${CORE_PATH}/lib/android/${ANDROID_ABI}/libLive2DCubismCore.a
        INTERFACE_INCLUDE_DIRECTORIES ${CORE_PATH}/include
)

set(FRAMEWORK_SOURCE OpenGL)
add_subdirectory(${FRAMEWORK_PATH} ${CMAKE_CURRENT_BINARY_DIR}/Framework)
target_compile_definitions(Framework PUBLIC CSM_TARGET_ANDROID_ES2)

add_library(aituber_live2d SHARED
    AituberLive2DNative.cpp
)

target_include_directories(aituber_live2d PRIVATE
    ${CORE_PATH}/include
    ${FRAMEWORK_PATH}/src
    ${STB_PATH}
)

target_compile_definitions(aituber_live2d PRIVATE
    CSM_TARGET_ANDROID_ES2
)

target_link_libraries(aituber_live2d
    Framework
    Live2DCubismCore
    GLESv2
    android
    log
)

target_link_options(aituber_live2d PRIVATE "-Wl,-z,max-page-size=16384")
