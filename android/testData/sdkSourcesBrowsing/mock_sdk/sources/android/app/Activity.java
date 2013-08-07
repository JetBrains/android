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
