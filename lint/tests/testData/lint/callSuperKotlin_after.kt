package p1.p2

import android.support.annotation.CallSuper

interface Foo {
    @CallSuper
    fun foo() {}
}

class FooImpl : Foo {
    override fun foo() {
        super.foo()
    }
}
