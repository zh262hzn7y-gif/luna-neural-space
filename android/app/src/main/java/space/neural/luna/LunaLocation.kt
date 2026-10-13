package space.neural.luna

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

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
        val lm = activity.getSystemService(LocationManager::class.java)
        val loc = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        if (loc != null) callback(loc.latitude, loc.longitude) else callback(0.0, 0.0)
    }
}
