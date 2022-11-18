package androidx.annotation

import android.os.Build.VERSION_CODES.R
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresExtension

@RequiresExtension(extension = R, 4)
@RequiresApi(34)
fun test() {
    <error descr="Call requires version 4 of SDK 1000000 (current min is 0): `rAndRb`"><caret>rAndRb</error>() // ERROR 1
}

@RequiresExtension(R, 4)
@RequiresExtension(1000000, 4)
fun rAndRb() {
}

annotation class RequiresApi(val api: Int)
@Repeatable annotation class RequiresExtension(val extension: Int, val version: Int)
