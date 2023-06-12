package androidx.annotation;

import android.os.Build;

public @interface RequiresExtension {
    int extension();
    int version();
}

class SdkExtensionsTest {
    public void test() {
        <error descr="Call requires version 4 of the R SDK (current min is 0): `requiresExtRv4`">requires<caret>ExtRv4</error>();
    }

    @RequiresExtension(extension= Build.VERSION_CODES.R, version=4)
    public void requiresExtRv4() {
    }
}