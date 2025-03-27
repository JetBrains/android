package p1.p2;

import android.location.LocationManager;

class LocationTest {
  void test(LocationManager manager, String provider) {
    <error>manager.get<caret>LastKnownLocation(provider)</error>;
  }
}