package androidx.annotation

import android.os.Build

annotation class RequiresApi(val api: Int)
internal class SdkExtensionsTest {
    fun test() {
        <error descr="Call requires API level 99.9 (current min is 1): `requiresFutureMinor`">requires<caret>FutureMinor</error>()
    }

    // Some future API level >= 36 such that we insert the SDK_INT check.
    // (Picked much higher so test doesn't break when we add known SDK names
    // for the chosen API level.)
    @RequiresApi(99*100_000+9)
    fun requiresFutureMinor() {
    }
}