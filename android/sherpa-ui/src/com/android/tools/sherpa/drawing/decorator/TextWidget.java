/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.sherpa.drawing.decorator;

import com.android.tools.sherpa.drawing.ViewTransform;
import com.google.tnt.solver.widgets.ConstraintWidget;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

/**
 * Decorator for text widgets
 */
public class TextWidget extends WidgetDecorator {
    static boolean DO_WRAP = false;
    protected int mHorizontalPadding = 0;
    protected int mVerticalPadding = 0;
    protected int mVerticalMargin = 0;
    protected int mHorizontalMargin = 0;
    protected boolean mToUpperCase = false;
    protected static final int TEXT_ALIGNMENT_VIEW_START = 5;
    protected static final int TEXT_ALIGNMENT_VIEW_END = 6;
    protected static final int TEXT_ALIGNMENT_CENTER = 4;
    protected int mAlignmentX = TEXT_ALIGNMENT_VIEW_START;
    protected int mAlignmentY = TEXT_ALIGNMENT_VIEW_START;
    private String mText;
    protected Font mFont = new Font("Helvetica", Font.PLAIN, 12);
    private float mFontSize = 18;
    private boolean mDisplayText = true;

    /**
     * Set the behavior to do a text wrap content or not
     * In Android Studio, this should not be active
     *
     * @param doWrap
     */
    public static void setDoWrap(boolean doWrap) {
        DO_WRAP = doWrap;
    }

    /**
     * Base constructor
     *
     * @param widget the widget we are decorating
     * @param text   the text content
     */
    public TextWidget(ConstraintWidget widget, String text) {
        super(widget);
        setText(text);
    }

    /**
     * Accessor for the font Size
     *
     * @return text content
     */
    public float getTextSize() {
        return mFontSize;
    }

    /**
     * Setter for the font Size
     *
     * @param fontSize
     */
    public void setTextSize(float fontSize) {
        mFontSize = fontSize;
        // regression derived approximation of Android to Java font size
        int size = androidToSwingFontSize(mFontSize);
        mFont = new Font("Helvetica", Font.PLAIN, size);
        wrapContent();
    }

    public static int androidToSwingFontSize(float fontSize) {
        return  (int) Math.round((fontSize * 1.333f + 4.5f) / 2.41f);
    }
    /**
     * Accessor for the text content
     *
     * @return text content
     */
    public String getText() {
        return mText;
    }

    /**
     * Setter for the text content
     *
     * @param text
     */
    public void setText(String text) {
        mText = text;
        wrapContent();
    }

    /**
     * Apply the size behaviour
     */
    @Override
    public void applyDimensionBehaviour() {
        wrapContent();
    }

    /**
     * Utility method computing the size of the widget if dimensions are set
     * to wrap_content, using the default font
     */
    protected void wrapContent() {
        if (!TextWidget.DO_WRAP) {
            return;
        }
        if (mText == null) {
            return;
        }
        Canvas c = new Canvas();
        c.setFont(mFont);
        FontMetrics fm = c.getFontMetrics(mFont);

        String string = getText();
        if (mToUpperCase) {
            string = string.toUpperCase();
        }
        int tw = fm.stringWidth(string) + 2 * (mHorizontalPadding + mHorizontalMargin);
        int th = fm.getMaxAscent() + 2*fm.getMaxDescent() + 2 * (mVerticalPadding + mVerticalMargin);
        if (tw > mWidget.getMinWidth()) {
            mWidget.setMinWidth(tw);
        }
        if (th > mWidget.getMinHeight()) {
            mWidget.setMinHeight(th);
        }
        if (mWidget.getHorizontalDimensionBehaviour()
                == ConstraintWidget.DimensionBehaviour.WRAP_CONTENT) {
            mWidget.setWidth(tw);
        }
        if (mWidget.getVerticalDimensionBehaviour()
                == ConstraintWidget.DimensionBehaviour.WRAP_CONTENT) {
            mWidget.setHeight(th);
        }
        if (mWidget.getHorizontalDimensionBehaviour() ==
                ConstraintWidget.DimensionBehaviour.FIXED) {
            if (mWidget.getWidth() <= mWidget.getMinWidth()) {
                mWidget.setHorizontalDimensionBehaviour(
                        ConstraintWidget.DimensionBehaviour.WRAP_CONTENT);
            }
        }
        if (mWidget.getVerticalDimensionBehaviour() == ConstraintWidget.DimensionBehaviour.FIXED) {
            if (mWidget.getHeight() <= mWidget.getMinHeight()) {
                mWidget.setVerticalDimensionBehaviour(
                        ConstraintWidget.DimensionBehaviour.WRAP_CONTENT);
            }
        }
        int baseline = fm.getAscent() + fm.getMaxDescent() + mVerticalPadding+mVerticalMargin;
        mWidget.setBaselineDistance(baseline);
    }


    @Override
    public void onPaintBackground(ViewTransform transform, Graphics2D g) {
        super.onPaintBackground(transform, g);
        if (mColorSet.drawBackground() && mDisplayText) {
            drawText(transform, g, mWidget.getDrawX(), mWidget.getDrawY());
        }
    }

    protected void drawText(ViewTransform transform, Graphics2D g, int x, int y) {
        int tx = transform.getSwingX(x);
        int ty = transform.getSwingY(y);
        int h = transform.getSwingDimension(mWidget.getDrawHeight());
        int w = transform.getSwingDimension(mWidget.getDrawWidth());

        int horizontalPadding = transform.getSwingDimension(mHorizontalPadding+mHorizontalMargin);
        int verticalPadding = transform.getSwingDimension(mVerticalPadding+mVerticalMargin);
        int originalSize = mFont.getSize();
        int scaleSize = transform.getSwingDimension(originalSize);
        g.setFont(mFont.deriveFont((float) scaleSize));
        FontMetrics fontMetrics = g.getFontMetrics();

        if (mWidget.getVisibility() == ConstraintWidget.INVISIBLE) {
            Color c = mTextColor.getColor();
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 100));
        } else {
            g.setColor(mTextColor.getColor());
        }
        String string = getText();
        if (mToUpperCase) {
            string = string.toUpperCase();
        }
        int ftx = 0;
        int fty = 0;
        int stringWidth = fontMetrics.stringWidth(string);

        switch (mAlignmentX) {
            case TEXT_ALIGNMENT_VIEW_START: {
                ftx = tx + horizontalPadding;
            } break;
            case TEXT_ALIGNMENT_CENTER: {
                int paddx = (w - stringWidth) / 2;
                ftx = tx + paddx;
            } break;
            case TEXT_ALIGNMENT_VIEW_END: {
                int padd = w - stringWidth + horizontalPadding;
                ftx = tx + padd;
            } break;
        }
        switch (mAlignmentY) {
            case TEXT_ALIGNMENT_VIEW_START: {
                fty = ty + fontMetrics.getAscent() + fontMetrics.getMaxDescent() +
                        verticalPadding;
            } break;
            case TEXT_ALIGNMENT_CENTER: {
                fty = ty + fontMetrics.getAscent() + (h - fontMetrics.getAscent()) / 2;
            } break;
            case TEXT_ALIGNMENT_VIEW_END: {
                fty = ty + h - fontMetrics.getMaxDescent() - verticalPadding;
            } break;
        }
        g.drawString(string, ftx, fty);
    }

    public void setDisplayText(boolean displayText) {
        mDisplayText = displayText;
    }
}
