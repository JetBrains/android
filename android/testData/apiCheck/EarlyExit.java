<error descr="The SDK platform-tools version ((16.0.2)) is too old  to check APIs compiled with API 17; please update">package p1.p2;</error>

import android.os.Build;
import android.widget.GridLayout;

public class Class {
    public void testEarlyExit1() {
        // https://code.google.com/p/android/issues/detail?id=37728
        if (Build.VERSION.SDK_INT < 11) return;

        new GridLayout(null); // OK
    }

    public void testEarlyExit2() {
        if (Utils.isLollipop()) {
            return;
        }

        new GridLayout(null); // OK
    }

    public void testEarlyExit3(boolean nested) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        if (nested) {
            new GridLayout(null); // OK
        }
    }

    public void testEarlyExit4(boolean nested) {
        if (nested) {
            if (Utils.isLollipop()) {
                return;
            }
        }

        <error descr="Call requires API level 14 (current min is 1): android.widget.GridLayout#GridLayout">new GridLayout(null)</error>; // ERROR

        if (Utils.isLollipop()) { // too late
            //noinspection UnnecessaryReturnStatement
            return;
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
