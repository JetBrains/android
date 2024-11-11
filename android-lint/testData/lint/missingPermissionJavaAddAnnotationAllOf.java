package p1.p2;

import android.os.DropBoxManager;

class LocationTest {
  void test(DropBoxManager manager) {
    <error>manager.getNext<caret>Entry("tag", 8675309L)</error>;
  }
}