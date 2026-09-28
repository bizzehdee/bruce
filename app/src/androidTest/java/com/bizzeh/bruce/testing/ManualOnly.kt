package com.bizzeh.bruce.testing

/**
 * Excluded from normal device test runs (see `testInstrumentationRunnerArguments` in
 * app/build.gradle.kts): benchmarks, and tests that need the internet. Run one with
 * `adb shell am instrument -w -e class <test class> com.bizzeh.bruce.test/androidx.test.runner.AndroidJUnitRunner`.
 * AndroidJUnitRunner honoured only the first class of a comma-separated `notAnnotation`
 * list, so this single annotation covers every manual-only test.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class ManualOnly(val reason: String)
