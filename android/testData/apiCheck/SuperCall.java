<error descr="The SDK platform-tools version ((16.0.2)) is too old  to check APIs compiled with API 17; please update">package p1.p2;</error>

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;

@SuppressWarnings("UnusedDeclaration")
public class Class extends View {
    public Class(Context context) {
        super(context);
    }

    protected boolean dispatchGenericFocusedEvent(MotionEvent event) {
        return super.dispatchGenericFocusedEvent(event); // OK: same method
    }

    public void randomMethod(MotionEvent event) {
        super.<error descr="Call requires API level 14 (current min is 1): android.view.View#dispatchGenericFocusedEvent">dispatchGenericFocusedEvent</error>(event); // ERROR
    }
}