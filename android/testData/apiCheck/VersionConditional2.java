<error descr="The SDK platform-tools version ((16.0.2)) is too old  to check APIs compiled with API 17; please update">package p1.p2;</error>

import android.graphics.drawable.Drawable;
import android.view.View;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES;
import static android.os.Build.VERSION_CODES.GINGERBREAD;
import static android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH;
import static android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1;
import static android.os.Build.VERSION_CODES.JELLY_BEAN;

@SuppressWarnings({"ConstantConditions", "StatementWithEmptyBody"})
public class Class {
    // Requires API 16 (JELLY_BEAN)
    // root.setBackground(background);

    private void testGreaterThan(View root, Drawable background) {
        if (SDK_INT > GINGERBREAD) {
            root.<error descr="Call requires API level 16 (current min is 1): android.view.View#setBackground">setBackground</error>(background); // Flagged
        }

        if (SDK_INT > ICE_CREAM_SANDWICH) {
            root.<error descr="Call requires API level 16 (current min is 1): android.view.View#setBackground">setBackground</error>(background); // Flagged
        }

        if (SDK_INT > ICE_CREAM_SANDWICH_MR1) { // => SDK_INT >= JELLY_BEAN
            root.setBackground(background); // Not flagged
        }

        if (SDK_INT > JELLY_BEAN) {
            root.setBackground(background); // Not flagged
        }

        if (SDK_INT > VERSION_CODES.JELLY_BEAN_MR1) {
            root.setBackground(background); // Not flagged
        }
    }

    private void testGreaterThanOrEquals(View root, Drawable background) {
        if (SDK_INT >= GINGERBREAD) {
            root.<error descr="Call requires API level 16 (current min is 1): android.view.View#setBackground">setBackground</error>(background); // Flagged
        }

        if (SDK_INT >= ICE_CREAM_SANDWICH) {
            root.<error descr="Call requires API level 16 (current min is 1): android.view.View#setBackground">setBackground</error>(background); // Flagged
        }

        if (SDK_INT >= ICE_CREAM_SANDWICH_MR1) {
            root.<error descr="Call requires API level 16 (current min is 1): android.view.View#setBackground">setBackground</error>(background); // Flagged
        }

        if (SDK_INT >= JELLY_BEAN) {
            root.setBackground(background); // Not flagged
        }

        if (SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
            root.setBackground(background); // Not flagged
        }
    }

    private void testLessThan(View root, Drawable background) {
        if (SDK_INT < GINGERBREAD) {
            // Other
        } else {
            root.<error descr="Call requires API level 16 (current min is 1): android.view.View#setBackground">setBackground</error>(background); // Flagged
        }

        if (SDK_INT < ICE_CREAM_SANDWICH) {
            // Other
        } else {
            root.<error descr="Call requires API level 16 (current min is 1): android.view.View#setBackground">setBackground</error>(background); // Flagged
        }

        if (SDK_INT < ICE_CREAM_SANDWICH_MR1) {
            // Other
        } else {
            root.<error descr="Call requires API level 16 (current min is 1): android.view.View#setBackground">setBackground</error>(background); // Flagged
        }

        if (SDK_INT < JELLY_BEAN) {
            // Other
        } else {
            root.setBackground(background); // Not flagged
        }

        if (SDK_INT < VERSION_CODES.JELLY_BEAN_MR1) {
            // Other
        } else {
            root.setBackground(background); // Not flagged
        }
    }

    private void testLessThanOrEqual(View root, Drawable background) {
        if (SDK_INT <= GINGERBREAD) {
            // Other
        } else {
            root.<error descr="Call requires API level 16 (current min is 1): android.view.View#setBackground">setBackground</error>(background); // Flagged
        }

        if (SDK_INT <= ICE_CREAM_SANDWICH) {
            // Other
        } else {
            root.<error descr="Call requires API level 16 (current min is 1): android.view.View#setBackground">setBackground</error>(background); // Flagged
        }

        if (SDK_INT <= ICE_CREAM_SANDWICH_MR1) {
            // Other
        } else { // => SDK_INT >= JELLY_BEAN
            root.setBackground(background); // Not flagged
        }

        if (SDK_INT <= JELLY_BEAN) {
            // Other
        } else {
            root.setBackground(background); // Not flagged
        }

        if (SDK_INT <= VERSION_CODES.JELLY_BEAN_MR1) {
            // Other
        } else {
            root.setBackground(background); // Not flagged
        }
    }

    private void testEquals(View root, Drawable background) {
        if (SDK_INT == GINGERBREAD) {
            root.<error descr="Call requires API level 16 (current min is 1): android.view.View#setBackground">setBackground</error>(background); // Flagged
        }

        if (SDK_INT == ICE_CREAM_SANDWICH) {
            root.<error descr="Call requires API level 16 (current min is 1): android.view.View#setBackground">setBackground</error>(background); // Flagged
        }

        if (SDK_INT == ICE_CREAM_SANDWICH_MR1) {
            root.<error descr="Call requires API level 16 (current min is 1): android.view.View#setBackground">setBackground</error>(background); // Flagged
        }

        if (SDK_INT == JELLY_BEAN) {
            root.setBackground(background); // Not flagged
        }

        if (SDK_INT == VERSION_CODES.JELLY_BEAN_MR1) {
            root.setBackground(background); // Not flagged
        }
    }
}