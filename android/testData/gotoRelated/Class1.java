package p1.p2;

import android.app.Activity;
import android.os.Bundle;

public class Class1 {
  public void onCreate(Bundle savedInstanceState) {
    setContentView(R.layout.layout);
    getLayoutInflater().inflate(R.layout.layout, null);
  }

  void setContentView(int n) {
  }

  LayoutInflater getLayoutInflater() {
    return new LayoutInflater(null) {
      public LayoutInflater cloneInContext(Context newContext) {
        return null;
      }
    };
  }
}