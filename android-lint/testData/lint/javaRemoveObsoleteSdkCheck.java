package p1.p2;

import android.os.Build;
import android.support.annotation.RequiresApi;

@SuppressWarnings({"unused", "WeakerAccess"})
public class JavaRemoveObsoleteSdkCheckTest {
    public void test() {
        if (<warning descr="Unnecessary; `SDK_INT` is always >= 19">Build.VERSION.SDK_INT<caret> >= Build.VERSION_CODES.ICE_CREAM_SANDWICH</warning>) {
            requiresApi14();
        } else {
            // This should be deleted
        }
    }

    <warning descr="Unnecessary; `SDK_INT` is always >= 14">@RequiresApi(14)</warning>
    public void requiresApi14() {
    }
}
