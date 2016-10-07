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
import android.support.constraint.solver.widgets.Animator;

import java.awt.Graphics2D;

/**
 * Base class for animations
 */
public abstract class Animation {

    private int mDuration = 400;
    private long mStart = 0;
    private long mDelay = 0;
    private double mProgress = 0;
    private boolean mLoop = false;

    /**
     * Setter for the duration
     *
     * @param duration the animation's duration in ms
     */
    public void setDuration(int duration) {
        mDuration = duration;
    }

    /**
     * Setter for the delay
     *
     * @param delay the animation's delay before starting
     */
    public void setDelay(int delay) { mDelay = delay; }

    /**
     * Setter for looping the animation
     *
     * @param loop if true, the animation will loop and not end
     */
    public void setLoop(boolean loop) { mLoop = loop; }

    /**
     * Setter for the progress. Used e.g. in AnimationSet
     *
     * @param progress
     */
    public void setProgress(double progress) {
        mProgress = progress;
    }

    /**
     * Accessor for progress. The progress of the animation is defined to be within
     * the [0 .. 1] range.
     *
     * @return the current progress value for this animation
     */
    public double getProgress() { return mProgress; }

    /**
     * Start the animation
     */
    public void start() {
        mStart = System.currentTimeMillis() + mDelay;
    }

    /**
     * Reset the animation
     */
    public void reset() {
        mStart = 0;
    }

    /**
     * Step in the animation. Will compute the current progress as well.
     *
     * @return true if the animation is still in progress, false otherwise.
     */
    public boolean step() {
        long current = System.currentTimeMillis();
        if (mStart == 0 || (current - mStart < 0)) {
            mProgress = 0;
        } else if (current - mStart > mDuration) {
            if (mLoop) {
                mProgress = 0;
                start();
                return true;
            }
            mProgress = 1;
            return false;
        } else {
            mProgress = (current - mStart) / (double) mDuration;
        }
        return true;
    }

    /**
     * Utility function, returns the alpha channel given the animation's progress.
     * As the progress is given in the [0 .. 1] range, we need to map the alpha to the
     * progress such that [0 .. 0.5] -> [0->255], and [0.5 .. 1] -> [255->0]
     *
     * @param progress
     * @return
     */
    public int getPulsatingAlpha(double progress) {
        progress *= 2;
        int start = 0;
        int end = 255;
        if (progress > 1) {
            start = 255;
            end = 0;
            progress -= 1;
        }
        return (int)Animator.EaseInOutinterpolator(progress, start, end);
    }

    /**
     * Abstract function that subclasses have to implement to draw.
     *
     * @param transform view transform
     * @param g Graphics context
     */
    abstract public void onPaint(ViewTransform transform, Graphics2D g);
}
