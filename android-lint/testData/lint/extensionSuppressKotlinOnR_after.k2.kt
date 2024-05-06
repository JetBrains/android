package androidx.annotation

import android.os.Build
import android.os.Build.VERSION_CODES.R
import android.os.ext.SdkExtensions.getExtensionVersion

annotation class RequiresExtension(val extension: Int, val version: Int)
internal class SdkExtensionsTest {
    fun test() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            <caret>if (getExtensionVersion(R) >= 4) {
                requiresExtRv4()
            }
        }
    }

    @RequiresExtension(extension = Build.VERSION_CODES.R, version = 4)
    fun requiresExtRv4() {
    }
}