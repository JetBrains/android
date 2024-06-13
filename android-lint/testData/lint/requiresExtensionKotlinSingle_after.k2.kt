package androidx.annotation

import android.os.Build
import android.os.Build.VERSION_CODES.R

@Repeatable
annotation class RequiresExtension(val extension: Int, val version: Int)
internal class SdkExtensionsTest {
    @RequiresExtension(extension = R, version = 4)
    fun test() {
        requires<caret>ExtRv4()
    }

    @RequiresExtension(extension = Build.VERSION_CODES.R, version = 4)
    fun requiresExtRv4() {
    }
}