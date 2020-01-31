package test.pkg;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;

public class TestActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    PackageManager pm = getPackageManager();
    pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
  }
}
