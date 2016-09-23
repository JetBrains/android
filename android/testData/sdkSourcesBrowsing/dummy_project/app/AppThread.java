package app;

import util.UtilClass;

public class AppThread {
    private int myVariable;

    public final int getVariable() {
        return myVariable;
    }

    public void perform(int param) {
        myVariable = param + 1;
        SomeActivity a = new SomeActivity();
        UtilClass.doUtilMethod();
    }
}
