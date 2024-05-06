package p1.p2

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.location.LocationManager

@Suppress("unused")
@SuppressLint("Registered")
class LocationTest : Activity() {
    fun test(manager: LocationManager, provider: String) {
        if (checkSelfPermission(ACCESS_FINE_LOCATION) != PERMISSION_GRANTED && checkSelfPermission(ACCESS_COARSE_LOCATION) != PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    Activity#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for Activity#requestPermissions for more details.
            return
        }
        manager.getLastKnownLocation(provider)
    }
}
