package androidx.annotation

import android.os.Build

@Repeatable
annotation class RequiresSdkVersion(val sdk: Int, val version: Int)
internal class SdkExtensionsTest {
    fun test() {
        <error descr="Call requires version 4 of the R SDK (current min is 0): `requiresExtRv4`">requires<caret>ExtRv4</error>()
    }

    @RequiresSdkVersion(sdk = Build.VERSION_CODES.R, version = 4)
    fun requiresExtRv4() {
    }
}