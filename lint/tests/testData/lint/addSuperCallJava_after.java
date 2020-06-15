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
        public String foo<caret>(String s) {
            super.foo(s);
            return s;
        }
    }
}