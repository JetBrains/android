package p1.p2;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Build;

public class MyActivity extends Activity {
  private MyActivity() {
  }

  public void test1() {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
          ActionBar actionBar = getActionBar();
      }
  }
}