@file:Suppress("unused")

package p1.p2

import android.support.annotation.CallSuper

class SuperTest {
    open class Parent() {
        @CallSuper open fun bar(arg: String) { }
    }

    class Child : Parent() {
        override fun bar(arg: String) {
            super.bar(arg)
            // Comment
        }
    }
}
