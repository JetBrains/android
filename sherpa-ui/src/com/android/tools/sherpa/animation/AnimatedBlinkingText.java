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

package com.android.tools.sherpa.animation;

import com.android.tools.sherpa.drawing.ViewTransform;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

/**
 * Implements a blinking text animation
 */
public class AnimatedBlinkingText extends Animation {

    private final String mText;
    private static Font sFont = new Font("Helvetica", Font.PLAIN, 12);
    int mTextWidth;
    int mTextHeight;
    Color mTextColor = Color.WHITE;
    int mCanvasWidth;
    int mCanvasHeight;

    /**
     * Default constructor
     *
     * @param text the text we will display
     */
    public AnimatedBlinkingText(String text) {
        mText = text;
        setLoop(true);
        setDuration(2000);

        Canvas c = new Canvas();
        FontMetrics fm = c.getFontMetrics(sFont);
        int padding = 8;
        mTextWidth = fm.stringWidth(mText) + 2 * padding;
        mTextHeight = fm.getMaxAscent() + fm.getMaxDescent() + 2 * padding;
    }

    /**
     * Set the canvas dimension we are going to paint on
     *
     * @param width width of the canvas
     * @param height height of the canvas
     */
    public void setCanvasDimension(int width, int height) {
        mCanvasWidth = width;
        mCanvasHeight = height;
    }

    /**
     * Paint method for the animation
     *
     * @param transform view transform
     * @param g Graphics context
     */
    @Override
    public void onPaint(ViewTransform transform, Graphics2D g) {
        int alpha = getPulsatingAlpha(getProgress());
        Color color = new Color(mTextColor.getRed(),
                mTextColor.getGreen(),
                mTextColor.getBlue(), alpha);
        g.setColor(color);
        g.drawString(mText, mCanvasWidth - mTextWidth, mTextHeight);
    }
}
