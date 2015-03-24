package p1.p2;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.app.Service;
import android.app.backup.BackupAgent;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.ActionProvider;
import android.widget.Button;

// Test case for unused constructors (http://b.android.com/77054). In the below
// it's normal for the *classes* to be unused; it's the *constructors* that are not
// marked as unused (except for the expected cases, e.g. the 4 arg view constructor,
// the non-framework class, etc.)
public class <warning descr="Class 'UnusedConstructors' is never used">UnusedConstructors</warning> {
    public static class <warning descr="Class 'MyButton' is never used">MyButton</warning> extends Button {
        public MyButton(Context context) {
            super(context);
        }

        public MyButton(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public MyButton(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        public <warning descr="Constructor 'MyButton(android.content.Context, android.util.AttributeSet, int, int)' is never used">MyButton</warning>(Context context, AttributeSet attrs, int defStyle, int <warning descr="Parameter 'other' is never used">other</warning>) { // unused
            super(context, attrs, defStyle);
        }
    }

    public static class MyActivity extends Activity {
        public MyActivity() {
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class <warning descr="Class 'MyFragment' is never used">MyFragment</warning> extends Fragment {
        public MyFragment() {
        }
    }

    public static abstract class MyService extends Service {
        public MyService() {
        }
    }

    public static abstract class <warning descr="Class 'MyBackupAgent' is never used">MyBackupAgent</warning> extends BackupAgent {
        public MyBackupAgent() {
        }
    }

    public static abstract class MyContentProvider extends ContentProvider {
        public MyContentProvider() {
        }
    }

    public static abstract class MyBroadcastReceiver extends BroadcastReceiver {
        public MyBroadcastReceiver() {
        }
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public static abstract class <warning descr="Class 'MyActionProvider' is never used">MyActionProvider</warning> extends ActionProvider {

        public MyActionProvider(Context context) {
            super(context);
        }
    }

    public static class <warning descr="Class 'OtherClass' is never used">OtherClass</warning> {
        public <warning descr="Constructor 'OtherClass()' is never used">OtherClass</warning>() { // this constructor *is* unused
        }
        public <warning descr="Constructor 'OtherClass(android.content.Context)' is never used">OtherClass</warning>(Context <warning descr="Parameter 'context' is never used">context</warning>) { // this constructor *is* unused
        }
    }
}