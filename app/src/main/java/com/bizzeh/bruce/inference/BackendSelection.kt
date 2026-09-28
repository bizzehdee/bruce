package com.bizzeh.bruce.inference

/** Which backend to load a model on. [AUTO] lets Bruce choose, and currently always chooses the CPU. */
enum class BackendPreference {
    AUTO,
    CPU,
    VULKAN,
    OPENCL,
    ;

    val isGpu: Boolean get() = this == VULKAN || this == OPENCL
}

/** One way to load a model: a backend and the devices it uses. An empty list means the CPU. */
internal data class LoadAttempt(val backend: Backend, val devices: List<ComputeDevice>)

internal sealed interface BackendPlan {
    /** Attempts in order; the last is always the CPU. */
    data class Attempts(val attempts: List<LoadAttempt>) : BackendPlan

    /** The user asked for a backend that has no usable device on this phone. */
    data class Unavailable(val backend: Backend) : BackendPlan
}

object BackendSelection {
    private val CPU_ATTEMPT = LoadAttempt(Backend.CPU, emptyList())

    /**
     * The choices to offer on this phone: Auto, CPU, and each GPU backend with a usable device.
     * [current] is kept even when unavailable (a setting saved on another build), so it can be changed.
     */
    fun choices(capabilities: EngineCapabilities, current: BackendPreference? = null): List<BackendPreference> {
        val usable = capabilities.usableBackends
        return BackendPreference.entries.filter { preference ->
            when (preference) {
                BackendPreference.AUTO, BackendPreference.CPU -> true
                BackendPreference.VULKAN, BackendPreference.OPENCL -> Backend.valueOf(preference.name) in usable || preference == current
            }
        }
    }

    internal fun plan(preference: BackendPreference, capabilities: EngineCapabilities): BackendPlan {
        fun gpuAttempt(backend: Backend): LoadAttempt? =
            capabilities.devices.firstOrNull { it.backend == backend && it.usable }
                ?.let { LoadAttempt(backend, listOf(it)) }

        return when (preference) {
            // Never a GPU: Vulkan on the Pixel 11's PowerVR GPU loads fine and returns wrong text
            // (.learnings/gpu-backends-on-test-phones.md). A GPU is used only when the user picks it.
            BackendPreference.AUTO -> BackendPlan.Attempts(listOf(CPU_ATTEMPT))
            BackendPreference.CPU -> BackendPlan.Attempts(listOf(CPU_ATTEMPT))
            BackendPreference.VULKAN, BackendPreference.OPENCL -> {
                val backend = Backend.valueOf(preference.name)
                gpuAttempt(backend)?.let { BackendPlan.Attempts(listOf(it, CPU_ATTEMPT)) }
                    ?: BackendPlan.Unavailable(backend)
            }
        }
    }
}
