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

package com.android.tools.sherpa.interaction;

import com.android.tools.sherpa.drawing.ViewTransform;
import android.constraint.solver.widgets.ConstraintWidget;

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
     * @param viewTransform the view transform
     */
    public void updatePosition(ViewTransform viewTransform) {
        if (mOwner == null) {
            mBounds.setBounds(0, 0, 0, 0);
            return;
        }
        int x = getOwner().getDrawX();
        int y = getOwner().getDrawY();
        int w = getOwner().getDrawWidth();
        int h = getOwner().getDrawHeight();
        int slope = (int) (1 + SLOPE / viewTransform.getScale());
        switch (mType) {
            case LEFT_TOP: {
                mBounds.setBounds(x - slope/2, y - slope/2, slope, slope);
            } break;
            case LEFT_BOTTOM: {
                mBounds.setBounds(x - slope/2, y + h - slope/2, slope, slope);
            } break;
            case RIGHT_TOP: {
                mBounds.setBounds(x + w - slope/2, y - slope/2, slope, slope);
            } break;
            case RIGHT_BOTTOM: {
                mBounds.setBounds(x + w - slope/2, y + h - slope/2, slope, slope);
            } break;
            case LEFT_SIDE: {
                mBounds.setBounds(x - slope/2, y + slope/2, slope, h - slope);
            } break;
            case RIGHT_SIDE: {
                mBounds.setBounds(x + w - slope/2, y + slope/2, slope, h - slope);
            } break;
            case TOP_SIDE: {
                mBounds.setBounds(x + slope/2, y - slope/2, w - slope, slope);
            } break;
            case BOTTOM_SIDE: {
                mBounds.setBounds(x + slope/2, y + h - slope/2, w - slope, slope);
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
        bounds.setBounds(transform.getSwingX(mBounds.x),
                transform.getSwingY(mBounds.y),
                transform.getSwingDimension(mBounds.width),
                transform.getSwingDimension(mBounds.height));
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
    public boolean hit(float x, float y) {
        return mBounds.contains(x, y);
    }
}
