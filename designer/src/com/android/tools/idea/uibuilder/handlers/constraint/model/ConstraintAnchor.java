/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.constraint.model;

import java.util.HashSet;

/**
 * Model a constraint relation. Widgets contains anchors, and a constraint relation between
 * two widgets is made by connecting one anchor to another. The anchor will contains a pointer
 * to the target anchor if it is connected.
 */
public class ConstraintAnchor {

  private static final int UNSET_GONE_MARGIN = -1;

    final ConstraintWidget mOwner;
    final ConstraintAnchorConstants.Type mType;
    ConstraintAnchor mTarget;
    public int mMargin = 0;
    int mGoneMargin = UNSET_GONE_MARGIN;

    /**
     * Constructor
     * @param owner the widget owner of this anchor.
     * @param type the anchor type.
     */
    public ConstraintAnchor(ConstraintWidget owner, ConstraintAnchorConstants.Type type) {
        mOwner = owner;
        mType = type;
    }

    /**
     * Return the anchor's owner
     * @return the Widget owning the anchor
     */
    public ConstraintWidget getOwner() { return mOwner; }

    /**
     * Return the type of the anchor
     * @return type of the anchor.
     */
    public ConstraintAnchorConstants.Type getType() { return mType; }

    /**
     * Return the connection's margin from this anchor to its target.
     * @return the margin value. 0 if not connected.
     */
    public int getMargin() {
        if (mOwner.getVisibility() == ConstraintWidgetConstants.GONE) {
            return 0;
        }
        if (mGoneMargin > UNSET_GONE_MARGIN && mTarget != null
                && mTarget.mOwner.getVisibility() == ConstraintWidgetConstants.GONE) {
            return mGoneMargin;
        }
        return mMargin;
    }

    /**
     * Return the connection's target (null if not connected)
     * @return the ConstraintAnchor target
     */
    public ConstraintAnchor getTarget() { return mTarget; }

    /**
     * Resets the anchor's connection.
     */
    public void reset() {
        mTarget = null;
        mMargin = 0;
        mGoneMargin = UNSET_GONE_MARGIN;
    }

    /**
     * Connects this anchor to another one.
     * @param toAnchor
     * @param margin
     * @param strength
     * @param creator
     * @return true if the connection succeeds.
     */
    public boolean connect(ConstraintAnchor toAnchor, int margin, ConstraintAnchorConstants.Strength strength,
            int creator) {
        return connect(toAnchor, margin, -1, strength, creator, false);
    }

    /**
     * Connects this anchor to another one.
     *
     * @param toAnchor
     * @param margin
     * @param goneMargin
     * @param strength
     * @param creator
     * @param forceConnection
     * @return true if the connection succeeds.
     */
    public boolean connect(ConstraintAnchor toAnchor, int margin, int goneMargin,
                           ConstraintAnchorConstants.Strength strength, int creator, boolean forceConnection) {
        if (toAnchor == null) {
            mTarget = null;
            mMargin = 0;
            mGoneMargin = UNSET_GONE_MARGIN;
                  return true;
        }
        if (!forceConnection && !isValidConnection(toAnchor)) {
            return false;
        }
        mTarget = toAnchor;
        if (margin > 0) {
            mMargin = margin;
        } else {
            mMargin = 0;
        }
        mGoneMargin = goneMargin;
        return true;
    }

    /**
     * Connects this anchor to another one.
     * @param toAnchor
     * @param margin
     * @param creator
     * @return true if the connection succeeds.
     */
    public boolean connect(ConstraintAnchor toAnchor, int margin, int creator) {
        return connect(toAnchor, margin, UNSET_GONE_MARGIN, ConstraintAnchorConstants.Strength.STRONG, creator, false);
    }

    /**
     * Connects this anchor to another one.
     * @param toAnchor
     * @param margin
     * @return true if the connection succeeds.
     */
    public boolean connect(ConstraintAnchor toAnchor, int margin) {
        return connect(toAnchor, margin, UNSET_GONE_MARGIN, ConstraintAnchorConstants.Strength.STRONG, ConstraintAnchorConstants.USER_CREATOR, false);
    }

    /**
     * Returns the connection status of this anchor
     * @return true if the anchor is connected to another one.
     */
    public boolean isConnected() {
        return mTarget != null;
    }

    /**
     * Checks if the connection to a given anchor is valid.
     * @param anchor the anchor we want to connect to
     * @return true if it's a compatible anchor
     */
    public boolean isValidConnection(ConstraintAnchor anchor) {
        if (anchor == null) {
            return false;
        }
        ConstraintAnchorConstants.Type target = anchor.getType();
        if (target == mType) {
            if (mType == ConstraintAnchorConstants.Type.BASELINE
                    && (!anchor.getOwner().hasBaseline() || !getOwner().hasBaseline())) {
                return false;
            }
            return true;
        }
        switch (mType) {
            case CENTER: {
                // allow everything but baseline and center_x/center_y
                return target != ConstraintAnchorConstants.Type.BASELINE && target != ConstraintAnchorConstants.Type.CENTER_X
                       && target != ConstraintAnchorConstants.Type.CENTER_Y;
            }
            case LEFT:
            case RIGHT: {
                boolean isCompatible = target == ConstraintAnchorConstants.Type.LEFT || target == ConstraintAnchorConstants.Type.RIGHT;
                if (anchor.getOwner() instanceof Guideline) {
                    isCompatible = isCompatible || target == ConstraintAnchorConstants.Type.CENTER_X;
                }
                return isCompatible;
            }
            case TOP:
            case BOTTOM: {
                boolean isCompatible = target == ConstraintAnchorConstants.Type.TOP || target == ConstraintAnchorConstants.Type.BOTTOM;
                if (anchor.getOwner() instanceof Guideline) {
                    isCompatible = isCompatible || target == ConstraintAnchorConstants.Type.CENTER_Y;
                }
                return isCompatible;
            }
            case BASELINE:
            case CENTER_X:
            case CENTER_Y:
            case NONE:
                return false;
        }
        throw new AssertionError(mType.name());
    }

    /**
     * Set the margin of the connection (if there's one)
     * @param margin the new margin of the connection
     */
    public void setMargin(int margin) {
        if (isConnected()) {
            mMargin = margin;
        }
    }

    /**
     * Return a string representation of this anchor
     *
     * @return string representation of the anchor
     */
    @Override
    public String toString() {
        HashSet<ConstraintAnchor> visited = new HashSet<ConstraintAnchor>();
        return mOwner.getDebugName() + ":" + mType.toString() + (mTarget != null ? " connected to " + mTarget.toString(visited) : "");
    }

    private String toString(HashSet<ConstraintAnchor> visited) {
        if (visited.add(this)) {
            return mOwner.getDebugName() + ":" + mType.toString() + (mTarget != null ? " connected to " + mTarget.toString(visited) : "");
        }
        return "<-";
    }

    /**
     * Returns the opposite anchor to this one
     * @return opposite anchor
     */
    public final ConstraintAnchor getOpposite() {
        switch (mType) {
            case LEFT: {
                return mOwner.mRight;
            }
            case RIGHT: {
                return mOwner.mLeft;
            }
            case TOP: {
                return mOwner.mBottom;
            }
            case BOTTOM: {
                return mOwner.mTop;
            }
            case BASELINE:
            case CENTER:
            case CENTER_X:
            case CENTER_Y:
            case NONE:
                return null;
        }
        throw new AssertionError(mType.name());
    }
}
