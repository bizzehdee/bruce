# Unit tests need a JDK 21 or later, not a JRE

Observed 2026-09-28 on the development machine.

- `/usr/lib/jvm` holds only JREs (`java-latest-openjdk` is now 27). Gradle run on one fails with
  "Toolchain installation ... does not provide the required capabilities: [JAVA_COMPILER]".
- `~/jdk17` compiles, but every Robolectric test then fails with "Android SDK 36 requires
  Java 21 (have Java 17)".
- What works: `JAVA_HOME=~/.local/share/JetBrains/Toolbox/apps/android-studio/jbr ./gradlew ...`
  (Android Studio's bundled JBR).

Read when a Gradle run fails before any test runs, or when Robolectric fails to create a sandbox.
