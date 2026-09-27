cmake_minimum_required(VERSION 3.22.1)

project(bruce LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(EXTERNAL_DIR ${CMAKE_CURRENT_LIST_DIR}/external)

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

add_library(bruce SHARED llama_jni.cpp cpu_features.cpp vulkan_devices.cpp)
target_include_directories(bruce PRIVATE ${EXTERNAL_DIR}/Vulkan-Headers/include)
target_link_libraries(bruce PRIVATE llama ggml log vulkan)
