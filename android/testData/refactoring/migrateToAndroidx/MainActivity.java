package p1.p2;

import android.support.v7.app.AppCompatActivity;
import android.support.design.widget.CoordinatorLayout;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

  CoordinatorLayout lay;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    android.support.design.widget.CoordinatorLayout layout = findViewById(R.id.layout);
  }
}