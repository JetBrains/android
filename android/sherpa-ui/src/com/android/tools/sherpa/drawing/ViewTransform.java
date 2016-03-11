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

package com.android.tools.sherpa.drawing;

/**
 * Simple class encapsulating a basic transform (scale + translate).
 * Used to draw from one coordinate system (android) to another (swing)
 */
public class ViewTransform {
    int dx;
    int dy;
    float scale;

    /**
     * Return the corresponding swing coordinate in X given a X android coordinate
     * @param androidX the android coordinate
     * @return the swing coordinate
     */
    public int getSwingX(int androidX) {
        return (int) (dx + androidX * scale);
    }

    /**
     * Return the corresponding swing coordinate in Y given a Y android coordinate
     * @param androidY the android coordinate
     * @return the swing coordinate
     */
    public int getSwingY(int androidY) {
        return (int) (dy + androidY * scale);
    }

    /**
     * Return the corresponding swing dimension given an android dimension
     * @param androidDimension the android dimension
     * @return the swing dimension
     */
    public int getSwingDimension(int androidDimension) {
        return (int) (androidDimension * scale);
    }

    /**
     * Return the corresponding android X coordinate given a X swing coordinate
     * @param swingX the swing coordinate
     * @return the android coordinate
     */
    public int getAndroidX(int swingX) {
        return (int) ((swingX - dx) / scale);
    }

    /**
     * Return the corresponding android Y coordinate given a Y swing coordinate
     * @param swingY the swing coordinate
     * @return the android coordinate
     */
    public int getAndroidY(int swingY) {
        return (int) ((swingY - dy) / scale);
    }

    /**
     * Setter for the view translation
     * @param x translation in x
     * @param y translation in y
     */
    public void setTranslate(int x, int y) { dx = x; dy = y; }

    /**
     * Setter for the view scale
     * @param scale
     */
    public void setScale(float scale) { this.scale = scale; }

    /**
     * Accessor for scale
     * @return scale factor
     */
    public float getScale() {
        return scale;
    }

    /**
     * Accessor for the current translate x
     * @return translate x
     */
    public int getTranslateX() {
        return dx;
    }

    /**
     * Accessor for the current translate y
     * @return translate y
     */
    public int getTranslateY() {
        return dy;
    }
}
