package p1.p2;

import android.Manifest;
import android.os.DropBoxManager;

import androidx.annotation.RequiresPermission;

class LocationTest {
  @RequiresPermission(allOf = {Manifest.permission.READ_LOGS, Manifest.permission.PACKAGE_USAGE_STATS})
  void test(DropBoxManager manager) {
    manager.getNext<caret>Entry("tag", 8675309L);
  }
}