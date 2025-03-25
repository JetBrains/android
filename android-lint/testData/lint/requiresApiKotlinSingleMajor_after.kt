package androidx.annotation

import android.os.Build

internal class RequiresApiTest {
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun test() {
        requires<caret>_35()
    }

    @RequiresApi(35)
    fun requires_35() {
    }
}

annotation class RequiresApi(val api: Int)