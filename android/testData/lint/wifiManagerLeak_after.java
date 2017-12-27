package test.pkg;

import android.app.Activity;

@SuppressWarnings("UnusedDeclaration")
public class WifiManagerLeak extends Activity {
  public Object wifiManager() {
    return getApplicationContext().getSystemService(WIFI_SERVICE);
  }
}