package test.pkg;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.TextView;

public class CustomComponent extends TextView {
    private final boolean mOnMeasureFail;
    private final boolean mOnLayoutFail;
    private final boolean mOnDraw;

    public CustomComponent(Context context, AttributeSet attrs) {
        super(context, attrs);

        String failureMode = attrs.getAttributeValue(null, "failure");

        mOnMeasureFail = "onMeasure".equals(failureMode);
        mOnLayoutFail = "onLayout".equals(failureMode);
        mOnDraw = "onDraw".equals(failureMode);
        setText("Custom");
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mOnMeasureFail) {
            throw new NullPointerException();
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (mOnLayoutFail) {
            throw new NullPointerException();
        }

        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mOnDraw) {
            throw new NullPointerException();
        }

        super.onDraw(canvas);
    }
}
