package com.bizzeh.bruce.benchmark

/** Excluded from normal device test runs (see `testInstrumentationRunnerArguments` in app/build.gradle.kts). */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Benchmark
