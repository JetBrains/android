package p1.p2;

import android.os.Bundle;
import android.view.MenuItem;

public class MyActivity1 extends android.app.Activity {
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  public void clickHandler1(MenuItem v) {
  }

  void clickHandler2(MenuItem <warning>v</warning>) {
  }

  public static void clickHandler3(MenuItem <warning>v</warning>) {
  }

  public int clickHandler4(MenuItem <warning>v</warning>) {
    return 0;
  }
}