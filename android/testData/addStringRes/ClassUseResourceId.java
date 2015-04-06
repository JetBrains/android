package p1.p2.p3;

import android.content.Context;
import android.widget.Toast;

public class Class {
  public void f(Context context) {
    Toast.makeText(context, "h<caret>ello", Toast.LENGTH_SHORT).show();
  }
}