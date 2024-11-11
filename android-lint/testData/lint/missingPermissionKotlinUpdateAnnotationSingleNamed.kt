package p1.p2

import android.os.Vibrator
import androidx.annotation.RequiresPermission

@Suppress("unused")
class LocationTest {
  @RequiresPermission(value = "foo.bar.baz")
  fun test(vibrator: Vibrator) {
    <error>vibrator.can<caret>cel()</error>
  }
}