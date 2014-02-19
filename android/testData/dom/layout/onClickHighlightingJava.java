package p1.p2;

import android.os.Bundle;
import android.view.View;

public class MyActivity1 extends android.app.Activity {
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  public void clickHandler1(View v) {
  }

  void clickHandler2(View <warning>v</warning>) {
  }

  public static void clickHandler3(View <warning>v</warning>) {
  }

  public int clickHandler4(View <warning>v</warning>) {
    return 0;
  }
}