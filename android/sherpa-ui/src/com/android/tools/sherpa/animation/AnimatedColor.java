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
        int r = Animator.EaseInOutinterpolator(progress, mBeginColor.getRed(), mEndColor.getRed());
        int g = Animator.EaseInOutinterpolator(progress, mBeginColor.getGreen(),
                mEndColor.getGreen());
        int b = Animator.EaseInOutinterpolator(progress, mBeginColor.getBlue(),
                mEndColor.getBlue());
        int a = Animator.EaseInOutinterpolator(progress, mBeginColor.getAlpha(),
                mEndColor.getAlpha());
        return new Color(r, g, b, a);
    }
}
