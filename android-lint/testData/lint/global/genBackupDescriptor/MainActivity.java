package p1.pkg;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;

public class MainActivity extends Activity {

  private static final String PREF_NAME = "p1.pkg.PREFS";
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

    SharedPreferences prefs2 = getSharedPreferences(getString(R.string.pref_name), MODE_PRIVATE);
  }
}
