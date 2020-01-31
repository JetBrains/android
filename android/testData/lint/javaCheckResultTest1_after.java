package p1.p2;

import android.support.annotation.CheckResult;

@SuppressWarnings({"unused", "WeakerAccess"})
public class JavaCheckResultTest1 {
    @CheckResult(suggest="#replace")
    public static boolean check(int arg) {
        return true;
    }
    
    public void test() {
        boolean ok = check(42); // OK
        replace(42); // ERROR
    }

    public void replace(int arg) {
    }
}
