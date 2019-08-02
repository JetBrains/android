package p1.p2;

import android.support.annotation.CheckResult;

@SuppressWarnings({"unused", "WeakerAccess", "UnusedReturnValue"})
public class JavaCheckResultTest2 {
    @CheckResult(suggest="#enforceFooPermission")
    public static boolean checkFooPermission(String name) {
        return true;
    }

    public void enforceFooPermission(int arg) {
    }

    public void test() {
        <warning descr="The result of `checkFooPermission` is not used; did you mean to call `#enforceFooPermission`?">checkFooPermission<caret>("permission1")</warning>;
    }
}
