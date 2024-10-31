@file:Suppress("unused")

package p1.p2;

import android.os.Build
import android.support.annotation.RequiresApi

class KotlinRemoveObsoleteSdkCheckTest2 {
    fun neverTrue() {
        if (<warning descr="Unnecessary; `SDK_INT` is never < 19">Build.VERSION.SDK_INT<caret> == 14</warning>) {
            // This is pointless
        } else {
            // This will always be called
        }
    }
}