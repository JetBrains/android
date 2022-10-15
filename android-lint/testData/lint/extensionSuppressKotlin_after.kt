package androidx.annotation

import android.os.Build
import android.os.ext.SdkExtensions

annotation class RequiresSdkVersion(val sdk: Int, val version: Int)
internal class SdkExtensionsTest {
    fun test() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 4) {
            requiresExtRv4()
        }
    }

    @RequiresSdkVersion(sdk = Build.VERSION_CODES.R, version = 4)
    fun requiresExtRv4() {
    }
}