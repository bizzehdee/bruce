# Host-side unit tests for native logic that does not depend on Android or llama.cpp.
cmake_minimum_required(VERSION 3.22.1)

project(bruce_native_tests LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(INSTALL_GTEST OFF CACHE BOOL "" FORCE)
set(BUILD_GMOCK OFF CACHE BOOL "" FORCE)
add_subdirectory(googletest)

set(MAIN_CPP ${CMAKE_CURRENT_LIST_DIR}/../../main/cpp)

add_executable(bruce_native_tests
    cpu_features_test.cpp
    ${MAIN_CPP}/cpu_features.cpp)
target_include_directories(bruce_native_tests PRIVATE ${MAIN_CPP})
target_link_libraries(bruce_native_tests PRIVATE GTest::gtest_main)

enable_testing()
include(GoogleTest)
gtest_discover_tests(bruce_native_tests)
