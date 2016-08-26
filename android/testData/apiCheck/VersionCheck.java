package p1.p2;

import android.os.Build;
@SuppressWarnings({"WeakerAccess", "unused", "StatementWithEmptyBody"})
public class Class {
    public void test() {
        if (<warning descr="Unnecessary; SDK_INT is always >= 16">Build.VERS<caret>ION.SDK_INT >= 3</warning>) {
            System.out.println("Here we are");
        }
        if (<warning descr="Unnecessary; SDK_INT is always >= 16">Build.VERSION.SDK_INT >= 15</warning>) { }
        if (<warning descr="Unnecessary; SDK_INT is always >= 16">Build.VERSION.SDK_INT > 15</warning>) { }
        if (<warning descr="Unnecessary; SDK_INT is never < 16">Build.VERSION.SDK_INT < 15</warning>) { }
        if (<warning descr="Unnecessary; SDK_INT is never < 16">Build.VERSION.SDK_INT <= 15</warning>) { }
        if (Build.VERSION.SDK_INT == 15) { }

        if (Build.VERSION.SDK_INT >= 16) { }
        if (Build.VERSION.SDK_INT > 16) { }
        if (<warning descr="Unnecessary; SDK_INT is never < 16">Build.VERSION.SDK_INT < 16</warning>) { }
        if (Build.VERSION.SDK_INT <= 16) { }
        if (Build.VERSION.SDK_INT == 16) { }

        if (Build.VERSION.SDK_INT >= 17) { }
        if (Build.VERSION.SDK_INT > 17) { }
        if (Build.VERSION.SDK_INT < 17) { }
        if (Build.VERSION.SDK_INT <= 17) { }
        if (Build.VERSION.SDK_INT == 17) { }
    }
}