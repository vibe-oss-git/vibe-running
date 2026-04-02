package com.viberunning.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GpsStatusMonitor(private val context: Context) {

    enum class GpsSignal {
        UNAVAILABLE,
        SEARCHING,
        WEAK,
        READY
    }

    private val _signal = MutableStateFlow(GpsSignal.UNAVAILABLE)
    val signal: StateFlow<GpsSignal> = _signal.asStateFlow()

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fusedClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    private var gnssCallback: GnssStatus.Callback? = null
    private var locationCallback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    fun startMonitoring() {
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            _signal.value = GpsSignal.UNAVAILABLE
            return
        }

        _signal.value = GpsSignal.SEARCHING

        // Monitor GNSS satellite status
        gnssCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var usedCount = 0
                for (i in 0 until status.satelliteCount) {
                    if (status.usedInFix(i)) usedCount++
                }
                _signal.value = when {
                    usedCount >= 4 -> GpsSignal.READY
                    usedCount >= 1 -> GpsSignal.WEAK
                    else -> GpsSignal.SEARCHING
                }
            }
        }
        locationManager.registerGnssStatusCallback(gnssCallback!!, android.os.Handler(Looper.getMainLooper()))

        // Request location updates to trigger satellite acquisition
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    if (loc.accuracy <= 20f) {
                        _signal.value = GpsSignal.READY
                    } else if (loc.accuracy <= 50f && _signal.value != GpsSignal.READY) {
                        _signal.value = GpsSignal.WEAK
                    }
                }
            }
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        fusedClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
    }

    fun stopMonitoring() {
        gnssCallback?.let { locationManager.unregisterGnssStatusCallback(it) }
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        gnssCallback = null
        locationCallback = null
    }
}
