package androidx.annotation

import android.os.Build

annotation class RequiresExtension(val extension: Int, val version: Int)
internal class SdkExtensionsTest {
    fun test() {
        <error descr="Call requires version 1 of the R SDK (current min is 0): `requiresExtRv4`">requires<caret>ExtRv4</error>()
    }

    @RequiresExtension(extension = Build.VERSION_CODES.R, version = 1)
    fun requiresExtRv4() {
    }
}