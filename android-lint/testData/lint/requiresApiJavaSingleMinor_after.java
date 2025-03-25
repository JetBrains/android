package androidx.annotation;

import android.os.Build;

public @interface RequiresApi {
    int value();
}

class RequiresApiTest {
    @RequiresApi(api = Build.VERSION_CODES_FULL.VANILLA_ICE_CREAM_2)
    public void test() {
        requires<caret>_352();
    }

    @RequiresApi(35*100_000+2) // Use constant in VERSION_CODES when available
    public void requires_352() {
    }
}