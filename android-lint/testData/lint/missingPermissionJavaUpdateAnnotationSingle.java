package p1.p2;

import android.os.Vibrator;
import androidx.annotation.RequiresPermission;

class LocationTest {
  @RequiresPermission("foo.bar.baz")
  void test(Vibrator vibrator) {
    <error>vibrator.can<caret>cel()</error>;
  }
}