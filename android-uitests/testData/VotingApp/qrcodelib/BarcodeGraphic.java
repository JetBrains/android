/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package qrcodelib;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.google.android.gms.vision.barcode.Barcode;

/**
 * Graphic instance for rendering barcode position, size, and ID within an associated graphic
 * overlay view.
 */
public class BarcodeGraphic extends GraphicOverlay.Graphic {

    private int mId;

    private static int mCurrentColorIndex = 0;

    private Paint mRectPaint;
    private Paint mTextPaint;
    private volatile Barcode mBarcode;

    private int mStrokeWidth = 24;
    private int mCornerWidth = 64;
    private int mCorderPadding = mStrokeWidth / 2;

    public BarcodeGraphic(GraphicOverlay overlay, final int trackerColor) {
        super(overlay);

        mRectPaint = new Paint();
        mRectPaint.setColor(trackerColor);
        mRectPaint.setStyle(Paint.Style.STROKE);
        mRectPaint.setStrokeWidth(mStrokeWidth);
        //mRectPaint.setAlpha(100);

        mTextPaint = new Paint();
        mTextPaint.setColor(trackerColor);
        mTextPaint.setFakeBoldText(true);
        mTextPaint.setTextSize(46.0f);
    }

    public int getId() {
        return mId;
    }

    public void setId(int id) {
        this.mId = id;
    }

    public Barcode getBarcode() {
        return mBarcode;
    }

    /**
     * Updates the barcode instance from the detection of the most recent frame.  Invalidates the
     * relevant portions of the overlay to trigger a redraw.
     */
    void updateItem(Barcode barcode) {
        mBarcode = barcode;
        postInvalidate();
    }

    /**
     * Draws the barcode annotations for position, size, and raw value on the supplied canvas.
     */
    @Override
    public void draw(Canvas canvas) {
        Barcode barcode = mBarcode;
        if (barcode == null) {
            return;
        }

        // Draws the bounding box around the barcode.
        RectF rect = new RectF(barcode.getBoundingBox());
        rect.left = translateX(rect.left);
        rect.top = translateY(rect.top);
        rect.right = translateX(rect.right);
        rect.bottom = translateY(rect.bottom);

        //canvas.drawRect(rect, mRectPaint);

        /**
         * Draw the top left corner
         */
        canvas.drawLine(rect.left - mCorderPadding, rect.top, rect.left + mCornerWidth, rect.top, mRectPaint);
        canvas.drawLine(rect.left, rect.top, rect.left, rect.top + mCornerWidth, mRectPaint);

        /**
         * Draw the bottom left corner
         */
        canvas.drawLine(rect.left, rect.bottom, rect.left, rect.bottom - mCornerWidth, mRectPaint);
        canvas.drawLine(rect.left - mCorderPadding, rect.bottom, rect.left + mCornerWidth, rect.bottom, mRectPaint);

        /**
         * Draw the top right corner
         */
        canvas.drawLine(rect.right + mCorderPadding, rect.top, rect.right - mCornerWidth, rect.top, mRectPaint);
        canvas.drawLine(rect.right, rect.top, rect.right, rect.top + mCornerWidth, mRectPaint);

        /**
         * Draw the bottom right corner
         */
        canvas.drawLine(rect.right + mCorderPadding, rect.bottom, rect.right - mCornerWidth, rect.bottom, mRectPaint);
        canvas.drawLine(rect.right, rect.bottom, rect.right, rect.bottom - mCornerWidth, mRectPaint);

        // Draws a label at the bottom of the barcode indicate the barcode value that was detected.
        canvas.drawText(barcode.displayValue, rect.left, rect.bottom + 100, mTextPaint);
    }
}
