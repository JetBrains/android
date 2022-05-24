package p1.p2;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;

public class MyActivity extends Activity {
  private MyActivity() {
  }

  public void test1() {
    @SuppressLint("NewApi") ActionBar actionBar = getActionBar();
  }
}