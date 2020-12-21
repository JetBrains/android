package p1.p2

import android.support.annotation.CallSuper

interface Foo {
    @CallSuper
    fun foo() {}
}

class FooImpl : Foo {
    override fun <error descr="Overriding method should call `super.foo`">foo<caret></error>() {
    }
}
