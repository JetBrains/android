package p1.p2

import android.os.Vibrator

@Suppress("unused")
class LocationTest {
  fun test(vibrator: Vibrator) {
    <error>vibrator.can<caret>cel()</error>
  }
}