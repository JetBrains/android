package p1.p2;

import android.app.Activity;

public class X extends Activity {
  public void test() {
    Object o = getResources().getDrawable(R.mipmap.ic_launcher);
  }

  public static final class R {
    public static final class drawable {
      public static int icon=0x7f020000;
    }
    public static final class mipmap {
      public static int ic_launcher=0x7f020001;
    }
  }
}