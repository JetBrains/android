package p1.p2

import android.os.DropBoxManager

@Suppress("unused")
class LocationTest {
  fun test(manager: DropBoxManager) {
    <error>manager.getNext<caret>Entry("tag", 8675309L)</error>
  }
}