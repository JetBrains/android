package test.pkg;

import android.app.Activity;

@SuppressWarnings("UnusedDeclaration")
public class WifiManagerLeak extends Activity {
  public Object wifiManager() {
    return <error descr="The WIFI_SERVICE must be looked up on the Application context or memory will leak on devices < Android N. Try changing `getSystemService` to `getApplicationContext().getSystemService`">getSystem<caret>Service(WIFI_SERVICE)</error>;
  }
}