package androidx.annotation

import android.os.Build
import android.os.ext.SdkExtensions

annotation class RequiresSdkVersion(val sdk: Int, val version: Int)
internal class SdkExtensionsTest {
    fun test() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            <caret>if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 4) {
                requiresExtRv4()
            }
        }
    }

    @RequiresSdkVersion(sdk = Build.VERSION_CODES.R, version = 4)
    fun requiresExtRv4() {
    }
}