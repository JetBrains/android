package androidx.annotation;

import android.os.Build;

public @interface RequiresApi {
    int value();
}

class RequiresApiTest {
    public void test() {
        <error descr="Call requires API level 99.9 (current min is 1): `requiresFutureMinor`">requires<caret>FutureMinor</error>();
    }

    // Some future API level >= 36 such that we insert the SDK_INT check.
    // (Picked much higher so test doesn't break when we add known SDK names
    // for the chosen API level.)
    @RequiresApi(99*100_000+9)
    public void requiresFutureMinor() {
    }
}