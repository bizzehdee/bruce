package com.bizzeh.bruce.models

import com.bizzeh.bruce.gguf.GgufMetadata
import com.bizzeh.bruce.gguf.LlamaFileTypes
import com.bizzeh.bruce.hardware.CpuFeatures

enum class Fit {
    FITS,
    /** Fits, but uses more than [ModelFit.TIGHT_SHARE] of usable memory. */
    TIGHT,
    DOES_NOT_FIT,
}

enum class SpeedBand {
    /** 15 tokens per second or more: comfortable for chat. */
    FAST,
    /** 5 to 15 tokens per second. */
    USABLE,
    /** Under 5 tokens per second. */
    SLOW,
}

/** The phone a model would run on. */
data class DeviceProfile(val usableMemoryBytes: Long, val cpu: CpuFeatures)

/** A model file offered for download, with its GGUF header when it has been read. */
data class Candidate(
    val repositoryId: String,
    val path: String,
    val sizeBytes: Long,
    val gated: Boolean,
    /** From the Hub search; used when [header] is not available. */
    val architecture: String?,
    val parameterCount: Long?,
    val header: GgufMetadata? = null,
    /** Published SHA-256, for verifying a download. */
    val sha256: String? = null,
)

data class Assessment(
    val candidate: Candidate,
    val supported: Boolean,
    val quantisation: String?,
    val estimate: MemoryEstimate,
    val fit: Fit,
    val expectedTokensPerSecond: Double,
    val speed: SpeedBand,
)

/**
 * Judges model files for this phone. Everything runs on the phone; nothing about the
 * device is sent anywhere.
 */
object ModelFit {
    /** A judgement, not a measurement: above this share of usable memory, other apps get squeezed. */
    const val TIGHT_SHARE = 0.8

    /**
     * Generation speed × weights in GB, measured in TASK-012 (.learnings/phase0-cpu-benchmarks.md):
     * about 24 on a DOTPROD CPU (Xperia 1 II) and 5.8 without (XZ Premium). Generation is
     * memory-bandwidth bound, so speed ≈ this constant ÷ model size. Provisional: two models,
     * two phones.
     */
    private const val DOTPROD_TOKENS_GB_PER_SECOND = 24.0
    private const val BASELINE_TOKENS_GB_PER_SECOND = 5.8

    private val UNQUANTISED = setOf("F32", "F16", "BF16")

    private const val FAST_TOKENS_PER_SECOND = 15.0
    private const val USABLE_TOKENS_PER_SECOND = 5.0
    private const val BYTES_PER_GB = 1_000_000_000.0

    private val QUANTISATION_IN_NAME = Regex(
        "(?:^|[-_.])(" + (LlamaFileTypes.names + listOf("Q4_K", "Q5_K", "Q3_K", "IQ4_NL")).sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) } + ")(?:[-_.]|$)",
        RegexOption.IGNORE_CASE,
    )

    fun assess(candidate: Candidate, device: DeviceProfile, contextLength: Int): Assessment {
        val header = candidate.header
        val architecture = header?.architecture ?: candidate.architecture
        val estimate = header?.let { ModelMemory.estimate(it, contextLength) }
            ?: MemoryEstimate(weightsBytes = candidate.sizeBytes, kvCacheBytes = null, contextLength = contextLength)
        val tokensPerSecond = (if (device.cpu.dotProd) DOTPROD_TOKENS_GB_PER_SECOND else BASELINE_TOKENS_GB_PER_SECOND) /
            (estimate.weightsBytes / BYTES_PER_GB)
        return Assessment(
            candidate = candidate,
            supported = architecture != null && LlamaArchitectures.supportsChat(architecture),
            quantisation = header?.quantisation ?: quantisationFromName(candidate.path),
            estimate = estimate,
            fit = fit(estimate.totalBytes, device.usableMemoryBytes),
            expectedTokensPerSecond = tokensPerSecond,
            speed = when {
                tokensPerSecond >= FAST_TOKENS_PER_SECOND -> SpeedBand.FAST
                tokensPerSecond >= USABLE_TOKENS_PER_SECOND -> SpeedBand.USABLE
                else -> SpeedBand.SLOW
            },
        )
    }

    /**
     * Best first: supported; able to fit (a gated model only needs sign-in, one that does not
     * fit cannot run); not gated; fits before tight; faster speed band; more parameters (more
     * capable); quantised before 16- and 32-bit floats (twice the memory, half the speed, for
     * little quality on a phone); then larger file (higher precision).
     */
    fun rank(candidates: List<Candidate>, device: DeviceProfile, contextLength: Int): List<Assessment> =
        candidates.map { assess(it, device, contextLength) }.sortedWith(
            compareBy<Assessment>(
                { !it.supported },
                { it.fit == Fit.DOES_NOT_FIT },
                { it.candidate.gated },
                { it.fit.ordinal },
                { it.speed.ordinal },
            )
                .thenByDescending { it.candidate.header?.parameterCount ?: it.candidate.parameterCount ?: 0 }
                .thenBy { it.quantisation in UNQUANTISED }
                .thenByDescending { it.candidate.sizeBytes },
        )

    internal fun quantisationFromName(path: String): String? =
        QUANTISATION_IN_NAME.find(path.substringAfterLast('/').removeSuffix(".gguf"))?.groupValues?.get(1)?.uppercase()

    private fun fit(required: Long, usable: Long): Fit = when {
        required > usable -> Fit.DOES_NOT_FIT
        required > usable * TIGHT_SHARE -> Fit.TIGHT
        else -> Fit.FITS
    }
}
