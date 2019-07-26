package p1.p2;

import android.os.Build;
import android.support.annotation.RequiresApi;

@SuppressWarnings({"unused", "WeakerAccess"})
public class JavaRemoveObsoleteSdkCheckTest {
    public void test() {
        requiresApi14();
    }

    @RequiresApi(14)
    public void requiresApi14() {
    }
}
