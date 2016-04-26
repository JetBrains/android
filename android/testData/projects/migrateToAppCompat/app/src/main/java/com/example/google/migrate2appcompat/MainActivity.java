package com.example.google.migrate2appcompat;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ShareActionProvider;

public class MainActivity extends Activity {

    private ShareActionProvider mShareActionProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ActionBar actionBar = getActionBar();
        FragmentManager manager = getFragmentManager();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        mShareActionProvider = (ShareActionProvider) menu.findItem(R.id.share).getActionProvider();
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
