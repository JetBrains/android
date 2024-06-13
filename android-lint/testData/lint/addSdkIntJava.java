package p1.p2;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;

public class JavaSdkIntTest {
    public static boolean <warning descr="This method should be annotated with `@ChecksSdkIntAtLeast(api=N)`">is<caret>Nougat2</warning>() {
        return SDK_INT >= N;
    }
    public static boolean <warning descr="This method should be annotated with `@ChecksSdkIntAtLeast(api=android.os.Build.VERSION_CODES.N_MR1)`">isAfterNougat</warning>() {
        return SDK_INT >= N + 1;
    }
    public static void <warning descr="This method should be annotated with `@ChecksSdkIntAtLeast(api=N, lambda=0)`">runOnNougat</warning>(Runnable runnable) {
        if (SDK_INT >= N) {
            runnable.run();
        }
    }
}