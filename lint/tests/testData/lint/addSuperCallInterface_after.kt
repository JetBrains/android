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
    override fun foo() {
        super<Foo>.foo()
    }
}
