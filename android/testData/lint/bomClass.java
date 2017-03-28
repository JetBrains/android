package test.pkg;
import android.annotation.SuppressLint;
public class MyTest {
    public void test1() {
        String s = "\uFEFF"; // OK
        String t = "<error>﻿</error>"; // ERROR
    }
    @SuppressLint("ByteOrderMark")
    public void test2() {
        String s = "﻿"; //OK/suppressed
    }
}
