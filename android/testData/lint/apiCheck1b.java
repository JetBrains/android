package p1.p2;

import android.app.ActionBar;
import android.app.Activity;

public class MyActivity extends Activity {
  private MyActivity() {
  }

  public void test1() {
    ActionBar actionBar = <error descr="Call requires API level 11 (current min is 1): android.app.Activity#getActionBar">getA<caret>ctionBar</error>();
  }
}