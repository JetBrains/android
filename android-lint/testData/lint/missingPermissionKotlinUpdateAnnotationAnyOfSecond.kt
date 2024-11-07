package p1.p2

import android.location.LocationManager
import androidx.annotation.RequiresPermission

@Suppress("unused")
class LocationTest {
  @RequiresPermission("foo.bar.baz")
  fun test(manager: LocationManager, provider: String) {
    <error>manager.get<caret>LastKnownLocation(provider)</error>
  }
}