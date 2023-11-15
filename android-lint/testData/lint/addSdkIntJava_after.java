package p1.p2;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;

import androidx.annotation.ChecksSdkIntAtLeast;

public class JavaSdkIntTest {
    @ChecksSdkIntAtLeast(api = N)
    public static boolean isNougat2() {
        return SDK_INT >= N;
    }
    public static boolean isAfterNougat() {
        return SDK_INT >= N + 1;
    }
    public static void runOnNougat(Runnable runnable) {
        if (SDK_INT >= N) {
            runnable.run();
        }
    }
}