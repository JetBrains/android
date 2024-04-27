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

/**
 * Implements a constraint Widget model supporting constraints relations between other widgets.
 * <p>
 * The widget has various anchors (i.e. Left, Top, Right, Bottom, representing their respective
 * sides, as well as Baseline, Center_X and Center_Y). Connecting anchors from one widget to another
 * represents a constraint relation between the two anchors; the {@link LinearSystem} will then
 * be able to use this model to try to minimize the distances between connected anchors.
 * </p>
 * <p>
 * If opposite anchors are connected (e.g. Left and Right anchors), if they have the same strength,
 * the widget will be equally pulled toward their respective target anchor positions; if the widget
 * has a fixed size, this means that the widget will be centered between the two target anchors. If
 * the widget's size is allowed to adjust, the size of the widget will change to be as large as
 * necessary so that the widget's anchors and the target anchors' distances are zero.
 * </p>
 * Constraints are set by connecting a widget's anchor to another via the
 * {@link #connect} function.
 */
public class ConstraintWidget {
    // Support for direct resolution
    public int mHorizontalResolution = ConstraintWidgetConstants.UNKNOWN;
    public int mVerticalResolution = ConstraintWidgetConstants.UNKNOWN;

    int mMatchConstraintDefaultWidth = ConstraintWidgetConstants.MATCH_CONSTRAINT_SPREAD;
    int mMatchConstraintDefaultHeight = ConstraintWidgetConstants.MATCH_CONSTRAINT_SPREAD;
    int mMatchConstraintMinWidth = 0;
    int mMatchConstraintMaxWidth = 0;
    float mMatchConstraintPercentWidth = 1;
    int mMatchConstraintMinHeight = 0;
    int mMatchConstraintMaxHeight = 0;
    float mMatchConstraintPercentHeight = 1;

    int mResolvedDimensionRatioSide = ConstraintWidgetConstants.UNKNOWN;
    float mResolvedDimensionRatio = 1.0f;

    private final int[] mMaxDimension = {Integer.MAX_VALUE, Integer.MAX_VALUE};

    public int getMaxHeight() {
        return mMaxDimension[ConstraintWidgetConstants.VERTICAL];
    }

    public int getMaxWidth() {
        return mMaxDimension[ConstraintWidgetConstants.HORIZONTAL];
    }

    public void setMaxWidth(int maxWidth) {
        mMaxDimension[ConstraintWidgetConstants.HORIZONTAL] = maxWidth;
    }

    public void setMaxHeight(int maxWidth) {
        mMaxDimension[ConstraintWidgetConstants.VERTICAL] = maxWidth;
    }

    /**
     * Define how the widget will resize
     */
    public enum DimensionBehaviour {
        FIXED, WRAP_CONTENT, MATCH_CONSTRAINT, MATCH_PARENT
    }

    // The anchors available on the widget
    // note: all anchors should be added to the mAnchors array (see addAnchors())
    ConstraintAnchor mLeft = new ConstraintAnchor(this, ConstraintAnchorConstants.Type.LEFT);
    ConstraintAnchor mTop = new ConstraintAnchor(this, ConstraintAnchorConstants.Type.TOP);
    ConstraintAnchor mRight = new ConstraintAnchor(this, ConstraintAnchorConstants.Type.RIGHT);
    ConstraintAnchor mBottom = new ConstraintAnchor(this, ConstraintAnchorConstants.Type.BOTTOM);
    ConstraintAnchor mBaseline = new ConstraintAnchor(this, ConstraintAnchorConstants.Type.BASELINE);
    ConstraintAnchor mCenterX = new ConstraintAnchor(this, ConstraintAnchorConstants.Type.CENTER_X);
    ConstraintAnchor mCenterY = new ConstraintAnchor(this, ConstraintAnchorConstants.Type.CENTER_Y);
    ConstraintAnchor mCenter = new ConstraintAnchor(this, ConstraintAnchorConstants.Type.CENTER);

    protected ConstraintAnchor[] mListAnchors = {mLeft, mRight, mTop, mBottom, mBaseline, mCenter};
    protected ArrayList<ConstraintAnchor> mAnchors = new ArrayList<>();

    // The horizontal and vertical behaviour for the widgets' dimensions
    static final int DIMENSION_HORIZONTAL = 0;
    static final int DIMENSION_VERTICAL = 1;
    protected DimensionBehaviour[] mListDimensionBehaviors = {DimensionBehaviour.FIXED, DimensionBehaviour.FIXED};

    // Parent of this widget
    ConstraintWidget mParent = null;

    // Dimensions of the widget
    int mWidth = 0;
    int mHeight = 0;
    protected float mDimensionRatio = 0;
    protected int mDimensionRatioSide = ConstraintWidgetConstants.UNKNOWN;

    // Origin of the widget
    protected int mX = 0;
    protected int mY = 0;

    // Current draw position in container's coordinate
    private int mDrawX = 0;
    private int mDrawY = 0;
    private int mDrawWidth = 0;
    private int mDrawHeight = 0;

    // Root offset
    protected int mOffsetX = 0;
    protected int mOffsetY = 0;

    // Baseline distance relative to the top of the widget
    int mBaselineDistance = 0;

    // Minimum sizes for the widget
    protected int mMinWidth;
    protected int mMinHeight;

    // Wrap content sizes for the widget
    private int mWrapWidth;
    private int mWrapHeight;

  float mHorizontalBiasPercent = ConstraintWidgetConstants.DEFAULT_BIAS;
    float mVerticalBiasPercent = ConstraintWidgetConstants.DEFAULT_BIAS;

    // Contains the visibility status of the widget (VISIBLE, INVISIBLE, or GONE)
    private int mVisibility = ConstraintWidgetConstants.VISIBLE;

    private String mDebugName = null;
    private String mType = null;

    boolean mHorizontalWrapVisited;
    boolean mVerticalWrapVisited;

    // Chain support
    int mHorizontalChainStyle = ConstraintWidgetConstants.CHAIN_SPREAD;
    int mVerticalChainStyle = ConstraintWidgetConstants.CHAIN_SPREAD;
    boolean mHorizontalChainFixedPosition;
    boolean mVerticalChainFixedPosition;

    float[] mWeight = {0, 0};

    // TODO: see if we can make this simpler
    public void reset() {
        mLeft.reset();
        mTop.reset();
        mRight.reset();
        mBottom.reset();
        mBaseline.reset();
        mCenterX.reset();
        mCenterY.reset();
        mCenter.reset();
        mParent = null;
        mWidth = 0;
        mHeight = 0;
        mDimensionRatio = 0;
        mDimensionRatioSide = ConstraintWidgetConstants.UNKNOWN;
        mX = 0;
        mY = 0;
        mDrawX = 0;
        mDrawY = 0;
        mDrawWidth = 0;
        mDrawHeight = 0;
        mOffsetX = 0;
        mOffsetY = 0;
        mBaselineDistance = 0;
        mMinWidth = 0;
        mMinHeight = 0;
        mWrapWidth = 0;
        mWrapHeight = 0;
        mHorizontalBiasPercent = ConstraintWidgetConstants.DEFAULT_BIAS;
        mVerticalBiasPercent = ConstraintWidgetConstants.DEFAULT_BIAS;
        mListDimensionBehaviors[DIMENSION_HORIZONTAL] = DimensionBehaviour.FIXED;
        mListDimensionBehaviors[DIMENSION_VERTICAL] = DimensionBehaviour.FIXED;
        mVisibility = ConstraintWidgetConstants.VISIBLE;
        mDebugName = null;
        mType = null;
        mHorizontalWrapVisited = false;
        mVerticalWrapVisited = false;
        mHorizontalChainStyle = ConstraintWidgetConstants.CHAIN_SPREAD;
        mVerticalChainStyle = ConstraintWidgetConstants.CHAIN_SPREAD;
        mHorizontalChainFixedPosition = false;
        mVerticalChainFixedPosition = false;
        mWeight[DIMENSION_HORIZONTAL] = 0;
        mWeight[DIMENSION_VERTICAL] = 0;
        mHorizontalResolution = ConstraintWidgetConstants.UNKNOWN;
        mVerticalResolution = ConstraintWidgetConstants.UNKNOWN;
        mMaxDimension[ConstraintWidgetConstants.HORIZONTAL] = Integer.MAX_VALUE;
        mMaxDimension[ConstraintWidgetConstants.VERTICAL] = Integer.MAX_VALUE;
        mMatchConstraintDefaultWidth = ConstraintWidgetConstants.MATCH_CONSTRAINT_SPREAD;
        mMatchConstraintDefaultHeight = ConstraintWidgetConstants.MATCH_CONSTRAINT_SPREAD;
        mMatchConstraintPercentWidth = 1;
        mMatchConstraintPercentHeight = 1;
        mMatchConstraintMaxWidth = Integer.MAX_VALUE;
        mMatchConstraintMaxHeight = Integer.MAX_VALUE;
        mMatchConstraintMinWidth = 0;
        mMatchConstraintMinHeight = 0;
        mResolvedDimensionRatioSide = ConstraintWidgetConstants.UNKNOWN;
        mResolvedDimensionRatio = 1f;
    }

    /*-----------------------------------------------------------------------*/
    // Creation
    /*-----------------------------------------------------------------------*/

    /**
     * Default constructor
     */
    public ConstraintWidget() {
        addAnchors();
    }

    /**
     * Add all the anchors to the mAnchors array
     */
    private void addAnchors() {
        mAnchors.add(mLeft);
        mAnchors.add(mTop);
        mAnchors.add(mRight);
        mAnchors.add(mBottom);
        mAnchors.add(mCenterX);
        mAnchors.add(mCenterY);
        mAnchors.add(mCenter);
        mAnchors.add(mBaseline);
    }

    /**
     * Returns true if the widget is the root widget
     *
     * @return true if root widget, false otherwise
     */
    public boolean isRoot() {
        return mParent == null;
    }

    /**
     * Returns the parent of this widget if there is one
     *
     * @return parent
     */
    public ConstraintWidget getParent() {
        return mParent;
    }

    /**
     * Set the parent of this widget
     *
     * @param widget parent
     */
    public void setParent(ConstraintWidget widget) {
        mParent = widget;
    }

    /**
     * Returns the type string if set
     *
     * @return type (null if not set)
     */
    public String getType() {
        return mType;
    }

    /**
     * Set the type of the widget (as a String)
     *
     * @param type type of the widget
     */
    public void setType(String type) {
        mType = type;
    }

    /**
     * Set the visibility for this widget
     *
     * @param visibility either VISIBLE, INVISIBLE, or GONE
     */
    public void setVisibility(int visibility) {
        mVisibility = visibility;
    }

    /**
     * Returns the current visibility value for this widget
     *
     * @return the visibility (VISIBLE, INVISIBLE, or GONE)
     */
    public int getVisibility() {
        return mVisibility;
    }

    /**
     * Returns the name of this widget (used for debug purposes)
     *
     * @return the debug name
     */
    public String getDebugName() {
        return mDebugName;
    }

    /**
     * Set the debug name of this widget
     */
    public void setDebugName(String name) {
        mDebugName = name;
    }

    /**
     * Returns a string representation of the ConstraintWidget
     *
     * @return string representation of the widget
     */
    @Override
    public String toString() {
        return (mType != null ? "type: " + mType + " " : "")
               + (mDebugName != null ? "id: " + mDebugName + " " : "")
               + "(" + mX + ", " + mY + ") - (" + mWidth + " x " + mHeight + ")"
               + " wrap: (" + mWrapWidth + " x " + mWrapHeight + ")";
    }

    /*-----------------------------------------------------------------------*/
    // Position
    /*-----------------------------------------------------------------------*/
    // The widget position is expressed in two ways:
    // - relative to its direct parent container (getX(), getY())
    // - relative to the root container (getDrawX(), getDrawY())
    // Additionally, getDrawX()/getDrawY() are used when animating the
    // widget position on screen
    /*-----------------------------------------------------------------------*/


    /**
     * Return the x position of the widget, relative to its container
     *
     * @return x position
     */
    public int getX() {
        return mX;
    }

    /**
     * Return the y position of the widget, relative to its container
     *
     * @return y position
     */
    public int getY() {
        return mY;
    }

    /**
     * Return the width of the widget
     *
     * @return width width
     */
    public int getWidth() {
        if (mVisibility == ConstraintWidgetConstants.GONE) {
            return 0;
        }
        return mWidth;
    }

    /**
     * Return the height of the widget
     *
     * @return height height
     */
    public int getHeight() {
        if (mVisibility == ConstraintWidgetConstants.GONE) {
            return 0;
        }
        return mHeight;
    }

    /**
     * Return the x position of the widget, relative to the root
     *
     * @return x position
     */
    public int getDrawX() {
        return mDrawX + mOffsetX;
    }

    /**
     * Return the y position of the widget, relative to the root
     *
     * @return
     */
    public int getDrawY() {
        return mDrawY + mOffsetY;
    }

    public int getDrawWidth() {
        return mDrawWidth;
    }

    public int getDrawHeight() {
        return mDrawHeight;
    }

    /**
     * Return the x position of the widget, relative to the root
     * (without animation)
     *
     * @return x position
     */
    protected int getRootX() {
        return mX + mOffsetX;
    }

    /**
     * Return the y position of the widget, relative to the root
     * (without animation)
     *
     * @return
     */
    protected int getRootY() {
        return mY + mOffsetY;
    }

    /**
     * Return the minimum width of the widget
     *
     * @return minimum width
     */
    public int getMinWidth() {
        return mMinWidth;
    }

    /**
     * Return the minimum height of the widget
     *
     * @return minimum height
     */
    public int getMinHeight() {
        return mMinHeight;
    }

    /**
     * Return the left position of the widget (similar to {@link #getX()})
     *
     * @return left position of the widget
     */
    public int getLeft() {
        return getX();
    }

    /**
     * Return the top position of the widget (similar to {@link #getY()})
     *
     * @return top position of the widget
     */
    public int getTop() {
        return getY();
    }

    /**
     * Return the right position of the widget
     *
     * @return right position of the widget
     */
    public int getRight() {
        return getX() + mWidth;
    }

    /**
     * Return the bottom position of the widget
     *
     * @return bottom position of the widget
     */
    public int getBottom() {
        return getY() + mHeight;
    }

    /**
     * Return true if this widget has a baseline
     *
     * @return true if the widget has a baseline, false otherwise
     */
    public boolean hasBaseline() {
        return mBaselineDistance > 0;
    }

    /**
     * Return the array of anchors of this widget
     *
     * @return array of anchors
     */
    public ArrayList<ConstraintAnchor> getAnchors() {
        return mAnchors;
    }

    /**
     * Set the x position of the widget, relative to its container
     *
     * @param x x position
     */
    public void setX(int x) {
        mX = x;
    }

    /**
     * Set the y position of the widget, relative to its container
     *
     * @param y y position
     */
    public void setY(int y) {
        mY = y;
    }

    /**
     * Set the offset of this widget relative to the root widget
     *
     * @param x horizontal offset
     * @param y vertical offset
     */
    public void setOffset(int x, int y) {
        mOffsetX = x;
        mOffsetY = y;
    }

    /**
     * Update the draw position to match the true position.
     * If animating is on, the transition between the old
     * position and new position will be animated...
     */
    public void updateDrawPosition() {
        int left = mX;
        int top = mY;
        int right = mX + mWidth;
        int bottom = mY + mHeight;
        mDrawX = left;
        mDrawY = top;
        mDrawWidth = right - left;
        mDrawHeight = bottom - top;
    }

    /**
     * Set the x position of the widget, relative to the root
     *
     * @param x x position
     */
    public void setDrawX(int x) {
        mDrawX = x - mOffsetX;
        mX = mDrawX;
    }

    /**
     * Set the y position of the widget, relative to its container
     *
     * @param y y position
     */
    public void setDrawY(int y) {
        mDrawY = y - mOffsetY;
        mY = mDrawY;
    }

    /**
     * Set the draw width of the widget
     *
     * @param drawWidth
     */
    public void setDrawWidth(int drawWidth) {
        mDrawWidth = drawWidth;
    }

    /**
     * Set the draw height of the widget
     *
     * @param drawHeight
     */
    public void setDrawHeight(int drawHeight) {
        mDrawHeight = drawHeight;
    }

    /**
     * Set the width of the widget
     *
     * @param w width
     */
    public void setWidth(int w) {
        mWidth = w;
        if (mWidth < mMinWidth) {
            mWidth = mMinWidth;
        }
    }

    /**
     * Set the height of the widget
     *
     * @param h height
     */
    public void setHeight(int h) {
        mHeight = h;
        if (mHeight < mMinHeight) {
            mHeight = mMinHeight;
        }
    }

    /**
     * Set the minimum width of the widget
     *
     * @param w minimum width
     */
    public void setMinWidth(int w) {
      mMinWidth = Math.max(w, 0);
    }

    /**
     * Set the minimum height of the widget
     *
     * @param h minimum height
     */
    public void setMinHeight(int h) {
      mMinHeight = Math.max(h, 0);
    }

    /*-----------------------------------------------------------------------*/
    // Connections
    /*-----------------------------------------------------------------------*/

    /**
     * Callback when a widget connects to us
     *
     * @param source
     */
    public void connectedTo(@SuppressWarnings("unused") ConstraintWidget source) {
        // do nothing by default
    }

    /**
     * Connect the given anchors together (the from anchor should be owned by this widget)
     *
     * @param from    the anchor we are connecting from (of this widget)
     * @param to      the anchor we are connecting to
     * @param margin  how much margin we want to have
     * @param creator who created the connection
     */
    public void connect(ConstraintAnchor from, ConstraintAnchor to, int margin,
                        ConstraintAnchorConstants.Strength strength, int creator) {
        if (from.getOwner() == this) {
            connect(from.getType(), to.getOwner(), to.getType(), margin, strength, creator);
        }
    }

    /**
     * Connect a given anchor of this widget to another anchor of a target widget
     *
     * @param constraintFrom which anchor of this widget to connect from
     * @param target         the target widget
     * @param constraintTo   the target anchor on the target widget
     * @param margin         how much margin we want to keep as a minimum distance between the two anchors
     * @return the undo operation
     */
    public void connect(ConstraintAnchorConstants.Type constraintFrom, ConstraintWidget target,
                        ConstraintAnchorConstants.Type constraintTo, int margin) {
        connect(constraintFrom, target, constraintTo, margin,
                ConstraintAnchorConstants.Strength.STRONG);
    }

    /**
     * Connect a given anchor of this widget to another anchor of a target widget
     *
     * @param constraintFrom which anchor of this widget to connect from
     * @param target         the target widget
     * @param constraintTo   the target anchor on the target widget
     * @return the undo operation
     */
    public void connect(ConstraintAnchorConstants.Type constraintFrom,
                        ConstraintWidget target,
                        ConstraintAnchorConstants.Type constraintTo) {
        connect(constraintFrom, target, constraintTo, 0, ConstraintAnchorConstants.Strength.STRONG);
    }


    /**
     * Connect a given anchor of this widget to another anchor of a target widget
     *
     * @param constraintFrom which anchor of this widget to connect from
     * @param target         the target widget
     * @param constraintTo   the target anchor on the target widget
     * @param margin         how much margin we want to keep as a minimum distance between the two anchors
     * @param strength       the constraint strength (Weak/Strong)
     */
    public void connect(ConstraintAnchorConstants.Type constraintFrom,
                        ConstraintWidget target,
                        ConstraintAnchorConstants.Type constraintTo, int margin,
                        ConstraintAnchorConstants.Strength strength) {
        connect(constraintFrom, target, constraintTo, margin, strength,
                ConstraintAnchorConstants.USER_CREATOR);
    }

    /**
     * Connect a given anchor of this widget to another anchor of a target widget
     *
     * @param constraintFrom which anchor of this widget to connect from
     * @param target         the target widget
     * @param constraintTo   the target anchor on the target widget
     * @param margin         how much margin we want to keep as a minimum distance between the two anchors
     * @param strength       the constraint strength (Weak/Strong)
     * @param creator        who created the constraint
     */
    public void connect(ConstraintAnchorConstants.Type constraintFrom,
                        ConstraintWidget target,
                        ConstraintAnchorConstants.Type constraintTo, int margin,
                        ConstraintAnchorConstants.Strength strength, int creator) {
        if (constraintFrom == ConstraintAnchorConstants.Type.CENTER) {
            // If we have center, we connect instead to the corresponding
            // left/right or top/bottom pairs
            if (constraintTo == ConstraintAnchorConstants.Type.CENTER) {
                ConstraintAnchor left = getAnchor(ConstraintAnchorConstants.Type.LEFT);
                ConstraintAnchor right = getAnchor(ConstraintAnchorConstants.Type.RIGHT);
                ConstraintAnchor top = getAnchor(ConstraintAnchorConstants.Type.TOP);
                ConstraintAnchor bottom = getAnchor(ConstraintAnchorConstants.Type.BOTTOM);
                boolean centerX = false;
                boolean centerY = false;
                if ((left != null && left.isConnected())
                    || (right != null && right.isConnected())) {
                    // don't apply center here
                }
                else {
                    connect(ConstraintAnchorConstants.Type.LEFT, target,
                            ConstraintAnchorConstants.Type.LEFT, 0, strength, creator);
                    connect(ConstraintAnchorConstants.Type.RIGHT, target,
                            ConstraintAnchorConstants.Type.RIGHT, 0, strength, creator);
                    centerX = true;
                }
                if ((top != null && top.isConnected())
                    || (bottom != null && bottom.isConnected())) {
                    // don't apply center here
                }
                else {
                    connect(ConstraintAnchorConstants.Type.TOP, target,
                            ConstraintAnchorConstants.Type.TOP, 0, strength, creator);
                    connect(ConstraintAnchorConstants.Type.BOTTOM, target,
                            ConstraintAnchorConstants.Type.BOTTOM, 0, strength, creator);
                    centerY = true;
                }
                if (centerX && centerY) {
                    ConstraintAnchor center = getAnchor(ConstraintAnchorConstants.Type.CENTER);
                    center.connect(target.getAnchor(ConstraintAnchorConstants.Type.CENTER), 0, creator);
                }
                else if (centerX) {
                    ConstraintAnchor center = getAnchor(ConstraintAnchorConstants.Type.CENTER_X);
                    center.connect(target.getAnchor(ConstraintAnchorConstants.Type.CENTER_X), 0, creator);
                }
                else if (centerY) {
                    ConstraintAnchor center = getAnchor(ConstraintAnchorConstants.Type.CENTER_Y);
                    center.connect(target.getAnchor(ConstraintAnchorConstants.Type.CENTER_Y), 0, creator);
                }
            }
            else if ((constraintTo == ConstraintAnchorConstants.Type.LEFT)
                     || (constraintTo == ConstraintAnchorConstants.Type.RIGHT)) {
                connect(ConstraintAnchorConstants.Type.LEFT, target,
                        constraintTo, 0, strength, creator);
                connect(ConstraintAnchorConstants.Type.RIGHT, target,
                        constraintTo, 0, strength, creator);
                ConstraintAnchor center = getAnchor(ConstraintAnchorConstants.Type.CENTER);
                center.connect(target.getAnchor(constraintTo), 0, creator);
            }
            else if ((constraintTo == ConstraintAnchorConstants.Type.TOP)
                     || (constraintTo == ConstraintAnchorConstants.Type.BOTTOM)) {
                connect(ConstraintAnchorConstants.Type.TOP, target,
                        constraintTo, 0, strength, creator);
                connect(ConstraintAnchorConstants.Type.BOTTOM, target,
                        constraintTo, 0, strength, creator);
                ConstraintAnchor center = getAnchor(ConstraintAnchorConstants.Type.CENTER);
                center.connect(target.getAnchor(constraintTo), 0, creator);
            }
        }
        else if (constraintFrom == ConstraintAnchorConstants.Type.CENTER_X
                 && (constraintTo == ConstraintAnchorConstants.Type.LEFT
                     || constraintTo == ConstraintAnchorConstants.Type.RIGHT)) {
            ConstraintAnchor left = getAnchor(ConstraintAnchorConstants.Type.LEFT);
            ConstraintAnchor targetAnchor = target.getAnchor(constraintTo);
            ConstraintAnchor right = getAnchor(ConstraintAnchorConstants.Type.RIGHT);
            left.connect(targetAnchor, 0, creator);
            right.connect(targetAnchor, 0, creator);
            ConstraintAnchor centerX = getAnchor(ConstraintAnchorConstants.Type.CENTER_X);
            centerX.connect(targetAnchor, 0, creator);
        }
        else if (constraintFrom == ConstraintAnchorConstants.Type.CENTER_Y
                 && (constraintTo == ConstraintAnchorConstants.Type.TOP
                     || constraintTo == ConstraintAnchorConstants.Type.BOTTOM)) {
            ConstraintAnchor targetAnchor = target.getAnchor(constraintTo);
            ConstraintAnchor top = getAnchor(ConstraintAnchorConstants.Type.TOP);
            top.connect(targetAnchor, 0, creator);
            ConstraintAnchor bottom = getAnchor(ConstraintAnchorConstants.Type.BOTTOM);
            bottom.connect(targetAnchor, 0, creator);
            ConstraintAnchor centerY = getAnchor(ConstraintAnchorConstants.Type.CENTER_Y);
            centerY.connect(targetAnchor, 0, creator);
        }
        else if (constraintFrom == ConstraintAnchorConstants.Type.CENTER_X
                 && constraintTo == ConstraintAnchorConstants.Type.CENTER_X) {
            // Center X connection will connect left & right
            ConstraintAnchor left = getAnchor(ConstraintAnchorConstants.Type.LEFT);
            ConstraintAnchor leftTarget = target.getAnchor(ConstraintAnchorConstants.Type.LEFT);
            left.connect(leftTarget, 0, creator);
            ConstraintAnchor right = getAnchor(ConstraintAnchorConstants.Type.RIGHT);
            ConstraintAnchor rightTarget = target.getAnchor(ConstraintAnchorConstants.Type.RIGHT);
            right.connect(rightTarget, 0, creator);
            ConstraintAnchor centerX = getAnchor(ConstraintAnchorConstants.Type.CENTER_X);
            centerX.connect(target.getAnchor(constraintTo), 0, creator);
        }
        else if (constraintFrom == ConstraintAnchorConstants.Type.CENTER_Y
                 && constraintTo == ConstraintAnchorConstants.Type.CENTER_Y) {
            // Center Y connection will connect top & bottom.
            ConstraintAnchor top = getAnchor(ConstraintAnchorConstants.Type.TOP);
            ConstraintAnchor topTarget = target.getAnchor(ConstraintAnchorConstants.Type.TOP);
            top.connect(topTarget, 0, creator);
            ConstraintAnchor bottom = getAnchor(ConstraintAnchorConstants.Type.BOTTOM);
            ConstraintAnchor bottomTarget = target.getAnchor(ConstraintAnchorConstants.Type.BOTTOM);
            bottom.connect(bottomTarget, 0, creator);
            ConstraintAnchor centerY = getAnchor(ConstraintAnchorConstants.Type.CENTER_Y);
            centerY.connect(target.getAnchor(constraintTo), 0, creator);
        }
        else {
            ConstraintAnchor fromAnchor = getAnchor(constraintFrom);
            ConstraintAnchor toAnchor = target.getAnchor(constraintTo);
            if (fromAnchor.isValidConnection(toAnchor)) {
                // make sure that the baseline takes precedence over top/bottom
                // and reversely, reset the baseline if we are connecting top/bottom
                if (constraintFrom == ConstraintAnchorConstants.Type.BASELINE) {
                    ConstraintAnchor top = getAnchor(ConstraintAnchorConstants.Type.TOP);
                    ConstraintAnchor bottom = getAnchor(ConstraintAnchorConstants.Type.BOTTOM);
                    if (top != null) {
                        top.reset();
                    }
                    if (bottom != null) {
                        bottom.reset();
                    }
                    margin = 0;
                }
                else if ((constraintFrom == ConstraintAnchorConstants.Type.TOP)
                         || (constraintFrom == ConstraintAnchorConstants.Type.BOTTOM)) {
                    ConstraintAnchor baseline = getAnchor(ConstraintAnchorConstants.Type.BASELINE);
                    if (baseline != null) {
                        baseline.reset();
                    }
                    ConstraintAnchor center = getAnchor(ConstraintAnchorConstants.Type.CENTER);
                    if (center.getTarget() != toAnchor) {
                        center.reset();
                    }
                    ConstraintAnchor opposite = getAnchor(constraintFrom).getOpposite();
                    ConstraintAnchor centerY = getAnchor(ConstraintAnchorConstants.Type.CENTER_Y);
                    if (centerY.isConnected()) {
                        if (opposite != null) {
                          opposite.reset();
                        }
                        centerY.reset();
                    }
                }
                else if ((constraintFrom == ConstraintAnchorConstants.Type.LEFT)
                         || (constraintFrom == ConstraintAnchorConstants.Type.RIGHT)) {
                    ConstraintAnchor center = getAnchor(ConstraintAnchorConstants.Type.CENTER);
                    if (center.getTarget() != toAnchor) {
                        center.reset();
                    }
                    ConstraintAnchor opposite = getAnchor(constraintFrom).getOpposite();
                    ConstraintAnchor centerX = getAnchor(ConstraintAnchorConstants.Type.CENTER_X);
                    if (centerX.isConnected()) {
                      if (opposite != null) {
                        opposite.reset();
                      }
                      centerX.reset();
                    }
                }
                fromAnchor.connect(toAnchor, margin, strength, creator);
                toAnchor.getOwner().connectedTo(fromAnchor.getOwner());
            }
        }
    }

    /**
     * Given a type of anchor, returns the corresponding anchor.
     *
     * @param anchorType type of the anchor (LEFT, TOP, RIGHT, BOTTOM, BASELINE, CENTER_X, CENTER_Y)
     * @return the matching anchor
     */
    public ConstraintAnchor getAnchor(ConstraintAnchorConstants.Type anchorType) {
        switch (anchorType) {
            case LEFT: {
                return mLeft;
            }
            case TOP: {
                return mTop;
            }
            case RIGHT: {
                return mRight;
            }
            case BOTTOM: {
                return mBottom;
            }
            case BASELINE: {
                return mBaseline;
            }
            case CENTER_X: {
                return mCenterX;
            }
            case CENTER_Y: {
                return mCenterY;
            }
            case CENTER: {
                return mCenter;
            }
            case NONE:
                return null;
        }
        throw new AssertionError(anchorType.name());
    }
}