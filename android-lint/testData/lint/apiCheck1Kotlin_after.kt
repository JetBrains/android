@file:Suppress("unused", "UNUSED_PARAMETER", "UNUSED_VARIABLE")

package p1.p2

import android.app.Activity
import android.os.Build

class MyActivity private constructor() : Activity() {

    fun test1() {
        val actionBar = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            actionBar
        } else {
            TODO("VERSION.SDK_INT < HONEYCOMB")
        }
    }
}
