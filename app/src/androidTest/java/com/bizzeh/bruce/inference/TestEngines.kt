package com.bizzeh.bruce.inference

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineDispatcher

internal fun deviceEngine(dispatcher: CoroutineDispatcher): LlamaCppEngine = LlamaCppEngine.create(
    InstrumentationRegistry.getInstrumentation().targetContext.applicationInfo.nativeLibraryDir,
    dispatcher,
)
