package androidx.annotation;

import android.os.Build;
import android.os.ext.SdkExtensions;

public @interface RequiresExtension {
    int extension();
    int version();
}

class SdkExtensionsTest {
    public void test() {
        if (<caret>Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 4) {
            requiresExtRv4();
        }
    }

    @RequiresExtension(extension= Build.VERSION_CODES.R, version=4)
    public void requiresExtRv4() {
    }
}