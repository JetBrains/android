package p1.p2;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

final class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        System.out.println(R.string.app_name);
    }
}
