package androidx.annotation;

import android.os.Build;

public @interface RequiresSdkVersion {
    int sdk();
    int version();
}

class SdkExtensionsTest {
    public void test() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && android.os.ext.SdkExtensions.getExtensionVersion(30) >= 4<caret>) {
            requiresExtRv4();
        }
    }

    @RequiresSdkVersion(sdk= Build.VERSION_CODES.R, version=4)
    public void requiresExtRv4() {
    }
}