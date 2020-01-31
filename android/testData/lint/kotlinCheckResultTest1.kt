@file:Suppress("unused", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package p1.p2

import android.support.annotation.CheckResult

class KotlinCheckResultTest1 {
    fun test() {
        val ok = check(42) // OK
        <warning descr="The result of `check` is not used; did you mean to call `#replace`?">check<caret>(42)</warning> // ERROR
    }

    companion object {
        @CheckResult(suggest = "#replace")
        fun check(arg: Int): Boolean {
            return true
        }

        fun replace(arg: Int) {}
    }
}
