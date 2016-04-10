/*
 * Copyright (C) 2015 The Android Open Source Project
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
    float dx;
    float dy;
    float scale;

    /**
     * Return the corresponding swing coordinate in X given a X android coordinate
     *
     * @param androidX the android coordinate
     * @return the swing coordinate
     */
    public int getSwingX(int androidX) {
        return (int) (dx + androidX * scale);
    }

    /**
     * Return the corresponding swing coordinate in X given a X android coordinate
     *
     * @param androidX the android coordinate in float
     * @return the swing coordinate
     */
    public int getSwingFX(float androidX) {
        return (int) (dx + androidX * scale);
    }

    /**
     * Return the corresponding swing coordinate in Y given a Y android coordinate
     *
     * @param androidY the android coordinate
     * @return the swing coordinate
     */
    public int getSwingY(int androidY) {
        return (int) (dy + androidY * scale);
    }

    /**
     * Return the corresponding swing coordinate in Y given a Y android coordinate
     *
     * @param androidY the android coordinate in float
     * @return the swing coordinate
     */
    public int getSwingFY(float androidY) {
        return (int) (dy + androidY * scale);
    }

    /**
     * Return the corresponding swing dimension given an android dimension
     *
     * @param androidDimension the android dimension
     * @return the swing dimension
     */
    public int getSwingDimension(int androidDimension) {
        return (int) (androidDimension * scale);
    }

    /**
     * Return the corresponding swing dimension in float, given an android dimension
     *
     * @param androidDimension the android dimension (in float)
     * @return the swing dimension (in float)
     */
    public float getSwingDimensionF(float androidDimension) {
        return (androidDimension * scale);
    }

    /**
     * Get the android dimension given a swing dimension value
     *
     * @param swingDimension the swing dimension
     * @return the android dimension
     */
    public int getAndroidDimension(int swingDimension) {
        return (int) (swingDimension / scale);
    }

    /**
     * Get the android dimension given a swing dimension value (in float)
     *
     * @param swingDimension the swing dimension (in float)
     * @return the android dimension (in float)
     */
    public float getAndroidDimensionF(float swingDimension) {
        return (swingDimension / scale);
    }

    /**
     * Return the corresponding android X coordinate given a X swing coordinate
     *
     * @param swingX the swing coordinate
     * @return the android coordinate
     */
    public int getAndroidX(int swingX) {
        return (int) ((swingX - dx) / scale);
    }

    /**
     * Return the corresponding android X coordinate given a X swing coordinate in float
     *
     * @param swingX the swing coordinate
     * @return the android coordinate
     */
    public float getAndroidFX(int swingX) {
        return ((swingX - dx) / scale);
    }

    /**
     * Return the corresponding android Y coordinate given a Y swing coordinate
     *
     * @param swingY the swing coordinate
     * @return the android coordinate
     */
    public int getAndroidY(int swingY) {
        return (int) ((swingY - dy) / scale);
    }

    /**
     * Return the corresponding android Y coordinate given a Y swing coordinate in float
     *
     * @param swingY the swing coordinate
     * @return the android coordinate
     */
    public float getAndroidFY(int swingY) {
        return ((swingY - dy) / scale);
    }

    /**
     * Setter for the view translation
     *
     * @param x translation in x
     * @param y translation in y
     */
    public void setTranslate(float x, float y) {
        dx = x;
        dy = y;
    }

    /**
     * Setter for the view scale
     *
     * @param scale
     */
    public void setScale(float scale) {
        this.scale = scale;
    }

    /**
     * Accessor for scale
     *
     * @return scale factor
     */
    public float getScale() {
        return scale;
    }

    /**
     * Accessor for the current translate x
     *
     * @return translate x
     */
    public float getTranslateX() {
        return dx;
    }

    /**
     * Accessor for the current translate y
     *
     * @return translate y
     */
    public float getTranslateY() {
        return dy;
    }

    /**
     * Set the view transform given another view transform
     *
     * @param transform
     */
    public void set(ViewTransform transform) {
        scale = transform.scale;
        dx = transform.dx;
        dy = transform.dy;
    }

    /**
     * Compares this transform with another one
     *
     * @param transform the transform to compare ourselves to
     * @return true if the transforms are equal
     */
    public boolean equalsTransform(ViewTransform transform) {
        if (dx == transform.dx && dy == transform.dy && scale == transform.scale) {
            return true;
        }
        return false;
    }
}
