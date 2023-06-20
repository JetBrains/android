package androidx.annotation

import android.os.Build

@Repeatable
annotation class RequiresExtension(val extension: Int, val version: Int)
annotation class RequiresApi(val api: Int)
internal class SdkExtensionsTest {
    @RequiresExtension(extension = Build.VERSION_CODES.R, version = 4)
    fun test() {
        <caret>requiresExtRv4()
    }

    @RequiresExtension(extension = Build.VERSION_CODES.R, version = 4)
    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 5)
    @RequiresApi(34)
    fun requiresExtRv4() {
    }
}