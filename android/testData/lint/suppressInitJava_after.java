package p1.p2;

import android.annotation.SuppressLint;

@SuppressLint("SdCardPath")
public class Foo {
    static String s;
    static {
        s = "/sdcard/path";
    }
}
