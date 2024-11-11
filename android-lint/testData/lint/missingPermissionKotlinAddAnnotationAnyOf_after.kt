package p1.p2

import android.Manifest
import android.location.LocationManager
import androidx.annotation.RequiresPermission

@Suppress("unused")
class LocationTest {
  @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
  fun test(manager: LocationManager, provider: String) {
    manager.get<caret>LastKnownLocation(provider)
  }
}