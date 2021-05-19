package test.pkg;

import android.content.pm.ActivityInfo;
import android.app.Activity;
import android.os.Bundle;

public class TestActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    <warning descr="You should not lock orientation of your activities, so that you can support a good user experience for any device or orientation"><caret>setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)</warning>;
  }
}
