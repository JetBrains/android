package androidx.annotation

import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.R
import android.os.ext.SdkExtensions.getExtensionVersion

annotation class RequiresExtension(val extension: Int, val version: Int)
internal class SdkExtensionsTest {
    fun test() {
        if (SDK_INT >= R && getExtensionVersion(R) >= 1) {
            requiresExtRv4()
        }
    }

    @RequiresExtension(extension = Build.VERSION_CODES.R, version = 1)
    fun requiresExtRv4() {
    }
}