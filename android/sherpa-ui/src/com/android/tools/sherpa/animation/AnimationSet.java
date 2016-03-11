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

import java.awt.Graphics2D;
import java.util.ArrayList;

/**
 * This class encapsulate a set of animation to play
 */
public class AnimationSet extends Animation {

    private ArrayList<Animation> mAnimations = new ArrayList<Animation>();

    /**
     * Add an animation to the AnimationSet
     *
     * @param animation the animation we want to add
     */
    public void add(Animation animation) {
        mAnimations.add(animation);
    }

    /**
     * Clear the list of animations that we might have
     */
    public void clear() {
        mAnimations.clear();
    }

    /**
     * Play the animations in our set
     *
     * @param transform view transform
     * @param g Graphics context
     */
    @Override
    public void onPaint(ViewTransform transform, Graphics2D g) {
        for (Animation animation : mAnimations) {
            animation.setProgress(getProgress());
            animation.onPaint(transform, g);
        }
    }
}
