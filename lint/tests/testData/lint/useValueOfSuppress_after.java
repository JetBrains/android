package test.pkg;

public class UseValueOf {
    @SuppressWarnings("UnnecessaryBoxing")
    public void useValueOf() {
        @SuppressWarnings("UseValueOf") Integer myInt = <caret>new Integer(5);
    }
}
