package template.test.in;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.TextView;

import template.test.in.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    // Used to load the 'in' library on application startup.
    static {
        System.loadLibrary("in");
    }

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Example of a call to a native method
        TextView tv = binding.sampleText;
        tv.setText(stringFromJNI());
    }

    /**
     * A native method that is implemented by the 'in' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}