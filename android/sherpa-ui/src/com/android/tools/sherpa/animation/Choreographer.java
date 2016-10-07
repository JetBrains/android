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
 * Basic Choreographer for animations
 */
public class Choreographer {
    ArrayList<Animation> mAnimations = new ArrayList<Animation>();

    /**
     * Add an animation to the choreographer and starts it immediately
     *
     * @param animation the animation we want to add
     */
    public void addAnimation(Animation animation) {
        if (mAnimations.contains(animation)) {
            return;
        }
        mAnimations.add(animation);
        animation.start();
    }

    /**
     * Remove the given animation from the list of running animations
     *
     * @param animation the animation to remove
     */
    public void removeAnimation(Animation animation) {
        mAnimations.remove(animation);
    }

    /**
     * Play the animations we have, discard the old ones. Return true if animations still
     * need to be executed (to signal the caller that we need another repaint).
     *
     * @param transform view transform
     * @param g Graphics context
     * @return true if we still have running animations
     */
    public boolean onPaint(ViewTransform transform, Graphics2D g) {
        ArrayList<Animation> finished = new ArrayList<Animation>();
        for (Animation animation : mAnimations) {
            if (!animation.step()) {
                finished.add(animation);
            }
            animation.onPaint(transform, g);
        }
        boolean needsRepaint = mAnimations.size() > 0;
        for (Animation animation : finished) {
            mAnimations.remove(animation);
        }
        return needsRepaint;
    }
}
