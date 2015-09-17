<error descr="The SDK platform-tools version ((16.0.2)) is too old  to check APIs compiled with API 17; please update">package p1.p2;</error>

import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.view.ViewDebug;

import static android.os.Build.VERSION_CODES.KITKAT_WATCH;
import static android.os.Build.VERSION_CODES.LOLLIPOP;

@SuppressWarnings({"unused", "StatementWithEmptyBody"})
public class Class {
    public void test(ViewDebug.ExportedProperty property) {
        // Test short circuit evaluation
        if (Build.VERSION.SDK_INT > 18 && property.<error descr="Call requires API level 21 (current min is 1): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping">hasAdjacentMapping</error>()) { // ERROR
        }
        if (Build.VERSION.SDK_INT > 19 && property.<error descr="Call requires API level 21 (current min is 1): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping">hasAdjacentMapping</error>()) { // ERROR
        }
        if (Build.VERSION.SDK_INT > 20 && property.hasAdjacentMapping()) { // OK
        }
        if (Build.VERSION.SDK_INT > 21 && property.hasAdjacentMapping()) { // OK
        }
        if (Build.VERSION.SDK_INT > 22 && property.hasAdjacentMapping()) { // OK
        }

        if (Build.VERSION.SDK_INT >= 18 && property.<error descr="Call requires API level 21 (current min is 1): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping">hasAdjacentMapping</error>()) { // ERROR
        }
        if (Build.VERSION.SDK_INT >= 19 && property.<error descr="Call requires API level 21 (current min is 1): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping">hasAdjacentMapping</error>()) { // ERROR
        }
        if (Build.VERSION.SDK_INT >= 20 && property.<error descr="Call requires API level 21 (current min is 1): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping">hasAdjacentMapping</error>()) { // ERROR
        }
        if (Build.VERSION.SDK_INT >= 21 && property.hasAdjacentMapping()) { // OK
        }
        if (Build.VERSION.SDK_INT >= 22 && property.hasAdjacentMapping()) { // OK
        }

        if (Build.VERSION.SDK_INT == 18 && property.<error descr="Call requires API level 21 (current min is 1): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping">hasAdjacentMapping</error>()) { // ERROR
        }
        if (Build.VERSION.SDK_INT == 19 && property.<error descr="Call requires API level 21 (current min is 1): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping">hasAdjacentMapping</error>()) { // ERROR
        }
        if (Build.VERSION.SDK_INT == 20 && property.<error descr="Call requires API level 21 (current min is 1): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping">hasAdjacentMapping</error>()) { // ERROR
        }
        if (Build.VERSION.SDK_INT == 21 && property.hasAdjacentMapping()) { // OK
        }
        if (Build.VERSION.SDK_INT == 22 && property.hasAdjacentMapping()) { // OK
        }

        if (Build.VERSION.SDK_INT < 18 && property.<error descr="Call requires API level 21 (current min is 1): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping">hasAdjacentMapping</error>()) { // ERROR
        }
        if (Build.VERSION.SDK_INT < 22 && property.<error descr="Call requires API level 21 (current min is 1): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping">hasAdjacentMapping</error>()) { // ERROR
        }
        if (Build.VERSION.SDK_INT <= 18 && property.<error descr="Call requires API level 21 (current min is 1): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping">hasAdjacentMapping</error>()) { // ERROR
        }
        if (Build.VERSION.SDK_INT <= 22 && property.<error descr="Call requires API level 21 (current min is 1): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping">hasAdjacentMapping</error>()) { // ERROR
        }

        // Symbolic names instead
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD && property.<error descr="Call requires API level 21 (current min is 1): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping">hasAdjacentMapping</error>()) { // ERROR
        }
        if (Build.VERSION.SDK_INT > VERSION_CODES.KITKAT && property.<error descr="Call requires API level 21 (current min is 1): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping">hasAdjacentMapping</error>()) { // ERROR
        }
        if (Build.VERSION.SDK_INT > KITKAT_WATCH && property.hasAdjacentMapping()) { // OK
        }
        if (Build.VERSION.SDK_INT > LOLLIPOP && property.hasAdjacentMapping()) { // OK
        }

        // Wrong operator
        if (Build.VERSION.SDK_INT > 21 || property.<error descr="Call requires API level 21 (current min is 1): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping">hasAdjacentMapping</error>()) { // ERROR
        }

        // Test multiple conditions in short circuit evaluation
        if (Build.VERSION.SDK_INT > 21 &&
                System.getProperty("something") != null &&
                property.hasAdjacentMapping()) { // OK
        }

        // Test order (still before call)
        if (System.getProperty("something") != null &&
                Build.VERSION.SDK_INT > 21 &&
                property.hasAdjacentMapping()) { // OK
        }

        // Test order (after call)
        if (System.getProperty("something") != null &&
                property.<error descr="Call requires API level 21 (current min is 1): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping">hasAdjacentMapping</error>() && // ERROR
                Build.VERSION.SDK_INT > 21) {
        }

        if (Build.VERSION.SDK_INT > 21 && System.getProperty("something") == null) { // OK
            boolean p = property.hasAdjacentMapping(); // OK
        }
    }
}