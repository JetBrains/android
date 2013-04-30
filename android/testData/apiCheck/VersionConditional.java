package p1.p2;

import android.os.Build;
import android.widget.GridLayout;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH;

@SuppressWarnings("UnusedDeclaration")
public class Class {
    public void test() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            new GridLayout(null).getOrientation(); // Not flagged
        } else {
            new GridLayout(null).getOrientation(); // Flagged
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            new GridLayout(null).getOrientation(); // Not flagged
        } else {
            new GridLayout(null).getOrientation(); // Flagged
        }

        if (SDK_INT >= ICE_CREAM_SANDWICH) {
            new GridLayout(null).getOrientation(); // Not flagged
        } else {
            new GridLayout(null).getOrientation(); // Flagged
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            new GridLayout(null).getOrientation(); // Not flagged
        } else {
            new GridLayout(null).getOrientation(); // Flagged
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            new GridLayout(null).getOrientation(); // Flagged
        } else {
            new GridLayout(null).getOrientation(); // Not flagged
        }

        if (Build.VERSION.SDK_INT >= 14) {
            new GridLayout(null).getOrientation(); // Not flagged
        } else {
            new GridLayout(null).getOrientation(); // Flagged
        }
    }
}
