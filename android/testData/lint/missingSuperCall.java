package test.pkg;

import android.app.Activity;
import android.os.Bundle;

public class SuperCallTest {
    public static class AppCompatActivity extends Activity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        }
    }

    public static class MainActivity extends AppCompatActivity {
        @Override
        protected void <error descr="Overriding method should call `super.onCreate`">onCreate</error>(Bundle savedInstanceState) {
        }
    }
}