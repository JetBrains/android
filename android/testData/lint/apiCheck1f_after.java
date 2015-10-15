package p1.p2;

import android.app.ActionBar;
import android.app.Activity;

public class MyActivity extends Activity {
  private MyActivity() {
  }

  public void test1() {
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
          ActionBar actionBar = getActionBar();
      }
  }
}