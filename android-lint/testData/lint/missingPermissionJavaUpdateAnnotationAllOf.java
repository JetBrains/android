package p1.p2;

import android.os.DropBoxManager;
import androidx.annotation.RequiresPermission;

class LocationTest {
  @RequiresPermission("foo.bar.baz")
  void test(DropBoxManager manager) {
    <error>manager.getNext<caret>Entry("tag", 8675309L)</error>;
  }
}