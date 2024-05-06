@file:Suppress("unused", "UNUSED_PARAMETER", "UNUSED_VARIABLE")

package p1.p2

import android.app.Activity
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.HONEYCOMB

class MyActivity private constructor() : Activity() {

    fun test1() {
        val actionBar = if (SDK_INT >= HONEYCOMB) {
            actionBar
        } else {
            TODO("VERSION.SDK_INT < HONEYCOMB")
        }
    }
}
