package p1.p2;

import android.os.Build;
@SuppressWarnings({"WeakerAccess", "unused", "StatementWithEmptyBody"})
public class Class {
    public void test() {
        System.out.println("Here we are");
        if (Build.VERSION.SDK_INT >= 15) { }
        if (Build.VERSION.SDK_INT > 15) { }
        if (Build.VERSION.SDK_INT < 15) { }
        if (Build.VERSION.SDK_INT <= 15) { }
        if (Build.VERSION.SDK_INT == 15) { }

        if (Build.VERSION.SDK_INT >= 16) { }
        if (Build.VERSION.SDK_INT > 16) { }
        if (Build.VERSION.SDK_INT < 16) { }
        if (Build.VERSION.SDK_INT <= 16) { }
        if (Build.VERSION.SDK_INT == 16) { }

        if (Build.VERSION.SDK_INT >= 17) { }
        if (Build.VERSION.SDK_INT > 17) { }
        if (Build.VERSION.SDK_INT < 17) { }
        if (Build.VERSION.SDK_INT <= 17) { }
        if (Build.VERSION.SDK_INT == 17) { }
    }
}