package p1.p2.p3;

import android.content.Context;

/**
 * We don't care about the contents of this class - we just need
 * something to make sure the deep link action doesn't get triggered
 * for java files
 */
public class Class {
  public void f(Context context) {
    String s = "h<caret>ello";
  }
}
