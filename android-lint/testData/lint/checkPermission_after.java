package test.pkg;

import android.app.Activity;
import android.content.Context;

@SuppressWarnings("UnusedDeclaration")
public class CheckPermissionTest extends Activity {
    public void test() {
        enforceCallingOrSelfPermission(ALARM_SERVICE, "TODO: message if thrown");
    }
    public void test2(Context context) {
        checkCallingOrSelfPermission(ALARM_SERVICE);
    }
}