package p1.p2

import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Build.VERSION_CODES.N_MR1
import androidx.annotation.ChecksSdkIntAtLeast

object SdkIntTest {
    val isNougat2: Boolean
        get() = VERSION.SDK_INT >= VERSION_CODES.N

    @ChecksSdkIntAtLeast(api = N_MR1)
    fun isAfterNougat(): Boolean {
        return VERSION.SDK_INT >= VERSION_CODES.N + 1
    }

    fun runOnNougat(runnable: Runnable) {
        if (VERSION.SDK_INT >= VERSION_CODES.N) {
            runnable.run()
        }
    }
}