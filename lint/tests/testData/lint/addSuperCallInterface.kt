@file:Suppress("unused")

package p1.p2

import android.support.annotation.CallSuper

open class Bar {
    open fun foo() {}
}

interface Foo {
    @CallSuper
    fun foo() {}
}

class FooImpl : Bar(), Foo {
    override fun <error descr="Overriding method should call `super.foo`">foo<caret></error>() {
    }
}
