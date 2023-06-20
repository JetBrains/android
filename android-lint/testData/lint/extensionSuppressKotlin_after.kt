package androidx.annotation

import android.os.Build
import android.os.ext.SdkExtensions

annotation class RequiresExtension(val extension: Int, val version: Int)
internal class SdkExtensionsTest {
    fun test() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 1) {
            requiresExtRv4()
        }
    }

    @RequiresExtension(extension = Build.VERSION_CODES.R, version = 1)
    fun requiresExtRv4() {
    }
}