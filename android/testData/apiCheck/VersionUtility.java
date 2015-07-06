package test.pkg;

import android.os.Build;
import android.widget.GridLayout;

public class Class {
    public void checkApi() {
        if (Utils.isLollipop()) {
            new GridLayout(null); // OK
        }
        if (Utils.isGingerbread()) {
            <error descr="Call requires API level 14 (current min is 1): android.widget.GridLayout#GridLayout">new GridLayout(null)</error>; // ERROR
        }
    }

    private static class Utils {
        public static boolean isLollipop() {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
        }
        public static boolean isGingerbread() {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD;
        }
    }
}
