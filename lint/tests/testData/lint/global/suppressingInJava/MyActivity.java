package p1.p2;

import android.app.Activity;
import android.os.Bundle;

public class MyActivity extends Activity
{
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        @SuppressWarnings("AndroidLintUseValueOf")
        Integer n = new Integer(3);
    }
}