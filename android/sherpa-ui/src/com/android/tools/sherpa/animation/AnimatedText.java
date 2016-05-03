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
import com.google.tnt.solver.widgets.Animator;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

/**
 * Implements simple fading text animation
 */
public class AnimatedText extends Animation {

    private final String mText;
    private final int mX;
    private final int mY;
    private Color mTextColor = Color.WHITE;
    private static Font sFont = new Font("Helvetica", Font.PLAIN, 12);
    private int mTextWidth;
    private int mTextHeight;

    public AnimatedText(String text, int x, int y) {
        mText = text;
        mX = x;
        mY = y;
        setDuration(1200);
        Canvas c = new Canvas();
        FontMetrics fm = c.getFontMetrics(sFont);
        mTextWidth = fm.stringWidth(mText);
        mTextHeight = fm.getMaxAscent() + fm.getMaxDescent();
    }

    @Override
    public void onPaint(ViewTransform transform, Graphics2D g) {
        int alpha = (int)Animator.EaseInOutinterpolator(getProgress(), 255, 0);
        Color color = new Color(mTextColor.getRed(),
                mTextColor.getGreen(),
                mTextColor.getBlue(), alpha);
        g.setColor(color);
        g.setFont(sFont);
        g.drawString(mText, mX - mTextWidth / 2, mY + mTextHeight);
    }
}
