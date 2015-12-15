package com.foo;

import android.app.Activity;
import android.os.Bundle;

public class MyActivity extends Activity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    String text = getResources().getString(R.string.<caret>cancel);
  }

  public static final class R {
    public static final class string {
      public static final int cancel = 0x7f0a000e;
    }
  }
}
