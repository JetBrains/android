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
import com.google.tnt.solver.widgets.ConstraintAnchor;
import com.google.tnt.solver.widgets.ConstraintWidget;
import com.google.tnt.solver.widgets.Guideline;

/**
 * Represents a constraint handle on the widget.
 * Set its bounds given its owner's bounds and its type.
 */
public class ConstraintHandle {

    private final WidgetInteractionTargets mOwner;
    private final ConstraintAnchor.Type mType;
    private ConstraintAnchor mAnchor;

    private int mX;
    private int mY;
    private int[] mCurve = new int[8];
    private boolean mHasCurve = false;

    /**
     * Default constructor
     *
     * @param owner the owner of this ConstraintHandle
     * @param type the type of ConstraintHandle
     */
    public ConstraintHandle(
            WidgetInteractionTargets owner,
            ConstraintAnchor.Type type) {
        mOwner = owner;
        mType = type;
    }

    /**
     * Return the owner of this ConstraintHandle (the widget it belongs to)
     *
     * @return the ConstraintHandle's owner
     */
    public ConstraintWidget getOwner() {
        return mOwner.getConstraintWidget();
    }

    /**
     * Return the ConstraintAnchor we represent
     *
     * @return the ConstraintAnchor we represent
     */
    public ConstraintAnchor getAnchor() {
        return mAnchor;
    }

    /**
     * Retrieve the anchor from the owner's constraint widget
     */
    public void updateAnchor() {
        if (mOwner != null) {
            ConstraintWidget widget = mOwner.getConstraintWidget();
            // FIXME: should use a subclass
            if (widget instanceof Guideline) {
                mAnchor = ((Guideline) widget).getAnchor();
            } else {
                mAnchor = widget.getAnchor(mType);
            }
        } else {
            mAnchor = null;
        }
    }

    /**
     * Set the curve parameters
     * @param x1
     * @param y1
     * @param x2
     * @param y2
     * @param x3
     * @param y3
     * @param x4
     * @param y4
     */
    public void setCurveParameters(int x1, int y1, int x2, int y2, int x3, int y3, int x4, int y4) {
        mCurve[0] = x1;
        mCurve[1] = y1;
        mCurve[2] = x2;
        mCurve[3] = y2;
        mCurve[4] = x3;
        mCurve[5] = y3;
        mCurve[6] = x4;
        mCurve[7] = y4;
        mHasCurve = true;
    }

    /**
     * Set true if the connection is represented by a curve
     * @param hasCurve
     */
    public void setCurve(boolean hasCurve) {
        mHasCurve = hasCurve;
    }

    /**
     * Return true if the connection is represented by a curve
     * @return true if curve, false otherwise
     */
    public boolean hasCurve() { return mHasCurve; }

    /**
     * Return an array of 4 pairs of integers representing a curve
     * @return the curve parameters
     */
    public int[] getCurve() { return mCurve; }

    /**
     * Return the x position of this anchor
     * @return x position
     */
    public int getDrawX() { return mX; }

    /**
     * Return the y position of this anchor
     * @return y position
     */
    public int getDrawY() { return mY; }

    /**
     * Setter for the x position of this anchor
     * @param x the new x position
     */
    public void setDrawX(int x) {
        mX = x;
    }

    /**
     * Setter for the y position of this anchor
     * @param y the new y position
     */
    public void setDrawY(int y) {
        mY = y;
    }

    /**
     * Update the position of the anchor depending on the owner's position
     * @param viewTransform the view transform
     */
    void updatePosition(ViewTransform viewTransform) {
        if (mOwner == null) {
            mX = 0;
            mY = 0;
            return;
        }
        ConstraintWidget widget = mOwner.getConstraintWidget();
        int x = widget.getDrawX();
        int y = widget.getDrawY();
        int w = widget.getDrawWidth();
        int h = widget.getDrawHeight();
        switch (mAnchor.getType()) {
            case LEFT: {
                mX = x;
                mY = y + h / 2;
            } break;
            case TOP: {
                mX = x + w / 2;
                mY = y;
            } break;
            case RIGHT: {
                mX = x + w;
                mY = y + h / 2;
            } break;
            case BOTTOM: {
                mX = x + w / 2;
                mY = y + h;
            } break;
            case CENTER:
            case CENTER_X:
            case CENTER_Y: {
                mX = x + w / 2;
                mY = y + h / 2;
            } break;
            case BASELINE: {
                mX = x + w / 2;
                mY = y + widget.getBaselineDistance();
            } break;
        }
    }

    /**
     * Return the value of the margin at creation time, getting
     * the distance between the anchor source and the anchor target
     *
     * @param anchor the target anchor
     * @return the computed margin
     */
    public int getCreationMarginFrom(ConstraintHandle anchor) {
        int distance = 0;
        switch (mAnchor.getType()) {
            case LEFT: {
                distance = mX - anchor.mX;
            } break;
            case RIGHT: {
                distance = anchor.mX - mX;
            } break;
            case TOP: {
                distance = mY - anchor.mY;
            } break;
            case BOTTOM: {
                distance = anchor.mY - mY;
            } break;
        }
        if (distance < 0) {
            return 0;
        }
        return distance;
    }

    /**
     * Return the straight line distance from this anchor to another.
     * Basically, we return only the horizontal or vertical distance
     * for a compatible anchor, or Integer.MAX_VALUE otherwise.
     *
     * @param anchor the anchor we are comparing to
     * @return the straight line distance or Integer.MAX_VALUE if incompatible anchor.
     */
    public int getStraightDistanceFrom(ConstraintHandle anchor) {
        if (anchor == null) {
            return Integer.MAX_VALUE;
        }
        switch (mAnchor.getType()) {
            case CENTER: {
                if (anchor.getAnchor().isVerticalAnchor()) {
                    return Math.abs(anchor.mY - mY);
                } else {
                    return Math.abs(anchor.mX - mX);
                }
            }
            case LEFT:
            case RIGHT:
            case CENTER_X: {
                return Math.abs(anchor.mX - mX);
            }
            case TOP:
            case BOTTOM:
            case BASELINE:
            case CENTER_Y: {
                return Math.abs(anchor.mY - mY);
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Return the minimum distance from this anchor's side to another component's side
     * (hitting the first compatible side of the component). For example, looking at the
     * left anchor, we'll return the minimum distance between the vertical sides of this anchor's
     * component to the vertical sides of the target component.
     * @param component the component we are looking at
     * @return the distance between this anchor's component to another component.
     */
    public int getDistanceFrom(ConstraintWidget component) {
        int myComponentX = getOwner().getX();
        int myComponentY = getOwner().getY();
        int myComponentWidth = getOwner().getWidth();
        int myComponentHeight = getOwner().getHeight();
        switch (mAnchor.getType()) {
            case LEFT:
            case RIGHT:
            case CENTER_X: {
                int ya1 = myComponentY;
                int ya2 = myComponentY + myComponentHeight;
                int yb1 = component.getDrawY();
                int yb2 = component.getDrawY() + component.getDrawHeight();
                int d1 = Math.abs(ya1 - yb1);
                int d2 = Math.abs(ya1 - yb2);
                int d3 = Math.abs(ya2 - yb1);
                int d4 = Math.abs(ya2 - yb2);
                return Math.min(Math.min(d1, d2), Math.min(d3, d4));
            }
            case TOP:
            case BOTTOM:
            case BASELINE:
            case CENTER_Y: {
                int xa1 = myComponentX;
                int xa2 = myComponentX + myComponentWidth;
                int xb1 = component.getDrawX();
                int xb2 = component.getDrawX() + component.getDrawWidth();
                int d1 = Math.abs(xa1 - xb1);
                int d2 = Math.abs(xa1 - xb2);
                int d3 = Math.abs(xa2 - xb1);
                int d4 = Math.abs(xa2 - xb2);
                return Math.min(Math.min(d1, d2), Math.min(d3, d4));
            }
        }
        return Integer.MAX_VALUE;
    }

}
