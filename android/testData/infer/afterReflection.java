import android.support.annotation.Keep;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@SuppressWarnings({"unused", "WeakerAccess"})
public class Reflection {
    @Keep
    public void usedFromReflection1(int value) {
    }

    @Keep
    public static void usedFromReflection2(int value) {
    }

    @Keep
    public static void usedFromReflection2(int value, int value2) {
    }

    @Keep
    public static void usedFromReflection2(String value) {
    }

    public void reflect1(Object o) throws ClassNotFoundException, NoSuchMethodException,
            InvocationTargetException, IllegalAccessException {
        Class<?> cls = Class.forName("Reflection");
        Method method = cls.getDeclaredMethod("usedFromReflection1", int.class);
        method.invoke(o, 42);
    }

    public void reflect2() throws ClassNotFoundException, NoSuchMethodException,
            InvocationTargetException, IllegalAccessException {
        Class<Reflection> cls = Reflection.class;
        Method method = cls.getDeclaredMethod("usedFromReflection2", int.class);
        method.invoke(null, 42);
    }

    public void reflect3() throws ClassNotFoundException, NoSuchMethodException,
            InvocationTargetException, IllegalAccessException {
        Reflection.class.getDeclaredMethod("usedFromReflection2", int.class, int.class).invoke(null, 42, 42);
    }

    public void reflect4() throws ClassNotFoundException, NoSuchMethodException,
            InvocationTargetException, IllegalAccessException {
        Class.forName("Reflection").getMethod("usedFromReflection2", String.class).invoke(null, "Hello World");
    }
}
