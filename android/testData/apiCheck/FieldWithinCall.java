package p1.p2;

import android.graphics.PorterDuff;

class FieldWithinCall {
  public void test() {
    int hash = <error descr="Field requires API level 11 (current min is 1): android.graphics.PorterDuff.Mode#OVERLAY">PorterDuff.Mode.OVERLAY</error>.hashCode();
  }
}
