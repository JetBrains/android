package p1.p2;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.widget.GridLayout;

@SuppressLint("NewApi")
public class Class extends GridLayout implements
        View.OnSystemUiVisibilityChangeListener, OnLayoutChangeListener {

    public Class(Context context) {
        super(context);
    }

    @Override
    public void onSystemUiVisibilityChange(int visibility) {
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right,
                               int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
    }
}
