@file:Suppress("unused", "UNUSED_PARAMETER", "UNUSED_VARIABLE")

package p1.p2

import android.app.Activity

class MyActivity private constructor() : Activity() {

    fun test1() {
        val actionBar = <error descr="Call requires API level 11 (current min is 1): `android.app.Activity#getActionBar`">actionBar<caret></error>
    }
}
