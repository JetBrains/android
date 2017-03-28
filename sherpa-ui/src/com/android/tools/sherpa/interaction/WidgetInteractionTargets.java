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
import com.android.tools.sherpa.structure.WidgetCompanion;
import android.support.constraint.solver.widgets.ConnectionCandidate;
import android.support.constraint.solver.widgets.ConstraintAnchor;
import android.support.constraint.solver.widgets.ConstraintWidget;
import android.support.constraint.solver.widgets.ConstraintWidgetContainer;
import android.support.constraint.solver.widgets.Guideline;

import java.util.ArrayList;

/**
 * Encapsulate the interactions components of a widget (resize handles, etc.)
 */
public class WidgetInteractionTargets {

    private static final boolean USE_SIDE_RESIZE = false;

    // The resize handles available on the widget
    // note: all resize handles should be added to the mResizeHandles array (see addResizeHandles())
    private ResizeHandle mLeftTop = new ResizeHandle(this, ResizeHandle.Type.LEFT_TOP);
    private ResizeHandle mLeftBottom = new ResizeHandle(this, ResizeHandle.Type.LEFT_BOTTOM);
    private ResizeHandle mRightTop = new ResizeHandle(this, ResizeHandle.Type.RIGHT_TOP);
    private ResizeHandle mRightBottom = new ResizeHandle(this, ResizeHandle.Type.RIGHT_BOTTOM);
    private ResizeHandle mLeftSide = new ResizeHandle(this, ResizeHandle.Type.LEFT_SIDE);
    private ResizeHandle mRightSide = new ResizeHandle(this, ResizeHandle.Type.RIGHT_SIDE);
    private ResizeHandle mTopSide = new ResizeHandle(this, ResizeHandle.Type.TOP_SIDE);
    private ResizeHandle mBottomSide = new ResizeHandle(this, ResizeHandle.Type.BOTTOM_SIDE);

    protected ArrayList<ResizeHandle> mResizeHandles = new ArrayList<>();

    // The constraint handles available on the widget
    // note: all constraint handles should be added to the mResizeHandles array (see resetConstraintHandles())
    private ConstraintHandle mLeftAnchor = new ConstraintHandle(this, ConstraintAnchor.Type.LEFT);
    private ConstraintHandle mTopAnchor = new ConstraintHandle(this, ConstraintAnchor.Type.TOP);
    private ConstraintHandle mRightAnchor = new ConstraintHandle(this, ConstraintAnchor.Type.RIGHT);
    private ConstraintHandle mBottomAnchor = new ConstraintHandle(this, ConstraintAnchor.Type.BOTTOM);
    private ConstraintHandle mBaselineAnchor = new ConstraintHandle(this, ConstraintAnchor.Type.BASELINE);
    private ConstraintHandle mCenterXAnchor = new ConstraintHandle(this, ConstraintAnchor.Type.CENTER_X);
    private ConstraintHandle mCenterYAnchor = new ConstraintHandle(this, ConstraintAnchor.Type.CENTER_Y);
    private ConstraintHandle mCenterAnchor = new ConstraintHandle(this, ConstraintAnchor.Type.CENTER);

    protected ArrayList<ConstraintHandle> mConstraintHandles = new ArrayList<>();

    private final ConstraintWidget mWidget;

    /**
     * Base constructor
     *
     * @param widget the ConstraintWidget we represent
     */
    public WidgetInteractionTargets(ConstraintWidget widget) {
        mWidget = widget;
        addResizeHandles();
        resetConstraintHandles();
    }

    /**
     * Accessor to the ConstraintWidget we represent
     *
     * @return the ConstraintWidget we represent
     */
    public ConstraintWidget getConstraintWidget() { return mWidget; }

    /**
     * Add all resize handles into a single array
     */
    private void addResizeHandles() {
        mResizeHandles.add(mLeftTop);
        mResizeHandles.add(mLeftBottom);
        mResizeHandles.add(mRightTop);
        mResizeHandles.add(mRightBottom);
        if (mWidget instanceof Guideline) {
            mResizeHandles.add(mLeftSide);
            mResizeHandles.add(mTopSide);
        } else {
            if (USE_SIDE_RESIZE) {
                mResizeHandles.add(mLeftSide);
                mResizeHandles.add(mTopSide);
                mResizeHandles.add(mRightSide);
                mResizeHandles.add(mBottomSide);
            }
        }
    }

    /**
     * Add all constraint handles into a single array
     */
    public void resetConstraintHandles() {
        mConstraintHandles.clear();
        for (ConstraintAnchor anchor : mWidget.getAnchors()) {
            switch (anchor.getType()) {
                case LEFT: {
                    mConstraintHandles.add(mLeftAnchor);
                } break;
                case TOP: {
                    mConstraintHandles.add(mTopAnchor);
                } break;
                case RIGHT: {
                    mConstraintHandles.add(mRightAnchor);
                } break;
                case BOTTOM: {
                    mConstraintHandles.add(mBottomAnchor);
                } break;
                case BASELINE: {
                    mConstraintHandles.add(mBaselineAnchor);
                } break;
                case CENTER_X: {
                    mConstraintHandles.add(mCenterXAnchor);
                } break;
                case CENTER_Y: {
                    mConstraintHandles.add(mCenterYAnchor);
                } break;
                case CENTER: {
                    mConstraintHandles.add(mCenterAnchor);
                } break;
            }
        }
        for (ConstraintHandle handle : mConstraintHandles) {
            handle.updateAnchor();
        }
    }

    /**
     * Return the array of resize handles of this widget
     *
     * @return array of resize handles
     */
    public ArrayList<ResizeHandle> getResizeHandles() {
        return mResizeHandles;
    }

    /**
     * Return the array of constraint handles of this widget
     *
     * @return array of constraint handles
     */
    public ArrayList<ConstraintHandle> getConstraintHandles() { return mConstraintHandles; }

    /**
     * Update the positions of the resize handles
     * @param viewTransform
     */
    private void updateResizeHandlesPositions(ViewTransform viewTransform) {
        for (ResizeHandle mResizeHandle : mResizeHandles) {
            mResizeHandle.updatePosition(viewTransform);
        }
    }

    /**
     * Update the positions of the constraint handles
     * @param viewTransform the view transform
     */
    private void updateConstraintHandlesPositions() {
        for (ConstraintHandle mConstraintHandle : mConstraintHandles) {
            mConstraintHandle.updatePosition();
        }
    }

    /**
     * Update our position
     * @param viewTransform the view transform
     */
    public void updatePosition(ViewTransform viewTransform) {
        updateResizeHandlesPositions(viewTransform);
        updateConstraintHandlesPositions();
    }

    /**
     * Iterate through the resize handles of this widget and return the handle intersecting
     * with the point (x, y), if any. Corner resize handles takes precedence over side handles.
     * We also don't return hits if the handles is inoperable (due to constraints on the widget)
     *
     * @param x the x coordinate of the point
     * @param y the y coordinate of the point
     * @return true if we find a resize handle below the given point
     */
    public ResizeHandle findResizeHandle(float x, float y) {
        ResizeHandle candidate = null;
        if (mWidget instanceof Guideline) {
            Guideline guideline = (Guideline) mWidget;
            if (guideline.getOrientation() == Guideline.VERTICAL) {
                if (mLeftSide.hit(x, y)) {
                    return mLeftSide;
                }
            } else {
                if (mTopSide.hit(x, y)) {
                    return mTopSide;
                }
            }
        }
        ConstraintAnchor leftAnchor = mWidget.getAnchor(ConstraintAnchor.Type.LEFT);
        ConstraintAnchor topAnchor = mWidget.getAnchor(ConstraintAnchor.Type.TOP);
        ConstraintAnchor rightAnchor = mWidget.getAnchor(ConstraintAnchor.Type.RIGHT);
        ConstraintAnchor bottomAnchor = mWidget.getAnchor(ConstraintAnchor.Type.BOTTOM);
        ConstraintAnchor baselineAnchor = mWidget.getAnchor(ConstraintAnchor.Type.BASELINE);
        for (ResizeHandle handle : mResizeHandles) {
            if (handle.hit(x, y)) {
                boolean leftAnchorIsConnected = leftAnchor != null && leftAnchor.isConnected();
                boolean topAnchorIsConnected = topAnchor != null && topAnchor.isConnected();
                boolean rightAnchorIsConnected = rightAnchor != null && rightAnchor.isConnected();
                boolean bottomAnchorIsConnected = bottomAnchor != null && bottomAnchor.isConnected();
                boolean baselineAnchorIsConnected = baselineAnchor != null && baselineAnchor.isConnected();
                switch (handle.getType()) {
                    case LEFT_TOP: {
                        if (leftAnchorIsConnected || topAnchorIsConnected) {
                            continue;
                        }
                    }
                    break;
                    case RIGHT_TOP: {
                        if (rightAnchorIsConnected || topAnchorIsConnected) {
                            continue;
                        }
                    }
                    break;
                    case LEFT_BOTTOM: {
                        if (leftAnchorIsConnected || bottomAnchorIsConnected) {
                            continue;
                        }
                    }
                    break;
                    case RIGHT_BOTTOM: {
                        if (rightAnchorIsConnected || bottomAnchorIsConnected) {
                            continue;
                        }
                    }
                    break;
                    case LEFT_SIDE: {
                        if (leftAnchorIsConnected) {
                            continue;
                        }
                    }
                    break;
                    case RIGHT_SIDE: {
                        if (rightAnchorIsConnected) {
                            continue;
                        }
                    }
                    break;
                    case TOP_SIDE: {
                        if (topAnchorIsConnected || baselineAnchorIsConnected) {
                            continue;
                        }
                    }
                    break;
                    case BOTTOM_SIDE: {
                        if (bottomAnchorIsConnected) {
                            continue;
                        }
                    }
                    break;
                }
                if (candidate == null || candidate.isSideHandle()) {
                    candidate = handle;
                }
            }
        }
        return candidate;
    }

    /**
     * Return the ConstraintHandle linked to the given ConstraintAnchor
     * @param anchor the ConstraintAnchor
     * @return the ConstraintHandle corresponding to the ConstraintAnchor, or null if not found
     */
    public ConstraintHandle getConstraintHandle(ConstraintAnchor anchor) {
        for (ConstraintHandle handle : mConstraintHandles) {
            if (handle.getAnchor() == anchor) {
                return handle;
            }
        }
        return null;
    }

    /**
     * Utility function giving the ConstraintHandle associated to the given ConstraintAnchor
     * @param anchor the ConstraintAnchor
     * @return the associated ConstraintHandle, or null if not found
     */
    public static ConstraintHandle constraintHandle(ConstraintAnchor anchor) {
        if (anchor == null) {
            return null;
        }
        ConstraintWidget widget = anchor.getOwner();
        if (widget == null) {
            return null;
        }
        if (widget.getCompanionWidget() == null) {
            return null;
        }
        WidgetCompanion widgetCompanion = (WidgetCompanion) widget.getCompanionWidget();
        WidgetInteractionTargets widgetInteraction = widgetCompanion.getWidgetInteractionTargets();
        return widgetInteraction.getConstraintHandle(anchor);
    }

    /**
     * Fill in the ConnectionCandidate structure with the closest anchor found at (x, y).
     * This function will not consider CENTER_X/CENTER_Y anchors.
     *
     * @param viewTransform
     * @param x         x coordinate we are looking at
     * @param y         y coordinate we are looking at
     * @param candidate a structure containing the current best anchor candidate.
     * @param mousePress true if we are in mouse press
     */
    public void findClosestConnection(ViewTransform viewTransform, float x, float y,
            ConnectionCandidate candidate,
            boolean mousePress) {
        // FIXME: should use subclasses this
        if (mWidget instanceof Guideline) {
            float distance;
            Guideline guideline = (Guideline) mWidget;
            ConstraintAnchor anchor = guideline.getAnchor();
            ConstraintHandle handle = constraintHandle(anchor);
            if (handle == null) {
                return;
            }
            if (guideline.getOrientation() == Guideline.VERTICAL) {
                distance = (handle.getDrawX() - x) * (handle.getDrawX() - x);
            } else {
                distance = (handle.getDrawY() - y) * (handle.getDrawY() - y);
            }
            distance = viewTransform.getSwingDimensionF(distance);
            if (distance < candidate.distance) {
                candidate.anchorTarget = anchor;
                candidate.distance = distance;
            }
        } else if (mWidget instanceof ConstraintWidgetContainer) {
            for (ConstraintHandle handle : mConstraintHandles) {
                ConstraintAnchor anchor = handle.getAnchor();
                if (anchor.getType() == ConstraintAnchor.Type.CENTER_X
                        || anchor.getType() == ConstraintAnchor.Type.CENTER_Y) {
                    continue;
                }
                float distance = 0;
                boolean computed = false;
                if (!mousePress && anchor.isSideAnchor()) {
                    if (!anchor.isVerticalAnchor()) {
                        if (y >= mWidget.getDrawY() && y <= mWidget.getDrawBottom()) {
                            distance = (handle.getDrawX() - x) * (handle.getDrawX() - x);
                            computed = true;
                        }
                    } else {
                        if (x >= mWidget.getDrawX() && x <= mWidget.getDrawRight()) {
                            distance = (handle.getDrawY() - y) * (handle.getDrawY() - y);
                            computed = true;
                        }
                    }
                }
                if (!computed) {
                    distance = (handle.getDrawX() - x) * (handle.getDrawX() - x) +
                            (handle.getDrawY() - y) * (handle.getDrawY() - y);
                }
                distance = viewTransform.getSwingDimensionF(distance);
                if (distance < candidate.distance) {
                    candidate.anchorTarget = anchor;
                    candidate.distance = distance;
                }
            }

        } else {
            for (ConstraintHandle handle : mConstraintHandles) {
                ConstraintAnchor anchor = handle.getAnchor();
                float distance = (handle.getDrawX() - x) * (handle.getDrawX() - x) +
                        (handle.getDrawY() - y) * (handle.getDrawY() - y);
                if (anchor.getType() == ConstraintAnchor.Type.CENTER_X
                        || anchor.getType() == ConstraintAnchor.Type.CENTER_Y) {
                    continue;
                }
                if (anchor.getType() == ConstraintAnchor.Type.BASELINE) {
                    if (!anchor.getOwner().hasBaseline()) {
                        continue;
                    }
                    ConstraintWidget widget = anchor.getOwner();
                    int minX = widget.getDrawX();
                    int maxX = widget.getDrawRight();
                    float d = Math.abs(handle.getDrawY() - y);
                    if (x >= minX && x <= maxX && d < 3) {
                        distance = d * d;
                    }
                }
                distance = viewTransform.getSwingDimensionF(distance);
                if (distance <= candidate.distance) {
                    if (candidate.anchorTarget == null
                            ||
                            candidate.anchorTarget.getPriorityLevel() < anchor.getPriorityLevel()) {
                        candidate.anchorTarget = anchor;
                        candidate.distance = distance;
                    }
                }
            }
        }
    }
}
