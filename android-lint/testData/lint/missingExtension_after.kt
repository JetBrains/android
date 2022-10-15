package androidx.annotation

import android.os.Build.VERSION_CODES.R
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresSdkVersion

@RequiresSdkVersion(sdk = 1000000, version = 4)
@RequiresSdkVersion(sdk = R, 4)
@RequiresApi(34)
fun test() {
    rAndRb() // ERROR 1
}

@RequiresSdkVersion(R, 4)
@RequiresSdkVersion(1000000, 4)
fun rAndRb() {
}

annotation class RequiresApi(val api: Int)
@Repeatable annotation class RequiresSdkVersion(val sdk: Int, val version: Int)