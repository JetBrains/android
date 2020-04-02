package test.pkg;

public class UseValueOf {
    @SuppressWarnings("UnnecessaryBoxing")
    public void useValueOf() {
        Integer myInt = <warning descr="Use `Integer.valueOf(5)` instead"><caret>new Integer(5)</warning>;
    }
}
