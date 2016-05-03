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

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Simple class animating between two colors
 */
public class AnimatedColor extends Animation {
    private final Color mBeginColor;
    private final Color mEndColor;

    /**
     * Base constructor
     *
     * @param begin the color we begin with
     * @param end   the color we end with
     */
    public AnimatedColor(Color begin, Color end) {
        mBeginColor = begin;
        mEndColor = end;
    }

    @Override
    public void onPaint(ViewTransform transform, Graphics2D g) {
        // do nothing
    }

    /**
     * Returns the current color (interpolated between begin/end colors)
     *
     * @return the current color
     */
    public Color getColor() {
        double progress = getProgress();

        double bR = mBeginColor.getRed() / 255.0;
        double bG = mBeginColor.getGreen() / 255.0;
        double bB = mBeginColor.getBlue() / 255.0;
        double bA = mBeginColor.getAlpha() / 255.0;

        double eR = mEndColor.getRed() / 255.0;
        double eG = mEndColor.getGreen() / 255.0;
        double eB = mEndColor.getBlue() / 255.0;
        double eA = mEndColor.getAlpha() / 255.0;

        bR = Math.pow(bR, 2.2);
        bG = Math.pow(bG, 2.2);
        bB = Math.pow(bB, 2.2);

        eR = Math.pow(eR, 2.2);
        eG = Math.pow(eG, 2.2);
        eB = Math.pow(eB, 2.2);

        double r = Animator.EaseInOutinterpolator(progress, bR, eR);
        double g = Animator.EaseInOutinterpolator(progress, bG, eG);
        double b = Animator.EaseInOutinterpolator(progress, bB, eB);
        double a = Animator.EaseInOutinterpolator(progress, bA, eA);

        r = Math.pow(r, 1.0 / 2.2);
        g = Math.pow(g, 1.0 / 2.2);
        b = Math.pow(b, 1.0 / 2.2);

        return new Color((int) (r * 255.0), (int) (g * 255.0), (int) (b * 255.0), (int) (a * 255.0));
    }
}
