package com.foo;

import android.app.Activity;
import android.os.Bundle;

public class MyActivity extends Activity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    String name = getResources().getResourceName(R.drawable.<caret>selector);
  }

  public static final class R {
    public static final class drawable {
      public static final int selector = 0x7f0a000e;
    }
  }
}
