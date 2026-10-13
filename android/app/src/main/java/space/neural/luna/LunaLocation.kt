package space.neural.luna

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

object LunaLocation {
    fun get(activity: Activity, callback: (Double, Double) -> Unit) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                1001
            )
            callback(0.0, 0.0)
            return
        }
        val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(activity)
        client.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) callback(loc.latitude, loc.longitude)
            else callback(0.0, 0.0)
        }.addOnFailureListener {
            callback(0.0, 0.0)
        }
    }
}
