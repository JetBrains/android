@file:Suppress("unused", "UNUSED_PARAMETER")

package p1.p2

import android.support.annotation.CheckResult

class KotlinCheckResultTest2 {
    fun test() {
        <warning descr="The result of `checkFooPermission` is not used; did you mean to call `#enforceFooPermission`?">checkFooPermission<caret>("permission1")</warning>
    }

    companion object {
        @CheckResult(suggest = "#enforceFooPermission")
        fun checkFooPermission(name: String): Boolean {
            return true
        }

        fun enforceFooPermission(arg: Int) {}
    }
}
