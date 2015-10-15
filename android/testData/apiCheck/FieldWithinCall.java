<error descr="The SDK platform-tools version ((16.0.2)) is too old  to check APIs compiled with API 17; please update">package p1.p2;</error>

import android.graphics.PorterDuff;

class FieldWithinCall {
  public void test() {
    int hash = <error descr="Field requires API level 11 (current min is 1): android.graphics.PorterDuff.Mode#OVERLAY">PorterDuff.Mode.OVERLAY</error>.hashCode();
  }
}
