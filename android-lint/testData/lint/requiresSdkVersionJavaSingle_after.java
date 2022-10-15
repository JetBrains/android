package androidx.annotation;

import android.os.Build;

public @interface RequiresSdkVersion {
    int sdk();
    int version();
}

class SdkExtensionsTest {
    @RequiresSdkVersion(sdk = Build.VERSION_CODES.R, version = 4)
    public void test() {
        <caret>requiresExtRv4();
    }

    @RequiresSdkVersion(sdk= Build.VERSION_CODES.R, version=4)
    public void requiresExtRv4() {
    }
}