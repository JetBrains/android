package p1.p2;

import android.app.Activity;
import android.os.Bundle;

public class MyActivity extends Activity {
  //<caret>
  public void onCreate(Bundle state) {
    setContentView(R.layout.layout);

    getLayoutInflater().inflate(R.layout.layout1, null);

    int n = R.layout.layout2;
    f(R.layout.layout2);
  }

  void f(int n) {

  }
}