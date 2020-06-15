package p1.p2;

import android.support.annotation.CallSuper;

interface Foo {
    @CallSuper default void bar() {}
}

public class FooImpl implements Foo {
    @Override
    public void <error descr="Overriding method should call `super.bar`">b<caret>ar</error>() {
    }
}