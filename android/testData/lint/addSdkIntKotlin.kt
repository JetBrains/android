package p1.p2

import android.os.Build.VERSION
import android.os.Build.VERSION_CODES

object SdkIntTest {
    val <warning descr="This method should be annotated with `@ChecksSdkIntAtLeast(api=VERSION_CODES.N)`">isNougat2</warning>: Boolean
        get() = VERSION.SDK_INT >= VERSION_CODES.N

    fun <warning descr="This method should be annotated with `@ChecksSdkIntAtLeast(api=android.os.Build.VERSION_CODES.N_MR1)`">is<caret>AfterNougat</warning>(): Boolean {
        return VERSION.SDK_INT >= VERSION_CODES.N + 1
    }

    fun <warning descr="This method should be annotated with `@ChecksSdkIntAtLeast(api=VERSION_CODES.N, lambda=0)`">runOnNougat</warning>(runnable: Runnable) {
        if (VERSION.SDK_INT >= VERSION_CODES.N) {
            runnable.run()
        }
    }
}