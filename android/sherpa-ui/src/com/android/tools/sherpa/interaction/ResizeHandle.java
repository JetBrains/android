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

package com.android.tools.sherpa.interaction;

import com.android.tools.sherpa.drawing.ViewTransform;
import com.google.tnt.solver.widgets.ConstraintWidget;

import java.awt.Rectangle;

/**
 * Represents a resize handle on the widget. Set its bounds given its owner's bounds and its type.
 */
public class ResizeHandle {

    private static final int SLOPE = 12;

    /**
     * Available type of ResizeHandle
     */
    public enum Type { LEFT_TOP, LEFT_BOTTOM, RIGHT_TOP, RIGHT_BOTTOM,
        LEFT_SIDE, RIGHT_SIDE, TOP_SIDE, BOTTOM_SIDE }

    private final WidgetInteractionTargets mOwner;
    private final Type mType;
    private Rectangle mBounds = new Rectangle();

    /**
     * Default constructor
     *
     * @param owner the owner of this ResizeHandle
     * @param type the type of ResizeHandle
     */
    public ResizeHandle(WidgetInteractionTargets owner, Type type) {
        mOwner = owner;
        mType = type;
    }

    /**
     * Return the owner of this ResizeHandle (the widget it belongs to)
     *
     * @return the ResizeHandle's owner
     */
    public ConstraintWidget getOwner() {
        return mOwner.getConstraintWidget();
    }

    /**
     * Return the type of ResizeHandle
     *
     * @return type of ResizeHandle
     */
    public Type getType() {
        return mType;
    }

    /**
     * Return the computed bounds for this ResizeHandle
     *
     * @return the bounds of the handle
     */
    public Rectangle getBounds() { return mBounds; }

    /**
     * Update the bounds of the ResizeHandle according to its type and the bounds
     * of the widget it belongs to.
     */
    public void updatePosition() {
        if (mOwner == null) {
            mBounds.setBounds(0, 0, 0, 0);
            return;
        }
        int x = getOwner().getDrawX();
        int y = getOwner().getDrawY();
        int w = getOwner().getDrawWidth();
        int h = getOwner().getDrawHeight();
        switch (mType) {
            case LEFT_TOP: {
                mBounds.setBounds(x - SLOPE/2, y - SLOPE/2, SLOPE, SLOPE);
            } break;
            case LEFT_BOTTOM: {
                mBounds.setBounds(x - SLOPE/2, y + h - SLOPE/2, SLOPE, SLOPE);
            } break;
            case RIGHT_TOP: {
                mBounds.setBounds(x + w - SLOPE/2, y - SLOPE/2, SLOPE, SLOPE);
            } break;
            case RIGHT_BOTTOM: {
                mBounds.setBounds(x + w - SLOPE/2, y + h - SLOPE/2, SLOPE, SLOPE);
            } break;
            case LEFT_SIDE: {
                mBounds.setBounds(x - SLOPE/2, y + SLOPE/2, SLOPE, h - SLOPE);
            } break;
            case RIGHT_SIDE: {
                mBounds.setBounds(x + w - SLOPE/2, y + SLOPE/2, SLOPE, h - SLOPE);
            } break;
            case TOP_SIDE: {
                mBounds.setBounds(x + SLOPE/2, y - SLOPE/2, w - SLOPE, SLOPE);
            } break;
            case BOTTOM_SIDE: {
                mBounds.setBounds(x + SLOPE/2, y + h - SLOPE/2, w - SLOPE, SLOPE);
            } break;
        }
    }

    /**
     * Return the bounds transformed
     *
     * @param transform the current view transform
     *
     * @return a new rectangle with the transformed bounds
     */
    public Rectangle getSwingBounds(ViewTransform transform) {
        Rectangle bounds = new Rectangle();
        if (mOwner == null) {
            bounds.setBounds(0, 0, 0, 0);
            return bounds;
        }
        int x = transform.getSwingX(getOwner().getDrawX());
        int y = transform.getSwingY(getOwner().getDrawY());
        int w = transform.getSwingDimension(getOwner().getDrawWidth());
        int h = transform.getSwingDimension(getOwner().getDrawHeight());
        switch (mType) {
            case LEFT_TOP: {
                bounds.setBounds(x - SLOPE/2, y - SLOPE/2, SLOPE, SLOPE);
            } break;
            case LEFT_BOTTOM: {
                bounds.setBounds(x - SLOPE/2, y + h - SLOPE/2, SLOPE, SLOPE);
            } break;
            case RIGHT_TOP: {
                bounds.setBounds(x + w - SLOPE/2, y - SLOPE/2, SLOPE, SLOPE);
            } break;
            case RIGHT_BOTTOM: {
                bounds.setBounds(x + w - SLOPE/2, y + h - SLOPE/2, SLOPE, SLOPE);
            } break;
            case LEFT_SIDE: {
                bounds.setBounds(x - SLOPE/2, y + SLOPE/2, SLOPE, h - SLOPE);
            } break;
            case RIGHT_SIDE: {
                bounds.setBounds(x + w - SLOPE/2, y + SLOPE/2, SLOPE, h - SLOPE);
            } break;
            case TOP_SIDE: {
                bounds.setBounds(x + SLOPE/2, y - SLOPE/2, w - SLOPE, SLOPE);
            } break;
            case BOTTOM_SIDE: {
                bounds.setBounds(x + SLOPE/2, y + h - SLOPE/2, w - SLOPE, SLOPE);
            } break;
        }
        return bounds;
    }


    /**
     * Return true if the ResizeHandle is a side handle (left/right/top/bottom)
     *
     * @return true if side handle
     */
    public boolean isSideHandle() {
        switch (mType) {
            case LEFT_SIDE:
            case RIGHT_SIDE:
            case TOP_SIDE:
            case BOTTOM_SIDE:
                return true;
        }
        return false;
    }

    /**
     * Return true if the point (x, y) intersects with the ResizeHandle
     *
     * @param x x coordinate
     * @param y y coordinate
     * @return true if we hit the handle
     */
    public boolean hit(int x, int y) {
        return mBounds.contains(x, y);
    }
}
