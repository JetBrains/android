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

import com.android.tools.sherpa.animation.AnimatedColor;

import java.awt.Color;

/**
 * This class adds support for color transitions
 */
public class ColorTheme {
    private static final int sAnimationDuration = 250; // ms

    /**
     * The type of looks we support
     */
    public enum Look { SUBDUED, NORMAL, HIGHLIGHTED, SELECTED }

    private final Color mNormalColor;
    private final Color mSelectedColor;
    private final Color mHighlightedColor;
    private final Color mSubduedColor;

    private Look mCurrentLook = Look.NORMAL;

    private AnimatedColor mAnimation;

    /**
     * Utility function returning a new color with an updated brightness
     *
     * @param color the source color
     * @param factor the brightness factor
     * @return a new color with updated brightness
     */
    public static Color updateBrightness(Color color, float factor) {
        float[] hsb = new float[3];
        Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), hsb);
        return new Color(Color.HSBtoRGB(hsb[0], hsb[1], Math.min(1.0f, hsb[2] * factor)));
    }

    /**
     * Utility function returning a new color faded to a target color
     *
     * @param source the source color
     * @param target the target color
     * @param ratio the ratio (0..1f) of the target color we want
     * @return a new color mixed between source and target
     */
    public static Color fadeToColor(Color source, Color target, float ratio) {
        int r = (int) ((1 - ratio) * source.getRed() + ratio * target.getRed());
        int g = (int) ((1 - ratio) * source.getGreen() + ratio * target.getGreen());
        int b = (int) ((1 - ratio) * source.getBlue() + ratio * target.getBlue());
        int a = (int) ((1 - ratio) * source.getAlpha() + ratio * target.getAlpha());
        return new Color (r, g, b, a);
    }

    /**
     * Base constructor
     * @param subdued the color used for the subdued look
     * @param normal the color used for the normal look
     * @param highlighted the color used for the highlighted look
     * @param selected the color used for the selected look
     */
    public ColorTheme(Color subdued,
            Color normal,
            Color highlighted,
            Color selected) {
        mSubduedColor = subdued;
        mNormalColor = normal;
        mHighlightedColor = highlighted;
        mSelectedColor = selected;
        mAnimation = new AnimatedColor(new Color(0, 0, 0, 0), mNormalColor);
        mAnimation.setDuration(sAnimationDuration);
        mAnimation.start();
    }

    /**
     * Return the current look used within the theme
     * @return the current look
     */
    public Look getLook() {
        return mCurrentLook;
    }

    /**
     * Set the current look. Animate the color transition
     * @param look the new look
     */
    public void setLook(Look look) {
        if (mCurrentLook != look) {
            Color currentColor = getColor();
            Color targetColor = mNormalColor;
            switch (look) {
                case SUBDUED: {
                    targetColor = mSubduedColor;
                } break;
                case NORMAL: {
                    targetColor = mNormalColor;
                } break;
                case HIGHLIGHTED: {
                    targetColor = mHighlightedColor;
                } break;
                case SELECTED: {
                    targetColor = mSelectedColor;
                } break;
            }
            mAnimation = new AnimatedColor(currentColor, targetColor);
            mAnimation.setDuration(sAnimationDuration);
            mAnimation.start();
            mCurrentLook = look;
        }
    }

    /**
     * Return true if we are animating a color transition
     * @return true if animating
     */
    public boolean isAnimating() {
        return mAnimation.step();
    }

    /**
     * Return the current color
     * @return the current color
     */
    public Color getColor() {
        mAnimation.step();
        return mAnimation.getColor();
    }
}
