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

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Model a constraint relation. Widgets contains anchors, and a constraint relation between
 * two widgets is made by connecting one anchor to another. The anchor will contains a pointer
 * to the target anchor if it is connected.
 */
public class ConstraintAnchor {

    private static final boolean ALLOW_BINARY = false;

    /**
     * Define the type of anchor
     */
    public enum Type { NONE, LEFT, TOP, RIGHT, BOTTOM, BASELINE, CENTER, CENTER_X, CENTER_Y }

    /**
     * Define the strength of an anchor connection
     */
    public enum Strength { NONE, STRONG, WEAK }

    /**
     * Define the type of connection - either relaxed (allow +/- errors) or strict (only allow positive errors)
     */
    public enum ConnectionType { RELAXED, STRICT }

    /**
     * Type of creator
     */
    public static final int USER_CREATOR = 0;
    public static final int SCOUT_CREATOR = 1;
    public static final int AUTO_CONSTRAINT_CREATOR = 2;

    private static final int UNSET_GONE_MARGIN = -1;

    final ConstraintWidget mOwner;
    final Type mType;
    ConstraintAnchor mTarget;
    public int mMargin = 0;
    int mGoneMargin = UNSET_GONE_MARGIN;

    private Strength mStrength = Strength.NONE;
    private ConnectionType mConnectionType = ConnectionType.RELAXED;
    private int mConnectionCreator = USER_CREATOR;

    /**
     * Constructor
     * @param owner the widget owner of this anchor.
     * @param type the anchor type.
     */
    public ConstraintAnchor(ConstraintWidget owner, Type type) {
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
    public Type getType() { return mType; }

    /**
     * Return the connection's margin from this anchor to its target.
     * @return the margin value. 0 if not connected.
     */
    public int getMargin() {
        if (mOwner.getVisibility() == ConstraintWidget.GONE) {
            return 0;
        }
        if (mGoneMargin > UNSET_GONE_MARGIN && mTarget != null
                && mTarget.mOwner.getVisibility() == ConstraintWidget.GONE) {
            return mGoneMargin;
        }
        return mMargin;
    }

    /**
     * Return the connection's strength (NONE if not connected)
     */
    public Strength getStrength() { return mStrength; }

    /**
     * Return the connection's target (null if not connected)
     * @return the ConstraintAnchor target
     */
    public ConstraintAnchor getTarget() { return mTarget; }

    /**
     * Return the type of connection
     * @return type connection type (relaxed or strict)
     */
    public ConnectionType getConnectionType() { return mConnectionType; }

    /**
     * Set the type of connection, either relaxed or strict
     * @param type
     */
    public void setConnectionType(ConnectionType type ) {
        mConnectionType = type;
    }

    /**
     * Return the creator of this connection
     */
    public int getConnectionCreator() { return mConnectionCreator; }

    /**
     * Set the creator for this connection
     * @param creator For now, values can be USER_CREATOR or SCOUT_CREATOR
     */
    public void setConnectionCreator(int creator) { mConnectionCreator = creator; }

    /**
     * Resets the anchor's connection.
     */
    public void reset() {
        mTarget = null;
        mMargin = 0;
        mGoneMargin = UNSET_GONE_MARGIN;
        mStrength = Strength.STRONG;
        mConnectionCreator = USER_CREATOR;
        mConnectionType = ConnectionType.RELAXED;
    }

    /**
     * Connects this anchor to another one.
     * @param toAnchor
     * @param margin
     * @param strength
     * @param creator
     * @return true if the connection succeeds.
     */
    public boolean connect(ConstraintAnchor toAnchor, int margin, Strength strength,
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
                           Strength strength, int creator, boolean forceConnection) {
        if (toAnchor == null) {
            mTarget = null;
            mMargin = 0;
            mGoneMargin = UNSET_GONE_MARGIN;
            mStrength = Strength.NONE;
            mConnectionCreator = AUTO_CONSTRAINT_CREATOR;
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
        mStrength = strength;
        mConnectionCreator = creator;
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
        return connect(toAnchor, margin, UNSET_GONE_MARGIN, Strength.STRONG, creator, false);
    }

    /**
     * Connects this anchor to another one.
     * @param toAnchor
     * @param margin
     * @return true if the connection succeeds.
     */
    public boolean connect(ConstraintAnchor toAnchor, int margin) {
        return connect(toAnchor, margin, UNSET_GONE_MARGIN, Strength.STRONG, USER_CREATOR, false);
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
        Type target = anchor.getType();
        if (target == mType) {
            if (mType == Type.BASELINE
                    && (!anchor.getOwner().hasBaseline() || !getOwner().hasBaseline())) {
                return false;
            }
            return true;
        }
        switch (mType) {
            case CENTER: {
                // allow everything but baseline and center_x/center_y
                return target != Type.BASELINE && target != Type.CENTER_X
                        && target != Type.CENTER_Y;
            }
            case LEFT:
            case RIGHT: {
                boolean isCompatible = target == Type.LEFT || target == Type.RIGHT;
                if (anchor.getOwner() instanceof Guideline) {
                    isCompatible = isCompatible || target == Type.CENTER_X;
                }
                return isCompatible;
            }
            case TOP:
            case BOTTOM: {
                boolean isCompatible = target == Type.TOP || target == Type.BOTTOM;
                if (anchor.getOwner() instanceof Guideline) {
                    isCompatible = isCompatible || target == Type.CENTER_Y;
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
     * Return true if this anchor is a side anchor
     *
     * @return true if side anchor
     */
    public boolean isSideAnchor() {
        switch (mType) {
            case LEFT:
            case RIGHT:
            case TOP:
            case BOTTOM:
                return true;
            case BASELINE:
            case CENTER:
            case CENTER_X:
            case CENTER_Y:
            case NONE:
                return false;
        }
        throw new AssertionError(mType.name());
    }

    /**
     * Return true if the connection to the given anchor is in the
     * same dimension (horizontal or vertical)
     *
     * @param anchor the anchor we want to connect to
     * @return true if it's an anchor on the same dimension
     */
    public boolean isSimilarDimensionConnection(ConstraintAnchor anchor) {
        Type target = anchor.getType();
        if (target == mType) {
            return true;
        }
        switch (mType) {
            case CENTER: {
                return target != Type.BASELINE;
            }
            case LEFT:
            case RIGHT:
            case CENTER_X: {
                return target == Type.LEFT || target == Type.RIGHT || target == Type.CENTER_X;
            }
            case TOP:
            case BOTTOM:
            case CENTER_Y:
            case BASELINE: {
                return target == Type.TOP || target == Type.BOTTOM || target == Type.CENTER_Y || target == Type.BASELINE;
            }
            case NONE:
                return false;
        }
        throw new AssertionError(mType.name());
    }

    /**
     * Set the strength of the connection (if there's one)
     * @param strength the new strength of the connection.
     */
    public void setStrength(Strength strength) {
        if (isConnected()) {
            mStrength = strength;
        }
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
     * Set the gone margin of the connection (if there's one)
     * @param margin the new margin of the connection
     */
    public void setGoneMargin(int margin) {
        if (isConnected()) {
            mGoneMargin = margin;
        }
    }

    /**
     * Utility function returning true if this anchor is a vertical one.
     *
     * @return true if vertical anchor, false otherwise
     */
    public boolean isVerticalAnchor() {
        switch (mType) {
            case LEFT:
            case RIGHT:
            case CENTER:
            case CENTER_X:
                return false;
            case CENTER_Y:
            case TOP:
            case BOTTOM:
            case BASELINE:
            case NONE:
                return true;
        }
        throw new AssertionError(mType.name());
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
     * Return the priority level of the anchor (higher is stronger).
     * This method is used to pick an anchor among many when there's a choice (we use it
     * for the snapping decisions)
     *
     * @return priority level
     */
    public int getSnapPriorityLevel() {
        switch (mType) {
            case LEFT: return 1;
            case RIGHT: return 1;
            case CENTER_X: return 0;
            case TOP: return 0;
            case BOTTOM: return 0;
            case CENTER_Y: return 1;
            case BASELINE: return 2;
            case CENTER: return 3;
            case NONE: return 0;
        }
        throw new AssertionError(mType.name());
    }

    /**
     * Return the priority level of the anchor (higher is stronger).
     * This method is used to pick an anchor among many when there's a choice (we use it
     * for finding the closest anchor)
     *
     * @return priority level
     */
    public int getPriorityLevel() {
        switch (mType) {
            case CENTER_X: return 0;
            case CENTER_Y: return 0;
            case BASELINE: return 1;
            case LEFT: return 2;
            case RIGHT: return 2;
            case TOP: return 2;
            case BOTTOM: return 2;
            case CENTER: return 2;
            case NONE: return 0;
        }
        throw new AssertionError(mType.name());
    }

    /**
     * Utility function to check if the anchor is compatible with another one.
     * Used for snapping.
     *
     * @param anchor the anchor we are checking against
     * @return true if compatible, false otherwise
     */
    public boolean isSnapCompatibleWith(ConstraintAnchor anchor) {
        if (mType == Type.CENTER) {
            return false;
        }
        if (mType == anchor.getType()) {
            return true;
        }
        switch (mType) {
            case LEFT: {
                switch (anchor.getType()) {
                    case RIGHT: return true;
                    case CENTER_X: return true;
                    default: return false;
                }
            }
            case RIGHT: {
                switch (anchor.getType()) {
                    case LEFT: return true;
                    case CENTER_X: return true;
                    default: return false;
                }
            }
            case CENTER_X: {
                switch (anchor.getType()) {
                    case LEFT: return true;
                    case RIGHT: return true;
                    default: return false;
                }
            }
            case TOP: {
                switch (anchor.getType()) {
                    case BOTTOM: return true;
                    case CENTER_Y: return true;
                    default: return false;
                }
            }
            case BOTTOM: {
                switch (anchor.getType()) {
                    case TOP: return true;
                    case CENTER_Y: return true;
                    default: return false;
                }
            }
            case CENTER_Y: {
                switch (anchor.getType()) {
                    case TOP: return true;
                    case BOTTOM: return true;
                    default: return false;
                }
            }
            case BASELINE:
            case CENTER:
            case NONE:
                return false;
        }
        throw new AssertionError(mType.name());
    }

    /**
     *
     *  Return true if we can connect this anchor to this target.
     * We recursively follow connections in order to detect eventual cycles; if we
     * do we disallow the connection.
     * We also only allow connections to direct parent, siblings, and descendants.
     *
     * @param target the ConstraintWidget we are trying to connect to
     * @param anchor Allow anchor if it loops back to me directly
     * @return if the connection is allowed, false otherwise
     */
    public boolean isConnectionAllowed(ConstraintWidget target, ConstraintAnchor anchor) {
        if (ALLOW_BINARY) {
            if (anchor != null && anchor.getTarget() == this) {
                return true;
            }
        }
        return isConnectionAllowed(target);
    }

    /**
     * Return true if we can connect this anchor to this target.
     * We recursively follow connections in order to detect eventual cycles; if we
     * do we disallow the connection.
     * We also only allow connections to direct parent, siblings, and descendants.
     *
     * @param target the ConstraintWidget we are trying to connect to
     * @return true if the connection is allowed, false otherwise
     */
    public boolean isConnectionAllowed(ConstraintWidget target) {
        HashSet<ConstraintWidget> checked = new HashSet<>();
        if (isConnectionToMe(target, checked)) {
            return false;
        }
        ConstraintWidget parent = getOwner().getParent();
        if (parent == target) { // allow connections to parent
            return true;
        }
        if (target.getParent() == parent) { // allow if we share the same parent
            return true;
        }
        return false;
    }

    /**
     * Recursive with check for loop
     *
     * @param target
     * @param checked set of things already checked
     * @return true if it is connected to me
     */
    private boolean isConnectionToMe(ConstraintWidget target, HashSet<ConstraintWidget> checked) {
        if (checked.contains(target)) {
            return false;
        }
        checked.add(target);

        if (target == getOwner()) {
            return true;
        }
        ArrayList<ConstraintAnchor> targetAnchors = target.getAnchors();
        for (int i = 0, targetAnchorsSize = targetAnchors.size(); i < targetAnchorsSize; i++) {
            ConstraintAnchor anchor = targetAnchors.get(i);
            if (anchor.isSimilarDimensionConnection(this) && anchor.isConnected()) {
                if (isConnectionToMe(anchor.getTarget().getOwner(), checked)) {
                    return true;
                }
            }
        }
        return false;
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
