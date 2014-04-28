package p1.p2;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;

public class MyView extends ViewGroup {
    public MyView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
    }
}
