package p1.p2

import android.Manifest
import android.os.Vibrator
import androidx.annotation.RequiresPermission

@Suppress("unused")
class LocationTest {
  @RequiresPermission(allOf = ["foo.bar.baz", Manifest.permission.VIBRATE])
  fun test(vibrator: Vibrator) {
    vibrator.can<caret>cel()
  }
}