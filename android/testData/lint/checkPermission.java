package test.pkg;

import android.app.Activity;
import android.content.Context;

@SuppressWarnings("UnusedDeclaration")
public class CheckPermissionTest extends Activity {
    public void test() {
        <warning descr="The result of `checkCallingOrSelfPermission` is not used; did you mean to call `enforceCallingOrSelfPermission`?">check<caret>CallingOrSelfPermission(ALARM_SERVICE)</warning>;
    }
    public void test2(Context context) {
        <warning descr="The result of `checkCallingOrSelfPermission` is not used; did you mean to call `enforceCallingOrSelfPermission`?">checkCallingOrSelfPermission(ALARM_SERVICE)</warning>;
    }
}