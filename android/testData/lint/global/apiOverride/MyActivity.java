package p1.p2;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.GridLayout;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Locale;

public class MyActivity extends Activity {
  private MyActivity() {
  }

  @Override
  public void test1() {
    ActionBar actionBar = getActionBar();
    GridLayout gridLayout = new GridLayout(null);
    int x = View.DRAWING_CACHE_QUALITY_AUTO;
    int y = View.MEASURED_HEIGHT_STATE_SHIFT;
    int alignmentMode = gridLayout.getAlignmentMode();
    gridLayout.setRowOrderPreserved(true);
    setContentView(R.layout.main);
  }

  @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
  @Override
  public void test2(Bundle savedInstanceState) {
    ActionBar actionBar = getActionBar();
    GridLayout gridLayout = new GridLayout(null);
    int x = View.DRAWING_CACHE_QUALITY_AUTO;
    int y = View.MEASURED_HEIGHT_STATE_SHIFT;
    int alignmentMode = gridLayout.getAlignmentMode();
    gridLayout.setRowOrderPreserved(true);
  }

  @SuppressLint("NewApi")
  public void testSuppressed() {
    ActionBar actionBar = getActionBar();
    GridLayout gridLayout = new GridLayout(null);
    int x = View.DRAWING_CACHE_QUALITY_AUTO;
    int y = View.MEASURED_HEIGHT_STATE_SHIFT;
    int alignmentMode = gridLayout.getAlignmentMode();
    gridLayout.setRowOrderPreserved(true);
  }

  // Override test
  @Override
  public boolean isDestroyed() {
    return super.isDestroyed();
  }
}

