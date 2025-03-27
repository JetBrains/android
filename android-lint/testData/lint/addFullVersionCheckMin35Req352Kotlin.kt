package androidx.annotation

import android.os.Build

annotation class RequiresApi(val api: Int)
internal class SdkExtensionsTest {
    @RequiresApi(35)
    fun test() {
        <error descr="Call requires API level 35.2 (current min is 35): `requires352`">requir<caret>es352</error>()
    }

    @RequiresApi(35*100_000+2) // Use constant in VERSION_CODES when available
    fun requires352() {
    }
}
