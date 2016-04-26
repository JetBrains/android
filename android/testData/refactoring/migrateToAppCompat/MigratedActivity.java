package p1.p2;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.ShareActionProvider;
import android.view.Menu;
import android.view.MenuItem;

public class MigratedActivity extends AppCompatActivity {

  private ShareActionProvider mShareActionProvider;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    ActionBar actionBar = getSupportActionBar();
    FragmentManager manager = getSupportFragmentManager();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.main, menu);
    MenuItem item = menu.findItem(R.id.help);
    mShareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(item);
    return true;
  }

  public static class MyFragment extends Fragment {
    public MyFragment() {
    }

    @Override
    public void onAttach(Activity context) {
      super.onAttach(context);
    }
  }
}
