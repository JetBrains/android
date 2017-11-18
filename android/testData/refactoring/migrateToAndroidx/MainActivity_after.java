package p1.p2;

import androidx.app.AppCompatActivity;
import androidx.widget.CoordinatorLayout;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

  CoordinatorLayout lay;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    CoordinatorLayout layout = findViewById(R.id.layout);
  }
}