import android.support.annotation.DrawableRes;

public class InferFromInheritance {
    public interface Interface {
        int something(@DrawableRes int foo);
    }

    public static class Parent {
        @DrawableRes
        public int foo(int foo) {
            return foo;
        }
    }

    public static class Child extends Parent implements Interface {
        @Override
        public int foo(int foo) {
            return super.foo(foo);
        }

        @Override
        public int something(int foo) {
            return 0;
        }
    }
}
