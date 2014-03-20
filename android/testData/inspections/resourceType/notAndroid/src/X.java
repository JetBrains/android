import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Build;
import android.support.annotation.DrawableRes;
import android.view.View;

import static android.content.Context.CONNECTIVITY_SERVICE;

@SuppressWarnings("UnusedDeclaration")
public class X {
    public void testResourceTypeParameters(Context context, int unknown) {
        Resources resources = context.getResources();
        String ok1 = resources.getString(R.string.app_name);
        String ok2 = resources.getString(unknown);
        String ok3 = resources.getString(android.R.string.ok);
        int ok4 = resources.getColor(android.R.color.black);
        if (testResourceTypeReturnValues(context, true) == R.drawable.ic_launcher) { // ok
        }

        //String ok2 = resources.getString(R.string.app_name, 1, 2, 3);
        float error1 = resources.getDimension(R.string.app_name);
        boolean error2 = resources.getBoolean(R.string.app_name);
        boolean error3 = resources.getBoolean(android.R.drawable.btn_star);
        if (testResourceTypeReturnValues(context, true) == R.string.app_name) {
        }
        @SuppressWarnings("UnnecessaryLocalVariable")
        int flow = R.string.app_name;
        @SuppressWarnings("UnnecessaryLocalVariable")
        int flow2 = flow;
        boolean error4 = resources.getBoolean(flow2);
    }

    @DrawableRes
    public int testResourceTypeReturnValues(Context context, boolean useString) {
        if (useString) {
            return R.string.app_name; // error
        } else {
            return R.drawable.ic_launcher; // ok
        }
    }

    public static String UNRELATED = "unrelated";

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public void testStringDef(Context context, String unknown) {
        Object ok1 = context.getSystemService(unknown);
        Object ok2 = context.getSystemService(Context.CLIPBOARD_SERVICE);
        Object ok3 = context.getSystemService(android.content.Context.WINDOW_SERVICE);
        Object ok4 = context.getSystemService(CONNECTIVITY_SERVICE);
        @SuppressWarnings("UnnecessaryLocalVariable")
        String flow1 = Context.CLIPBOARD_SERVICE;
        @SuppressWarnings("UnnecessaryLocalVariable")
        String flow2 = flow1;
        Object ok5 = context.getSystemService(flow2);

        Object error1 = context.getSystemService("wrong");
        Object error2 = context.getSystemService(Notification.EXTRA_INFO_TEXT);
        @SuppressWarnings("UnnecessaryLocalVariable")
        String flow3 = Notification.EXTRA_INFO_TEXT;
        @SuppressWarnings("UnnecessaryLocalVariable")
        String flow4 = flow3;
        Object error3 = context.getSystemService(flow4);
    }

    @SuppressLint("UseCheckPermission")
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public void testIntDef(Context context, int unknown, View view) {
        view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); // OK
        view.setLayoutDirection(View.TEXT_ALIGNMENT_TEXT_START); // Error
        view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL | View.LAYOUT_DIRECTION_RTL); // Error
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public void testIntDefFlags(Context context, int unknown, Intent intent,
                           ServiceConnection connection) {
        // Flags
        Object ok1 = context.bindService(intent, connection, 0);
        Object ok2 = context.bindService(intent, connection, -1);
        Object ok3 = context.bindService(intent, connection, Context.BIND_ABOVE_CLIENT);
        Object ok4 = context.bindService(intent, connection, Context.BIND_ABOVE_CLIENT
                | Context.BIND_AUTO_CREATE);
        int flags1 = Context.BIND_ABOVE_CLIENT | Context.BIND_AUTO_CREATE;
        Object ok5 = context.bindService(intent, connection, flags1);

        Object error1 = context.bindService(intent, connection,
                Context.BIND_ABOVE_CLIENT | Context.CONTEXT_IGNORE_SECURITY);
        int flags2 = Context.BIND_ABOVE_CLIENT | Context.CONTEXT_IGNORE_SECURITY;
        Object error2 = context.bindService(intent, connection, flags2);
    }

    public static final class R {
        public static final class drawable {
            public static final int ic_launcher=0x7f020057;
        }
        public static final class string {
            public static final int app_name=0x7f0a000e;
        }
    }
}
