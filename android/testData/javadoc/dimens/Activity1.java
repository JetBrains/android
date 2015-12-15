package com.foo;

import android.app.Activity;
import android.os.Bundle;

public class MyActivity extends Activity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    int size = getResources().getDimensionPixelSize(R.dimen.d<caret>im1);
  }

  public static final class R {
    public static final class dimen {
      public static final int dim1 = 0x7f0a000e;
    }
  }
}
