<error descr="The SDK platform-tools version ((16.0.2)) is too old  to check APIs compiled with API 17; please update">package p1.p2;</error>

import android.app.Application;

import java.lang.ClassNotFoundException;
import java.lang.IllegalAccessException;
import java.lang.NoSuchMethodException;
import java.lang.reflect.InvocationTargetException;

public class Class {
    public void reflect(int x) {
        try {
            thrower();
        } catch (<error descr="Multi-catch with these reflection exceptions requires API level 19 (current min is 1) because they get compiled to the common but new super type `ReflectiveOperationException`. As a workaround either create individual catch statements, or catch `Exception`.">ClassNotFoundException | NoSuchMethodException | IllegalAccessException ignore</error>) {
            ignore.printStackTrace();
        }
    }

    protected void thrower() throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException {
        // TODO
    }
}