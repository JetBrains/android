import android.support.annotation.DrawableRes;

@SuppressWarnings("WeakerAccess")
public class D {
    public static void d(int id) {
        something(id);
    }

    // Entrypoint: We should be able to deduce that id is @DimenRes
    public static void fromD(int id) {
        C.fromC(id);
    }

    public static void something(@DrawableRes int id) {
    }
}
