package androidx.annotation

import android.os.Build

internal class RequiresApiTest {
    fun test() {
        <error descr="Call requires API level 35 (current min is 1): `requires_35`">requires<caret>_35</error>()
    }

    @RequiresApi(35)
    fun requires_35() {
    }
}

annotation class RequiresApi(val api: Int)