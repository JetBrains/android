package com.android.tools.tests.layout;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;


public class MyPorterDuffView extends View {

    private Paint mPaint;
    private String mOverlayText;

    public MyPorterDuffView(Context context) {
        super(context);
        init(context, null, 0);
    }

    public MyPorterDuffView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public MyPorterDuffView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle);
    }

    private void init(Context context, AttributeSet attrs, int defStyle) {
        // Load attributes
        int[] attrsWanted = R.styleable.MyPorterDuffView;
        final TypedArray a = context.obtainStyledAttributes(
                attrs, attrsWanted, defStyle, 0);

        int colorFilterMode = a.getInt(R.styleable.MyPorterDuffView_colorFilterMode, 0);
        int paintMode = a.getInt(R.styleable.MyPorterDuffView_paintMode, 0);

        int color = a.getColor(R.styleable.MyPorterDuffView_my_color, 0xa000ff00);  // green with some transparency
        mOverlayText = a.getString(R.styleable.MyPorterDuffView_overlay_text);
        mOverlayText = mOverlayText == null ? getResources().getString(R.string.hello_world) : mOverlayText;
        a.recycle();

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
        mPaint.setStyle(Paint.Style.FILL);
        setColorFilter(color, PorterDuff.Mode.values()[colorFilterMode],
                PorterDuff.Mode.values()[paintMode]);

        Typeface typeface = Typeface.create("sans-serif-smallcaps", Typeface.NORMAL);
        mPaint.setTypeface(typeface);
        mPaint.setTextSize(36f);
    }

    public void setColorFilter(int color, PorterDuff.Mode mode, PorterDuff.Mode mode2) {
        PorterDuffColorFilter colorFilter = new PorterDuffColorFilter(color, mode);
        mPaint.setColorFilter(colorFilter);
        mPaint.setXfermode(new PorterDuffXfermode(mode2));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();

        int paddingRight = getPaddingRight();
        int paddingBottom = getPaddingBottom();

        int contentWidth = getWidth() - paddingLeft - paddingRight;
        int contentHeight = getHeight() - paddingTop - paddingBottom;

        drawNew(canvas, paddingLeft, paddingTop, contentWidth, contentHeight);
    }

    private void drawNew(Canvas canvas, int left, int top, int width, int height) {

        // Draw a red rect.
        int l = left + (int) (width * 0.05);
        int t = top + (int) (height * 0.05);
        int w = (int) (width * 0.8);
        int h = (int) (height * 0.8);
        mPaint.setColor(0xffff0000);  // red.
        canvas.drawRect(l, t, w + l, t + h, mPaint);

        // Draw a blue circle.
        l = left + (int) (width * 0.15);
        t = top + (int) (height * 0.15);
        mPaint.setColor(0xff0000ff); // blue.
        canvas.drawCircle(l + w / 2f, t + h / 2f, (w < h ? w : h) / 2f, mPaint);

        // Draw the overlay text.
        mPaint.setColor(0xff000000);  // black.
        CharSequence text = mOverlayText;
        canvas.drawText(text, 0, text.length(), left + 0.05f * w, top + 0.95f * height, mPaint);
    }

}
