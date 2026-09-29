package com.bizzeh.bruce.models

import com.bizzeh.bruce.huggingface.HubModel
import com.bizzeh.bruce.huggingface.HubSearchFilter

private const val B = 1_000_000_000L

/** Parameter-count buckets; bounds inclusive. */
enum class ParameterBucket(val min: Long?, val max: Long?) {
    UNDER_1B(null, B - 1),
    FROM_1B_TO_3B(B, 3 * B - 1),
    FROM_3B_TO_8B(3 * B, 8 * B - 1),
    FROM_8B_TO_14B(8 * B, 14 * B - 1),
    FROM_14B(14 * B, null),
}

/** Download-size buckets for the best-fitting file, in decimal gigabytes; bounds inclusive. */
enum class SizeBucket(val min: Long?, val max: Long?) {
    UNDER_1GB(null, B - 1),
    FROM_1GB_TO_2GB(B, 2 * B - 1),
    FROM_2GB_TO_4GB(2 * B, 4 * B - 1),
    FROM_4GB_TO_8GB(4 * B, 8 * B - 1),
    FROM_8GB(8 * B, null),
}

enum class RunsFilter {
    /** Fits with room to spare. */
    FITS,
    /** Fits, possibly using most of the phone's memory. */
    FITS_OR_TIGHT,
    ANY,
}

data class BrowseFilters(
    val parameters: ParameterBucket? = null,
    val size: SizeBucket? = null,
    val runs: RunsFilter = RunsFilter.FITS_OR_TIGHT,
    /** The only task type Bruce can run today. Off shows repositories without the tag too. */
    val textGeneration: Boolean = true,
)

/** A repository in the list, with the file Bruce would pick for this phone, sized by estimate. */
/** [skills] is false when the repository's chat template cannot express tool calls, null when unknown (TASK-061). */
data class Listing(val model: HubModel, val best: Assessment?, val skills: Boolean? = null)

/**
 * Recommendations and filtering for the model browser. The Hub applies the task and parameter
 * filters; size and fit are judged here, on the phone, from estimated file sizes.
 */
object Recommendations {
    /**
     * The fewest bits per weight worth offering (IQ2_XXS is 2.06). A repository whose parameter
     * count would not fit even at this size is not requested for recommendations.
     */
    private const val MIN_BITS_PER_WEIGHT = 2.0

    fun hubFilter(filters: BrowseFilters, device: DeviceProfile): HubSearchFilter {
        val deviceMax = (device.usableMemoryBytes * 8 / MIN_BITS_PER_WEIGHT).toLong().takeIf { filters.runs != RunsFilter.ANY }
        val bucketMax = filters.parameters?.max
        return HubSearchFilter(
            textGeneration = filters.textGeneration,
            minParameters = filters.parameters?.min,
            maxParameters = listOfNotNull(bucketMax, deviceMax).minOrNull(),
        )
    }

    /** The whole-model file ranked best for this phone, with its size estimated; null if none can be estimated. */
    fun bestFile(model: HubModel, device: DeviceProfile, contextLength: Int): Assessment? {
        val parameters = model.parameterCount ?: return null
        val candidates = model.files.filter(SizeEstimate::isWholeModel).mapNotNull { path ->
            SizeEstimate.bytes(parameters, path)?.let { size ->
                Candidate(model.id, path, size, model.gated, model.architecture, parameters)
            }
        }
        return ModelFit.rank(candidates, device, contextLength).firstOrNull()
    }

    /**
     * Applies the phone-side filters. Recommendations put files that fit first, then keep the Hub's
     * most-downloaded order; name searches keep the Hub's order.
     */
    fun listings(
        results: List<HubModel>,
        filters: BrowseFilters,
        device: DeviceProfile,
        contextLength: Int,
        recommended: Boolean,
        skills: (HubModel) -> Boolean? = { null },
    ): List<Listing> {
        val listed = results.map { Listing(it, bestFile(it, device, contextLength), skills(it)) }
            .filter { runs(it.best, filters.runs) && inSize(it.best, filters.size) }
        // Stable sorts keep the Hub's order (popularity) within each rank.
        val limitedLast = compareBy<Listing> { if (it.skills == false) 1 else 0 }
        return if (recommended) listed.sortedWith(compareBy<Listing> { it.best?.fit?.ordinal ?: Fit.entries.size }.then(limitedLast)) else listed.sortedWith(limitedLast)
    }

    private fun runs(best: Assessment?, filter: RunsFilter): Boolean = when (filter) {
        RunsFilter.ANY -> true
        RunsFilter.FITS -> best != null && best.supported && best.fit == Fit.FITS
        RunsFilter.FITS_OR_TIGHT -> best != null && best.supported && best.fit != Fit.DOES_NOT_FIT
    }

    private fun inSize(best: Assessment?, bucket: SizeBucket?): Boolean {
        if (bucket == null) return true
        val size = best?.candidate?.sizeBytes ?: return false
        return (bucket.min == null || size >= bucket.min) && (bucket.max == null || size <= bucket.max)
    }
}
