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
        setMyString(stringFromJNI());
        tv.setText(stringFromJNI());
        setContentView(tv);
    }

    /**
     * A dummy method for testing purpose.
     * @param s String, a string
     */
    public void setMyString(String s) {
        String myString = s;
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}
