package p1.p2;

import android.os.Vibrator;

class LocationTest {
  void test(Vibrator vibrator) {
    <error>vibrator.can<caret>cel()</error>;
  }
}