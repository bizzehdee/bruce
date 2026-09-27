package com.bizzeh.bruce.inference

/** Which backend to load a model on. [AUTO] lets Bruce choose. */
enum class BackendPreference {
    AUTO,
    CPU,
    VULKAN,
    OPENCL,
}

/** One way to load a model: a backend and the devices it uses. An empty list means the CPU. */
internal data class LoadAttempt(val backend: Backend, val devices: List<ComputeDevice>)

internal sealed interface BackendPlan {
    /** Attempts in order; the last is always the CPU. */
    data class Attempts(val attempts: List<LoadAttempt>) : BackendPlan

    /** The user asked for a backend that has no usable device on this phone. */
    data class Unavailable(val backend: Backend) : BackendPlan
}

internal object BackendSelection {
    // Provisional: no test phone has a usable GPU yet, so this order is not measured.
    private val GPU_ORDER = listOf(Backend.VULKAN, Backend.OPENCL)

    private val CPU_ATTEMPT = LoadAttempt(Backend.CPU, emptyList())

    fun plan(preference: BackendPreference, capabilities: EngineCapabilities): BackendPlan {
        fun gpuAttempt(backend: Backend): LoadAttempt? =
            capabilities.devices.firstOrNull { it.backend == backend && it.usable }
                ?.let { LoadAttempt(backend, listOf(it)) }

        return when (preference) {
            BackendPreference.AUTO -> BackendPlan.Attempts(GPU_ORDER.mapNotNull(::gpuAttempt) + CPU_ATTEMPT)
            BackendPreference.CPU -> BackendPlan.Attempts(listOf(CPU_ATTEMPT))
            BackendPreference.VULKAN, BackendPreference.OPENCL -> {
                val backend = Backend.valueOf(preference.name)
                gpuAttempt(backend)?.let { BackendPlan.Attempts(listOf(it, CPU_ATTEMPT)) }
                    ?: BackendPlan.Unavailable(backend)
            }
        }
    }
}
