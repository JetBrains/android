package p1.p2;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.os.Build;

public class MyActivity extends Activity {
  private MyActivity() {
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  public void test1() {
    ActionBar actionBar = getActionBar();
  }
}