package p1.p2

import android.location.LocationManager

@Suppress("unused")
class LocationTest {
  fun test(manager: LocationManager, provider: String) {
    <error>manager.get<caret>LastKnownLocation(provider)</error>
  }
}