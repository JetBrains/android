@file:Suppress("unused")

package p1.p2

import android.os.Build
import android.support.annotation.RequiresApi

class KotlinRemoveObsoleteSdkCheckTest {
    fun test() {
        // My comment before 4
        requiresApi14()
        // My comment after
    }

    @RequiresApi(14)
    fun requiresApi14() {
    }
}
