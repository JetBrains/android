package p1.p2;

import android.Manifest;
import android.os.Vibrator;

import androidx.annotation.RequiresPermission;

class LocationTest {
  @RequiresPermission(Manifest.permission.VIBRATE)
  void test(Vibrator vibrator) {
    vibrator.can<caret>cel();
  }
}