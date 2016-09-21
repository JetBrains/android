package p1.p2;

import android.app.Activity;
import android.os.Bundle;

/**
 * Tests that navigation to the SDK base class will work.
 */
public class MyActivity extends Act<caret>ivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);
  }
}
