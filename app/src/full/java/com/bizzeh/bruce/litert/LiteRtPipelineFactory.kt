package com.bizzeh.bruce.litert

/** The real LiteRT-LM pipeline (TASK-120); this flavour (`full`) has the library, so it is never null. */
fun createLiteRtPipeline(nativeLibraryDir: String): LiteRtPipeline = LiteRtPipelineImpl(LiteRtNative, nativeLibraryDir)
