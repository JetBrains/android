package p1.p2;

import android.support.annotation.CallSuper;

interface Foo {
    @CallSuper default void bar() {}
}

public class FooImpl implements Foo {
    @Override
    public void bar() {
        Foo.super.bar();
    }
}