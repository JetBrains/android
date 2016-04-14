package com.foo;

import android.app.Activity;
import android.os.Bundle;

public class MyActivity extends Activity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    String text = getResources().getString(R.string.app_n<caret>ame);
  }

  public static final class R {
    public static final class string {
      public static final int app_name = 0x7f0a000e;
    }
  }
}
