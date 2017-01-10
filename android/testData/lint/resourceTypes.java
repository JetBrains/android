package p1.p2;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Build;
import android.<error descr="Cannot resolve symbol 'support'">support</error>.annotation.DrawableRes;
import android.view.View;

import static android.content.Context.CONNECTIVITY_SERVICE;

@SuppressWarnings("UnusedDeclaration")
public class ResourceTypes {
    public void testResourceTypeParameters(Context context, int unknown) {
        Resources resources = context.getResources();
        String ok1 = resources.getString(R.string.app_name);
        String ok2 = resources.getString(unknown);
        String ok3 = resources.getString(android.R.string.ok);
        int ok4 = resources.getColor(android.R.color.black);
        if (testResourceTypeReturnValues(context, true) == R.drawable.ic_launcher) { // ok
        }

        //String ok2 = resources.getString(R.string.app_name, 1, 2, 3);
        float error1 = resources.getDimension(<error descr="Expected resource of type dimen">R.string.app_name</error>);
        boolean error2 = resources.getBoolean(<error descr="Expected resource of type bool">R.string.app_name</error>);
        boolean error3 = resources.getBoolean(<error descr="Expected resource of type bool">android.R.drawable.btn_star</error>);
        if (testResourceTypeReturnValues(context, true) == <error descr="Expected resource of type drawable">R.string.app_name</error>) {
        }
        @SuppressWarnings("UnnecessaryLocalVariable")
        int flow = R.string.app_name;
        @SuppressWarnings("UnnecessaryLocalVariable")
        int flow2 = flow;
        boolean error4 = resources.getBoolean(<error descr="Expected resource of type bool">flow2</error>);
    }

    @android.<error descr="Cannot resolve symbol 'support'">support</error>.annotation.DrawableRes
    public int testResourceTypeReturnValues(Context context, boolean useString) {
        if (useString) {
            return <error descr="Expected resource of type drawable">R.string.app_name</error>; // error
        } else {
            return R.drawable.ic_launcher; // ok
        }
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