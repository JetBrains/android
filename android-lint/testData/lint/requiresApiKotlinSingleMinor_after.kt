package androidx.annotation

import android.os.Build

internal class RequiresApiTest {
    @RequiresApi(Build.VERSION_CODES_FULL.VANILLA_ICE_CREAM_2)
    fun test() {
        requires<caret>_352()
    }

    @RequiresApi(35*100_000+2) // Use constant in VERSION_CODES when available
    fun requires_352() {
    }
}

annotation class RequiresApi(val api: Int)