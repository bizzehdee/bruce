# Building Bruce

This guide is for developers building Bruce from source.

## Requirements

- JDK 21 or later, a full JDK rather than a JRE. Robolectric's Android SDK 36 runtime
  refuses to start on JDK 17.
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
takes several minutes. The native build fails if `libbruce` imports network functions such as
`connect` (`app/src/main/cpp/check_no_network.cmake`).

## Manual device tests

Tests marked `@ManualOnly` are skipped by `connectedDebugAndroidTest`: benchmarks, tests that
need the internet, tests that can freeze a phone, and tests that need a real model. The
Gradle device run uninstalls the app afterwards, so install and run these by hand:

```
./gradlew installDebug installDebugAndroidTest
adb shell am instrument -w -e class <test class> com.bizzeh.bruce.test/androidx.test.runner.AndroidJUnitRunner
```

`ToolCallDeviceTest` needs Qwen3.5-0.8B-Q8_0.gguf (from `ggml-org/Qwen3.5-0.8B-GGUF`, SHA-256
`37ae482d336108d23516fa35e8e0c4126688d81018b87178a18d752a1357814f`) in the app's private
storage. Copy it in through the debuggable app's own user:

```
adb push Qwen3.5-0.8B-Q8_0.gguf /data/local/tmp/
adb shell "run-as com.bizzeh.bruce mkdir -p files/test-models"
adb shell "cat /data/local/tmp/Qwen3.5-0.8B-Q8_0.gguf | run-as com.bizzeh.bruce sh -c 'cat > files/test-models/Qwen3.5-0.8B-Q8_0.gguf'"
```

## Regenerating the icons

`docs/branding/export-icons.sh` rebuilds the launcher icon layers and the Play Store icon
from `docs/branding/bruce-logo.svg`. It needs ImageMagick 7 with librsvg support
(`magick -list format | grep RSVG`).
