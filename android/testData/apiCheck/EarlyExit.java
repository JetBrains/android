
package p1.p2;

import android.os.Build;
import android.widget.GridLayout;

public class Class {
    public void testEarlyExit1() {
        // https://code.google.com/p/android/issues/detail?id=37728
        if (Build.VERSION.SDK_INT < 14) return;

        new GridLayout(null); // OK
    }

    public void testEarlyExit2() {
        if (!Utils.isIcs()) {
            return;
        }

        new GridLayout(null); // OK
    }

    public void testEarlyExit3(boolean nested) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            return;
        }

        if (nested) {
            new GridLayout(null); // OK
        }
    }

    public void testEarlyExit4(boolean nested) {
        if (nested) {
            if (Utils.isIcs()) {
                return;
            }
        }

        <error descr="Call requires API level 14 (current min is 1): new android.widget.GridLayout">new GridLayout</error>(null); // ERROR

        if (Utils.isIcs()) { // too late
            //noinspection UnnecessaryReturnStatement
            return;
        }
    }

    private static class Utils {
        public static boolean isIcs() {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
        }
        public static boolean isGingerbread() {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD;
        }
    }
}