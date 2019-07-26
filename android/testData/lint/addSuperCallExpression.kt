@file:Suppress("unused", "UNUSED_PARAMETER")

package p1.p2

import android.support.annotation.CallSuper

fun bar(s: String) { }

class SuperTest {

    open class Parent() {
        @CallSuper
        open fun foo(arg: String) { }
    }

    class Child : Parent() {
        override fun <error descr="Overriding method should call `super.foo`">foo<caret></error>(arg: String) = bar("foo")
    }
}
