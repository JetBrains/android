package test.pkg;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.LinearLayout;


public abstract class WrongViewCall extends LinearLayout {
    private MyChild child;

    public WrongViewCall(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        child.draw(canvas);
    }

    private class MyChild extends FrameLayout {
        public MyChild(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
        }
    }
}
