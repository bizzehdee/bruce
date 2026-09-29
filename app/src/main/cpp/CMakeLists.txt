cmake_minimum_required(VERSION 3.22.1)

project(bruce LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(EXTERNAL_DIR ${CMAKE_CURRENT_LIST_DIR}/external)

# Ninja otherwise runs one compile per core; several llama.cpp translation units (the Vulkan
# backend above all) need gigabytes each, which exhausted a 32-core workstation's memory.
set_property(GLOBAL PROPERTY JOB_POOLS compile_pool=8 link_pool=4)
set(CMAKE_JOB_POOL_COMPILE compile_pool)
set(CMAKE_JOB_POOL_LINK link_pool)

# ggml backends are separate libraries loaded at runtime, so one APK carries a CPU
# variant per ARM feature level plus GPU backends, and the phone picks what it can run.
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
# The build host is not the target device.
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
# llama.cpp documents llamafile as unsupported on Android.
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

set(LLAMA_BUILD_IS_DEV OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)

# Vulkan: the NDK provides libvulkan but not vulkan.hpp or SPIR-V headers, and its glslc
# is too old for ggml's cooperative-matrix shaders, so glslc comes from the host PATH.
set(GGML_VULKAN ON CACHE BOOL "" FORCE)
set(Vulkan_INCLUDE_DIR ${EXTERNAL_DIR}/Vulkan-Headers/include CACHE PATH "" FORCE)
find_program(HOST_GLSLC glslc NO_CMAKE_FIND_ROOT_PATH REQUIRED)
set(Vulkan_GLSLC_EXECUTABLE ${HOST_GLSLC} CACHE FILEPATH "" FORCE)
# ggml finds SPIR-V headers as an installed package and expects them on the system include
# path; this stand-in package config adds the in-tree copy to the including directory.
set(SPIRV_HEADERS_CONFIG_DIR ${CMAKE_CURRENT_BINARY_DIR}/spirv-headers-config)
file(WRITE ${SPIRV_HEADERS_CONFIG_DIR}/SPIRV-HeadersConfig.cmake
    "include_directories(SYSTEM \"${EXTERNAL_DIR}/SPIRV-Headers/include\")\n")
set(SPIRV-Headers_DIR ${SPIRV_HEADERS_CONFIG_DIR} CACHE PATH "" FORCE)

# OpenCL: link against the Khronos ICD loader, but do not ship it. At runtime the
# phone's own vendor libOpenCL.so is used (see <uses-native-library> in the manifest).
set(GGML_OPENCL ON CACHE BOOL "" FORCE)
set(ENABLE_OPENCL_LAYERS OFF CACHE BOOL "" FORCE)
set(OPENCL_ICD_LOADER_HEADERS_DIR ${EXTERNAL_DIR}/OpenCL-Headers CACHE PATH "" FORCE)
add_subdirectory(${EXTERNAL_DIR}/OpenCL-ICD-Loader opencl-icd-loader EXCLUDE_FROM_ALL)
set(OpenCL_INCLUDE_DIR ${EXTERNAL_DIR}/OpenCL-Headers CACHE PATH "" FORCE)
set(OpenCL_LIBRARY OpenCL CACHE STRING "" FORCE)

add_subdirectory(llama.cpp)

# Tool-call formats (docs/adr/0001-tool-calling-format.md): llama.cpp's chat, template, parser and
# grammar code from common, built here rather than as llama-common, so its download, HTTP,
# argument, console and subprocess code never enters the app. cpp-httplib is not even defined
# (LLAMA_BUILD_COMMON is off), so if an upgrade makes this code need it, the build fails.
set(LLAMA_COMMON_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/common)
include(${LLAMA_COMMON_DIR}/parsers/sources.cmake)
set(LLAMA_BUILD_NUMBER 0)
set(LLAMA_BUILD_COMMIT "pinned submodule")
set(BUILD_COMPILER "NDK clang")
set(BUILD_TARGET ${ANDROID_ABI})
configure_file(${LLAMA_COMMON_DIR}/build-info.cpp.in ${CMAKE_CURRENT_BINARY_DIR}/build-info.cpp)
add_library(bruce_chat STATIC
    ${LLAMA_CHAT_PARSERS_SOURCES}
    ${CMAKE_CURRENT_BINARY_DIR}/build-info.cpp
    ${LLAMA_COMMON_DIR}/chat-auto-parser-generator.cpp
    ${LLAMA_COMMON_DIR}/chat-auto-parser-helpers.cpp
    ${LLAMA_COMMON_DIR}/chat-diff-analyzer.cpp
    ${LLAMA_COMMON_DIR}/chat-peg-parser.cpp
    ${LLAMA_COMMON_DIR}/chat.cpp
    ${LLAMA_COMMON_DIR}/common.cpp
    ${LLAMA_COMMON_DIR}/debug.cpp
    ${LLAMA_COMMON_DIR}/fit.cpp
    ${LLAMA_COMMON_DIR}/json-schema-to-grammar.cpp
    ${LLAMA_COMMON_DIR}/json-schema.cpp
    ${LLAMA_COMMON_DIR}/json.cpp
    ${LLAMA_COMMON_DIR}/log.cpp
    ${LLAMA_COMMON_DIR}/ngram-cache.cpp
    ${LLAMA_COMMON_DIR}/ngram-map.cpp
    ${LLAMA_COMMON_DIR}/ngram-mod.cpp
    ${LLAMA_COMMON_DIR}/peg-parser.cpp
    ${LLAMA_COMMON_DIR}/reasoning-budget.cpp
    ${LLAMA_COMMON_DIR}/sampling.cpp
    ${LLAMA_COMMON_DIR}/speculative.cpp
    ${LLAMA_COMMON_DIR}/trie.cpp
    ${LLAMA_COMMON_DIR}/unicode.cpp
    ${LLAMA_COMMON_DIR}/jinja/caps.cpp
    ${LLAMA_COMMON_DIR}/jinja/lexer.cpp
    ${LLAMA_COMMON_DIR}/jinja/parser.cpp
    ${LLAMA_COMMON_DIR}/jinja/runtime.cpp
    ${LLAMA_COMMON_DIR}/jinja/string.cpp
    ${LLAMA_COMMON_DIR}/jinja/value.cpp
)
set_target_properties(bruce_chat PROPERTIES POSITION_INDEPENDENT_CODE ON)
target_include_directories(bruce_chat PUBLIC ${LLAMA_COMMON_DIR})
target_compile_features(bruce_chat PUBLIC cxx_std_17)
target_link_libraries(bruce_chat PUBLIC llama vendor::nlohmann vendor::sheredom)

add_library(bruce SHARED llama_jni.cpp chat_tools.cpp cpu_features.cpp prompt_cache.cpp vulkan_devices.cpp)
target_include_directories(bruce PRIVATE ${EXTERNAL_DIR}/Vulkan-Headers/include)
target_link_libraries(bruce PRIVATE llama ggml log vulkan bruce_chat)
add_custom_command(TARGET bruce POST_BUILD
    COMMAND ${CMAKE_COMMAND} -DNM=${CMAKE_NM} -DLIBRARY=$<TARGET_FILE:bruce> -P ${CMAKE_CURRENT_SOURCE_DIR}/check_no_network.cmake
    VERBATIM)
