package test.pkg;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;

public class TestActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    PackageManager pm = getPackageManager();
    <warning descr="You should look for any camera available on the device, not just the rear"><caret>pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)</warning>;
  }
}
