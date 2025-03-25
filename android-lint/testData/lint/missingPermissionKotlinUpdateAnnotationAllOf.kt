package p1.p2

import android.os.DropBoxManager
import androidx.annotation.RequiresPermission

@Suppress("unused")
class LocationTest {
  @RequiresPermission("foo.bar.baz")
  fun test(manager: DropBoxManager) {
    <error>manager.getNext<caret>Entry("tag", 8675309L)</error>
  }
}