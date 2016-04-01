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
    protected int mHorizontalPadding = 0;
    protected int mVerticalPadding = 0;
    protected int mVerticalMargin = 0;
    protected int mHorizontalMargin = 0;
    protected boolean mToUpperCase = false;
    private String mText;
    protected Font mFont = new Font("Helvetica", Font.PLAIN, 12);
    private float mFontSize = 18;

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
        int size = (int) Math.round((mFontSize * 1.333f + 4.5f) / 2.41f);
        System.out.println("size = " + size);
        mFont = new Font("Helvetica", Font.PLAIN, size);

        wrapContent();
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
        mWidget.setMinWidth(tw);
        mWidget.setMinHeight(th);
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
        if (WidgetDecorator.isShowFakeUI()) {
            drawText(transform, g, mWidget.getDrawX(), mWidget.getDrawY());
        }
    }

    protected void drawText(ViewTransform transform, Graphics2D g, int x, int y) {
        int tx = transform.getSwingX(x);
        int ty = transform.getSwingY(y);
        int h = transform.getSwingDimension(mWidget.getDrawHeight());

        int horizontalPadding = transform.getSwingDimension(mHorizontalPadding+mHorizontalMargin);
        int verticalPadding = transform.getSwingDimension(mVerticalPadding+mVerticalMargin);
        int originalSize = mFont.getSize();
        int scaleSize = transform.getSwingDimension(originalSize);
        g.setFont(mFont.deriveFont((float) scaleSize));
        FontMetrics fontMetrics = g.getFontMetrics();
        if (mWidget.getVisibility() == ConstraintWidget.INVISIBLE) {
            g.setColor(new Color(255, 255, 255, 100));
        } else {
            g.setColor(Color.WHITE);
        }
        String string = getText();
        if (mToUpperCase) {
            string = string.toUpperCase();
        }
        g.drawString(string, tx + horizontalPadding,
                ty + fontMetrics.getAscent() + fontMetrics.getMaxDescent() + verticalPadding);
    }
}
