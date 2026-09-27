# Building Bruce

This guide is for developers building Bruce from source.

## Requirements

- JDK 17 or later.
- Android SDK with platform `android-37.0`, NDK `30.0.16248370` and CMake `4.1.2`.
- `glslc` from shaderc 2023 or later on `PATH`. The NDK's own `glslc` is too old for
  llama.cpp's Vulkan shaders. On Fedora: `sudo dnf install glslc`. On Debian or Ubuntu:
  `sudo apt-get install glslc`.
- A C++ compiler for the build machine (`g++` or `clang++`). The Vulkan backend builds a
  shader generator that runs on the build machine.

## Steps

1. Clone with submodules:
   `git clone --recurse-submodules <repository-url>`.
   For an existing clone, run `git submodule update --init`.
2. Point Gradle at the Android SDK, either with the `ANDROID_HOME` environment variable or
   with `sdk.dir=<path>` in `local.properties`. `local.properties` is ignored by git.
3. Build and run the JVM tests: `./gradlew build`.
4. Run the on-device tests with a phone connected over adb: `./gradlew connectedDebugAndroidTest`.
5. Run the native unit tests:

   ```
   cmake -S app/src/test/cpp -B build/native-tests
   cmake --build build/native-tests
   ctest --test-dir build/native-tests
   ```

The first native build compiles llama.cpp, seven CPU variants and the Vulkan shaders. It
takes several minutes.

## Regenerating the icons

`docs/branding/export-icons.sh` rebuilds the launcher icon layers and the Play Store icon
from `docs/branding/bruce-logo.svg`. It needs ImageMagick 7 with librsvg support
(`magick -list format | grep RSVG`).
