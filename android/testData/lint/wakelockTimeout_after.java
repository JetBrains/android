package test.pkg;

import android.content.Context;
import android.os.PowerManager;

@SuppressWarnings("unused")
public abstract class WakelockTest extends Context {
    public void acquireLock(PowerManager.WakeLock lock) {
        lock.acquire(1000L); // OK
        lock.acquire(10*60*1000L /*10 minutes*/); // WARN
    }
}