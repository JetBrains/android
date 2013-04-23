package com.foo;

import android.app.Activity;
import android.os.Bundle;

public class MyActivity extends Activity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    int size = getResources().getDimensionPixelSize(R.dimen.d<caret>im1);
  }
}
