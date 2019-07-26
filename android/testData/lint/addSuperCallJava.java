package p1.p2;

import android.support.annotation.CallSuper;

@SuppressWarnings("unused")
public class SuperTestJava {
    @SuppressWarnings("InnerClassMayBeStatic")
    public class Parent {
        @CallSuper
        public String foo(String s) {
            return s;
        }
    }

    public class Child extends Parent {
        @Override
        public String <error descr="Overriding method should call `super.foo`">foo<caret></error>(String s) {
            return s;
        }
    }
}