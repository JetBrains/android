package test.pkg;

import android.content.pm.ActivityInfo;
import android.app.Activity;
import android.os.Bundle;

public class TestActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
  }
}
