package p1.p2;

import android.Manifest;
import android.location.LocationManager;

import androidx.annotation.RequiresPermission;

class LocationTest {
  @RequiresPermission(anyOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
  void test(LocationManager manager, String provider) {
    manager.get<caret>LastKnownLocation(provider);
  }
}