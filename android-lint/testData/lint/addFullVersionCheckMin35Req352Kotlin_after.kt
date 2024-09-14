package androidx.annotation

import android.os.Build

annotation class RequiresApi(val api: Int)
internal class SdkExtensionsTest {
    @RequiresApi(35)
    fun test() {
        if (<caret>Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.VANILLA_ICE_CREAM_2) {
            requires352()
        }
    }

    @RequiresApi(35*100_000+2) // Use constant in VERSION_CODES when available
    fun requires352() {
    }
}
