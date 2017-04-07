package app;

import android.app.Activity;
import android.os.Bundle;

import util.UtilClass;

import java.util.TimerTask;

public class SomeActivity extends Activity {
    private int myId = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppThread t = new AppThread();
        t.perform(myId);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        handleThread(new MyAppThread());

        int string_resource = android.R.string.cancel;
        int drawable_resource = android.R.drawable.menuitem_background;
    }

    private void handleThread(AppThread t) {
    }

    private void createSdkObj() {
        TimerTask doNothingTask = new TimerTask() {
            @Override
            public void run() {
            }
        };
    }

    public class MyAppThread extends AppThread {
        @Override
        public void perform(int param) {
            super.perform(param);
            UtilClass.doUtilMethod();
        }
    }
}

