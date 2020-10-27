package p1.p2;
public class Foo {
    static String s;
    static {
        s = <warning descr="Do not hardcode \"/sdcard/\"; use `Environment.getExternalStorageDirectory().getPath()` instead">"/sdcard/<caret>path"</warning>;
    }
}
