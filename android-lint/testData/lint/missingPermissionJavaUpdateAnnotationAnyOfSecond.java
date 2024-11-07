package p1.p2;

import android.location.LocationManager;
import androidx.annotation.RequiresPermission;

class LocationTest {
  @RequiresPermission("foo.bar.baz")
  void test(LocationManager manager, String provider) {
    <error>manager.get<caret>LastKnownLocation(provider)</error>;
  }
}