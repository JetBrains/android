package p1.p2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.location.LocationManager;

@SuppressWarnings("unused")
@SuppressLint("Registered")
public class LocationTestJava extends Activity {
    public void test(LocationManager manager, String provider) {
        <error>manager.get<caret>LastKnownLocation(provider)</error>;
    }
}
