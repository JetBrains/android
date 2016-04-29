package com.foo;

import android.app.Activity;
import android.os.Bundle;

public class MyActivity extends Activity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    getResources().getStringArray(R.array.col<caret>ors);
  }

  public static final class R {
    public static final class array {
      public static final int colors = 0x7f0a000e;
    }
  }
}
