package com.example.basiccmakeapp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);

        // Example of a call to a native method
        String s = null;
        for (int i = 0; i < 1000; i++) {
            s = stringFromJNI();
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
            }
        }
        tv.setText(s);
        setContentView(tv);
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}
