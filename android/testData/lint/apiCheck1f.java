<error descr="The SDK platform-tools version ((16.0.2)) is too old  to check APIs compiled with API 17; please update">package p1.p2;</error>

import android.app.ActionBar;
import android.app.Activity;

public class MyActivity extends Activity {
  private MyActivity() {
  }

  public void test1() {
    ActionBar actionBar = <error descr="Call requires API level 11 (current min is 1): android.app.Activity#getActionBar">getA<caret>ctionBar</error>();
  }
}