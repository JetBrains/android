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

import com.android.tools.sherpa.interaction.ConstraintHandle;
import com.google.tnt.solver.widgets.ConstraintAnchor;

import java.awt.Color;

/**
 * Animated circle. The circle will be drawn at (x, y) and pulsate from transparent
 * to white, to transparent, growing in size.
 */
public class AnimatedDestroyCircle extends AnimatedCircle {

    /**
     * Constructor, create a new AnimatedCircle at the given anchor's position
     *
     * @param anchor ConstraintAnchor we animate on
     */
    public AnimatedDestroyCircle(ConstraintHandle anchor) {
        super(anchor);
        setLoop(false);
        setDuration(350);
        mColor = new Color(220, 220, 230);
        mBackgroundColor = null;
    }

    /**
     * Returns the radius of the circle, given the animation's progress. It will grow
     * from 3 to 23, mapped on the progress [0 .. 1]
     *
     * @param progress
     * @return the circle's radius
     */
    @Override
    protected int getRadius(double progress) {
        return (int) (2 + 20 * progress);
    }
}