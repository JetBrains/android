@file:Suppress("unused", "UNUSED_PARAMETER")

package p1.p2

import android.support.annotation.CheckResult

class KotlinCheckResultTest2 {
    fun test() {
        enforceFooPermission("permission1", "TODO: message if thrown")
    }

    companion object {
        @CheckResult(suggest = "#enforceFooPermission")
        fun checkFooPermission(name: String): Boolean {
            return true
        }

        fun enforceFooPermission(arg: Int) {}
    }
}
