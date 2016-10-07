import android.support.annotation.DimenRes;

@SuppressWarnings("WeakerAccess")
public class A {
    // Entrypoint: we should be able to deduce that id is @DrawableRes
    public static void a(int id) {
        B.b(id);
    }

    public static void fromA(int id) {
        something(id);
    }

    private static void something(@DimenRes int id) {
    }
}
