package androidx.annotation

import android.os.Build

annotation class RequiresExtension(val extension: Int, val version: Int)
internal class SdkExtensionsTest {
    fun test() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            <error descr="Call requires version 4 of the R Extensions SDK (current min is 0): `requiresExtRv4`">requires<caret>ExtRv4</error>()
        }
    }

    @RequiresExtension(extension = Build.VERSION_CODES.R, version = 4)
    fun requiresExtRv4() {
    }
}