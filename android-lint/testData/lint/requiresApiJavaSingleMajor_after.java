package androidx.annotation;

import android.os.Build;

public @interface RequiresApi {
    int value();
}

class RequiresApiTest {
    @RequiresApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    public void test() {
        requires<caret>_35();
    }

    @RequiresApi(35)
    public void requires_35() {
    }
}