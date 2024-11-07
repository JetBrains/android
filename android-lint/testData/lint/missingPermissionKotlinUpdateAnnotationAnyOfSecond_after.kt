package p1.p2

import android.Manifest
import android.location.LocationManager
import androidx.annotation.RequiresPermission

@Suppress("unused")
class LocationTest {
  @RequiresPermission(allOf = ["foo.bar.baz", Manifest.permission.ACCESS_COARSE_LOCATION])
  fun test(manager: LocationManager, provider: String) {
    manager.get<caret>LastKnownLocation(provider)
  }
}