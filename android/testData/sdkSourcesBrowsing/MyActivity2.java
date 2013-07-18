package p1.p2;

import android.app.Activity;
import android.os.Bundle;

public class MyActivity extends Act<caret>ivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);
  }
}
