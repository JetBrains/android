package p1.p2;

import android.app.Activity;
import android.os.Bundle;

public class MyActivity extends Activity {
  public void onCreate(Bundle state) {
    setContentView(R.layout.layout);

    getLayoutInflater().inflate(R.layout.layout1, null);

    int n = R.layout.layout2;
    f(R.layout.layout2);
  }

  public static class MyInnerActivity extends Activity {
    public void onCreate(Bundle state) {
      //<caret>
      getLayoutInflater().inflate(R.layout.layout, null);
      getLayoutInflater().inflate(R.layout.layout, null);
    }
  }
}