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

package com.android.tools.sherpa.drawing.decorator;

import android.support.constraint.solver.widgets.ConstraintWidget;
import android.support.constraint.solver.widgets.ConstraintWidgetContainer;
import com.android.tools.sherpa.drawing.ColorSet;
import com.android.tools.sherpa.drawing.ViewTransform;

import javax.swing.*;
import java.awt.*;

/**
 * Base class for painting a widget in blueprint mode
 */
public class WidgetDecorator {

    public static final int BLUEPRINT_STYLE = 0;
    public static final int ANDROID_STYLE = 1;

    public static Image sLockImageIcon = null;
    public static Image sUnlockImageIcon = null;
    public static Image sDeleteConnectionsImageIcon = null;
    public static Image sPackChainImageIcon = null;

    private static final int ACTIONS_HIDE_TIMEOUT = 1000; // ms

    private boolean mIsVisible = true;
    private boolean mIsSelected = false;
    protected ColorSet mColorSet;

    private AnimationProgress mShowBaseline = new AnimationProgress();
    private AnimationProgress mShowBias = new AnimationProgress();

    ColorTheme mBackgroundColor;
    ColorTheme mFrameColor;
    ColorTheme mTextColor;
    ColorTheme mConstraintsColor;

    ColorTheme.Look mLook;

    protected final ConstraintWidget mWidget;
    private int mStyle;

    private final Timer mHideActions = new Timer(ACTIONS_HIDE_TIMEOUT, e -> {
        repaint();
    });

    /**
     * Utility class encapsulating a simple animation timer
     */
    class AnimationProgress {
        long mStart = 0;
        long mDelay = 1000;
        long mDuration = 300;

        public void setDelay(long delay) {
            mDelay = delay;
        }

        public void setDuration(long duration) {
            mDuration = duration;
        }

        public void start() {
            mStart = System.currentTimeMillis() + mDelay;
        }

        public float getProgress() {
            if (mStart == 0) {
                return 0;
            }
            long current = System.currentTimeMillis();
            long delta = current - mStart;
            if (delta < 0) {
                return 0;
            }
            if (delta > mDuration) {
                return 1;
            }
            return (current - mStart) / (float) mDuration;
        }

        public boolean isDone() {
            if (mStart == 0) {
                return false;
            }
            long current = System.currentTimeMillis();
            long delta = current - mStart;
            if (delta > mDuration) {
                return true;
            }
            return false;
        }

        public void reset() {
            mStart = 0;
        }

        public boolean isRunning() {
            return mStart != 0 && !isDone();
        }
    }

    /**
     * Base constructor
     *
     * @param widget the widget we are decorating
     */
    public WidgetDecorator(ConstraintWidget widget) {
        mWidget = widget;
        mShowBias.setDelay(0);
        mShowBias.setDuration(1000);
        mShowBaseline.setDelay(1000);
        mShowBaseline.setDuration(1000);
        mHideActions.setRepeats(false);
    }

    /**
     * Call repaint() on the repaintable object
     */
    public void repaint() {
    }

    /**
     * Call repaint() on the repaintable object
     */
    public void repaint(int x, int y, int w, int h) {
    }

    /**
     * Set the current color set, and create the local color themes from it
     *
     * @param colorSet the new color set
     */
    public void setColorSet(ColorSet colorSet) {
        if (mColorSet == colorSet) {
            return;
        }
        mColorSet = colorSet;
        if (mColorSet == null) {
            return;
        }
        // Setup the colors we use

        // ColorTheme:
        // subdued
        // normal
        // highlighted
        // selected
        mBackgroundColor = new ColorTheme(
                mColorSet.getSubduedBackground(),
                mColorSet.getBackground(),
                mColorSet.getHighlightedBackground(),
                mColorSet.getSelectedBackground());

        mFrameColor = new ColorTheme(
                mColorSet.getSubduedFrames(),
                mColorSet.getFrames(),
                mColorSet.getHighlightedFrames(),
                mColorSet.getSelectedFrames());

        mTextColor = new ColorTheme(
                mColorSet.getSubduedText(),
                mColorSet.getText(),
                mColorSet.getText(),
                mColorSet.getSelectedText());

        mConstraintsColor = new ColorTheme(
                mColorSet.getSubduedConstraints(),
                mColorSet.getConstraints(),
                mColorSet.getHighlightedConstraints(),
                mColorSet.getSelectedConstraints());
    }

    /**
     * Setter for the visibility of the widget
     *
     * @param isVisible if true, display the widget
     */
    public void setIsVisible(boolean isVisible) {
        mIsVisible = isVisible;
    }

    /**
     * Getter for the visibility of the widget
     *
     * @return the current visibility status, true if visible
     */
    public boolean isVisible() {
        return mIsVisible;
    }

    /**
     * Accessor for the isSelected flag
     *
     * @return true if the decorator/widget is currently selected, false otherwise
     */
    public boolean isSelected() {
        return mIsSelected;
    }

    /**
     * Set the current look for this decorator
     *
     * @param look the look to use (normal, subdued, selected, etc.)
     */
    public void setLook(ColorTheme.Look look) {
        mLook = look;
    }

    /**
     * Accessor returning the current look
     *
     * @return the current look for this decorator
     */
    public ColorTheme.Look getLook() {
        return mLook;
    }

    /**
     * Return the current background color
     *
     * @return current background color
     */
    public Color getBackgroundColor() {
        return mBackgroundColor.getColor();
    }

    /**
     * Paint the background of the widget
     *
     * @param transform the view transform
     * @param g         the graphics context
     */
    public void onPaintBackground(ViewTransform transform, Graphics2D g) {
        if (mColorSet == null) {
            return;
        }
        if (!mColorSet.drawBackground()) {
            return;
        }
        if (mWidget.isRoot() || mWidget.isRootContainer()) {
            return;
        }
        if (!(mWidget instanceof ConstraintWidgetContainer)
                && mWidget.getVisibility() == ConstraintWidget.VISIBLE) {
            int l = transform.getSwingX(mWidget.getDrawX());
            int t = transform.getSwingY(mWidget.getDrawY());
            int w = transform.getSwingDimension(mWidget.getDrawWidth());
            int h = transform.getSwingDimension(mWidget.getDrawHeight());
            g.setColor(mBackgroundColor.getColor());
            if (mBackgroundColor.getLook() != ColorTheme.Look.NORMAL) {
                g.fillRect(l, t, w, h);
            }

            Color bg = new Color(0, 0, 0, 0);
            Color fg = ColorTheme.updateBrightness(mBackgroundColor.getColor(), 1.6f);
            Graphics2D gfill = (Graphics2D) g.create();
            gfill.setPaint(new LinearGradientPaint(l, t, (l + 2), (t + 2),
                    new float[] { 0, .1f, .1001f }, new Color[] { fg, fg, bg },
                    MultipleGradientPaint.CycleMethod.REFLECT)
            );
            gfill.fillRect(l, t, w, h);
            gfill.dispose();
        }
    }

    public int getStyle() {
        return mStyle;
    }

    public void setStyle(int style) {
        mStyle = style;
    }
}
