package p1.p2;

import android.app.Activity;
import android.os.Bundle;

public class MyActivity extends Activity {
  //<caret>
  public void onCreate(Bundle state) {
     // No explicit reference to R layouts here; searching
     // via tools:context attribute instead
  }
}
