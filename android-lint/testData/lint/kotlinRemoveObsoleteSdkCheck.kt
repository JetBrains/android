@file:Suppress("unused")

package p1.p2

import android.os.Build
import android.support.annotation.RequiresApi

class KotlinRemoveObsoleteSdkCheckTest {
    fun test() {
        if (<warning descr="Unnecessary; `SDK_INT` is always >= 19">Build.VERSION.SDK_INT<caret> >= Build.VERSION_CODES.ICE_CREAM_SANDWICH</warning>) {
            // My comment before 4
            requiresApi14()
            // My comment after
        } else {
            // This should be deleted
        }
    }

    <warning descr="Unnecessary; `SDK_INT` is always >= 14">@RequiresApi(14)</warning>
    fun requiresApi14() {
    }
}
