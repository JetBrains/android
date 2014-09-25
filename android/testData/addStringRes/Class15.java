package p1.p2.p3;

import android.content.Context;
import android.content.res.Resources;

public class Class extends Context {
  public void f(Context resources) {
    String t = "^([a-zA<caret>-Z0-9_\\.\\-\\+])+\\@(([a-zA-Z0-9\\-])+\\.)+([a-zA-Z0-9]{2,4})$";
  }
}
