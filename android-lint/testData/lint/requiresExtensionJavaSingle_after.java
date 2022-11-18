package androidx.annotation;

import android.os.Build;

public @interface RequiresExtension {
    int extension();
    int version();
}

class SdkExtensionsTest {
    @RequiresExtension(extension = Build.VERSION_CODES.R, version = 4)
    public void test() {
        <caret>requiresExtRv4();
    }

    @RequiresExtension(extension= Build.VERSION_CODES.R, version=4)
    public void requiresExtRv4() {
    }
}