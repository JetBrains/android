package test.pkg;

import android.content.Context;
import android.os.PowerManager;

@SuppressWarnings("unused")
public abstract class WakelockTest extends Context {
    public void acquireLock(PowerManager.WakeLock lock) {
        lock.acquire(1000L); // OK
        <warning descr="Provide a timeout when requesting a wakelock with `PowerManager.Wakelock.acquire(long timeout)`. This will ensure the OS will cleanup any wakelocks that last longer than you intend, and will save your user's battery.">lock.acqui<caret>re()</warning>; // WARN
    }
}