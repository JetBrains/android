package androidx.annotation;

import android.os.Build;

public @interface RequiresExtension {
    int extension();
    int version();
}

class SdkExtensionsTest {
    public void test() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && android.os.ext.SdkExtensions.getExtensionVersion(30) >= 4<caret>) {
            requiresExtRv4();
        }
    }

    @RequiresExtension(extension= Build.VERSION_CODES.R, version=4)
    public void requiresExtRv4() {
    }
}