package androidx.annotation

import android.os.Build

@Repeatable
annotation class RequiresSdkVersion(val sdk: Int, val version: Int)
internal class SdkExtensionsTest {
    @RequiresSdkVersion(sdk = Build.VERSION_CODES.R, version = 4)
    fun test() {
        <caret>requiresExtRv4()
    }

    @RequiresSdkVersion(sdk = Build.VERSION_CODES.R, version = 4)
    fun requiresExtRv4() {
    }
}