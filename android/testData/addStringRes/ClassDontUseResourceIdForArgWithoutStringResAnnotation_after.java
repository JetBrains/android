package p1.p2.p3;

import java.lang.StringBuilder
import android.content.Context;

import p1.p2.R;

public class Class {
  public void f(Context context) {
    StringBuilder sb = new StringBuilder()
    sb.append(context.getString(R.string.hello))
  }
}
