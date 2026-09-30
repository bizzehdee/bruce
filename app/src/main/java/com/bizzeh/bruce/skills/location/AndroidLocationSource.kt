package com.bizzeh.bruce.skills.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** One reading from Android's own location providers; no Google Play services. */
class AndroidLocationSource(private val context: Context) : LocationSource {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()

    override fun enabled(): Boolean = LocationManagerCompat.isLocationEnabled(manager)

    override fun precise(): Boolean = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    // The policy engine only runs this skill once the location permission is held.
    @SuppressLint("MissingPermission")
    override suspend fun current(): Reading? = withTimeoutOrNull(TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            val cancel = CancellationSignal()
            continuation.invokeOnCancellation { cancel.cancel() }
            LocationManagerCompat.getCurrentLocation(manager, provider(), cancel, executor) { location ->
                continuation.resume(location?.let(::reading))
            }
        }
    }

    /**
     * A fresh fix can take longer than [TIMEOUT_MS] indoors (none came in 30 s on the Xperia 1 II,
     * while the last known was a few minutes old), so a recent last known location stands in.
     */
    @SuppressLint("MissingPermission")
    override fun lastKnown(): Reading? = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        .filter { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S || it != LocationManager.FUSED_PROVIDER }
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.elapsedRealtimeNanos }
        ?.let(::reading)

    private fun reading(location: android.location.Location) = Reading(
        latitude = location.latitude,
        longitude = location.longitude,
        accuracyMetres = if (location.hasAccuracy()) location.accuracy else null,
        ageSeconds = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000_000,
    )

    private fun provider(): String = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.hasProvider(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) || !precise() -> LocationManager.NETWORK_PROVIDER
        else -> LocationManager.GPS_PROVIDER
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }
}
