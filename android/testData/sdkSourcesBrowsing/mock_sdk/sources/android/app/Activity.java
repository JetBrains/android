package android.app;

import util.UtilClass;

public class Activity {
    private int myId = 1;

    public void onCreate() {
        ActivityThread t = new ActivityThread();
        t.perform(myId);
    }

    public void onDestroy() {
        f(new MyActivityThread());

      int[] a = com.android.internal.R.styleable.Fragment;
      int n1 = com.android.internal.R.color.my_white;
      int n2 = com.android.internal.R.color.<error>unknown_resource</error>;

      int m1 = android.R.string.cancel;
      int m2 = android.R.id.checkbox;
      int m3 = android.R.string.private_str;
      int m4 = android.R.id.privatte;

      m1 = com.android.internal.R.string.cancel;
      m2 = com.android.internal.R.id.checkbox;
      m3 = com.android.internal.R.string.private_str;
      m4 = com.android.internal.R.id.privatte;

      m1 = android.R.drawable.menuitem_background;
      m1 = com.android.internal.R.drawable.menuitem_background;
    }

    private void f(ActivityThread t) {
    }

    public void func(ObjClass param) {
      ObjClass c = new ObjClass();
    }

    public class MyActivityThread extends ActivityThread {
        public MyActivityThread() {
        }

        public void perform(int param) {
            super.perform(param);
            UtilClass.utilMethod();
        }
    }
}
