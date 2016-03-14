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
import com.android.tools.sherpa.interaction.ConstraintHandle;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;

/**
 * Animated circle. The circle will be drawn at (x, y) and pulsate from transparent
 * to white, to transparent.
 */
public class AnimatedCircle extends Animation {

    private ConstraintHandle mAnchor;

    protected int mRadius = 3;
    protected Color mColor = Color.white;
    protected Color mBackgroundColor = new Color(24, 55, 112);

    /**
     * Constructor, create a new AnimatedCircle at the given anchor's position
     *
     * @param anchor ConstraintAnchor we animate on
     */
    public AnimatedCircle(ConstraintHandle anchor) {
        mAnchor = anchor;
        setLoop(true);
    }

    /**
     * Returns the radius of the circle, given the animation's progress.
     *
     * @param progress
     * @return the circle's radius
     */
    protected int getRadius(double progress) {
        return mRadius;
    }

    /**
     * Paint method for the animation. We simply draw an opaque circle at (x, y),
     * applying a transparency as the animation progresses.
     *
     * @param transform view transform
     * @param g Graphics context
     */
    @Override
    public void onPaint(ViewTransform transform, Graphics2D g) {
        int x = transform.getSwingX(mAnchor.getDrawX());
        int y = transform.getSwingY(mAnchor.getDrawY());
        double progress = getProgress();
        int alpha = getPulsatingAlpha(progress);
        int radius = getRadius(progress);
        if (mBackgroundColor != null) {
            g.setColor(mBackgroundColor);
            Ellipse2D.Float circle = new Ellipse2D.Float(x - radius, y - radius,
                    radius * 2, radius * 2);
            g.fill(circle);
        }
        Color highlight = new Color(mColor.getRed(), mColor.getGreen(), mColor.getBlue(), alpha);
        g.setColor(highlight);
        Ellipse2D.Float circle = new Ellipse2D.Float(x - radius, y - radius,
                radius * 2, radius * 2);
        g.fill(circle);
        g.draw(circle);
    }
}
