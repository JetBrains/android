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
import android.constraint.solver.widgets.ConstraintWidget;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

public class AnimatedLine extends Animation {

    private ConstraintHandle mConstraintHandle;

    protected Color mColor = Color.white;
    private Rectangle mBounds = new Rectangle();

    /**
     * Constructor, create a new AnimatedLine at the given anchor's position
     *
     * @param anchor ConstraintHandle we animate on
     */
    public AnimatedLine(ConstraintHandle anchor) {
        mConstraintHandle = anchor;
        ConstraintWidget widget = mConstraintHandle.getOwner();
        int l = widget.getDrawX();
        int t = widget.getDrawY();
        int w = widget.getDrawWidth();
        int h = widget.getDrawHeight();
        switch (mConstraintHandle.getAnchor().getType()) {
            case LEFT: {
                mBounds.setBounds(l, t, 0, h);
            }
            break;
            case RIGHT: {
                mBounds.setBounds(l + w, t, 0, h);
            }
            break;
            case TOP: {
                mBounds.setBounds(l, t, w, 0);
            }
            break;
            case BOTTOM: {
                mBounds.setBounds(l, t + h, w, 0);
            }
            break;
            case BASELINE: {
                mBounds.setBounds(l, t + widget.getBaselineDistance(), w, 0);
            }
            break;
        }
        setLoop(true);
    }

    /**
     * Paint method for the animation. We simply draw an opaque circle at (x, y),
     * applying a transparency as the animation progresses.
     *
     * @param transform view transform
     * @param g         Graphics context
     */
    @Override
    public void onPaint(ViewTransform transform, Graphics2D g) {
        int x = transform.getSwingX(mBounds.x);
        int y = transform.getSwingY(mBounds.y);
        int w = transform.getSwingDimension(mBounds.width);
        int h = transform.getSwingDimension(mBounds.height);
        double progress = getProgress();
        int alpha = getPulsatingAlpha(progress);
        Color highlight = new Color(mColor.getRed(), mColor.getGreen(), mColor.getBlue(), alpha);
        g.setColor(highlight);
        int extra = getExtra(progress);
        g.fillRect(x - extra, y - extra, w + 2*extra + 1, h + 2*extra + 1);
    }

    protected int getExtra(double progress) {
        return 0;
    }
}
