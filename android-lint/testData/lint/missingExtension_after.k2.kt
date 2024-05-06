package androidx.annotation

import android.os.ext.SdkExtensions
import android.os.Build.VERSION_CODES.R
import android.os.ext.SdkExtensions.AD_SERVICES
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresExtension

@RequiresExtension(extension = AD_SERVICES, version = 4)
@RequiresExtension(extension = R, 4)
@RequiresApi(34)
fun test() {
    rAndRb() // ERROR 1
}

@RequiresExtension(R, 4)
@RequiresExtension(extension = SdkExtensions.AD_SERVICES, 4)
fun rAndRb() {
}

annotation class RequiresApi(val api: Int)
@Repeatable annotation class RequiresExtension(val extension: Int, val version: Int)
