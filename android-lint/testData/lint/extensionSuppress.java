package androidx.annotation;

import android.os.Build;

public @interface RequiresSdkVersion {
    int sdk();
    int version();
}

class SdkExtensionsTest {
    public void test() {
        <error descr="Call requires version 4 of the R SDK (current min is 0): `requiresExtRv4`">requires<caret>ExtRv4</error>();
    }

    @RequiresSdkVersion(sdk= Build.VERSION_CODES.R, version=4)
    public void requiresExtRv4() {
    }
}