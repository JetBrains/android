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

import static com.android.tools.idea.uibuilder.handlers.constraint.model.ConstraintWidget.DimensionBehaviour.WRAP_CONTENT;

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
    private static final boolean AUTOTAG_CENTER = false;
    protected static final int SOLVER = 1;
    protected static final int DIRECT = 2;

    public static final int MATCH_CONSTRAINT_SPREAD = 0;
    public static final int MATCH_CONSTRAINT_WRAP = 1;
    public static final int MATCH_CONSTRAINT_PERCENT = 2;
    public static final int MATCH_CONSTRAINT_RATIO = 3;

    public static final int UNKNOWN = -1;
    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;

    public static final int VISIBLE = 0;
    public static final int INVISIBLE = 4;
    public static final int GONE = 8;

    // Values of the chain styles
    public static final int CHAIN_SPREAD = 0;
    public static final int CHAIN_SPREAD_INSIDE = 1;
    public static final int CHAIN_PACKED = 2;

    // Support for direct resolution
    public int mHorizontalResolution = UNKNOWN;
    public int mVerticalResolution = UNKNOWN;

    private static final int WRAP = -2;

    int mMatchConstraintDefaultWidth = MATCH_CONSTRAINT_SPREAD;
    int mMatchConstraintDefaultHeight = MATCH_CONSTRAINT_SPREAD;
    int mMatchConstraintMinWidth = 0;
    int mMatchConstraintMaxWidth = 0;
    float mMatchConstraintPercentWidth = 1;
    int mMatchConstraintMinHeight = 0;
    int mMatchConstraintMaxHeight = 0;
    float mMatchConstraintPercentHeight = 1;
    boolean mIsWidthWrapContent;
    boolean mIsHeightWrapContent;

    int mResolvedDimensionRatioSide = UNKNOWN;
    float mResolvedDimensionRatio = 1.0f;

    private int mMaxDimension[] = {Integer.MAX_VALUE, Integer.MAX_VALUE};
    private float mCircleConstraintAngle = 0;

    public int getMaxHeight() {
        return mMaxDimension[VERTICAL];
    }

    public int getMaxWidth() {
        return mMaxDimension[HORIZONTAL];
    }

    public void setMaxWidth(int maxWidth) {
        mMaxDimension[HORIZONTAL] = maxWidth;
    }

    public void setMaxHeight(int maxWidth) {
        mMaxDimension[VERTICAL] = maxWidth;
    }

    /**
     * Define how the content of a widget should align, if the widget has children
     */
    public enum ContentAlignment {
        BEGIN, MIDDLE, END, TOP, VERTICAL_MIDDLE, BOTTOM, LEFT, RIGHT
    }

    /**
     * Define how the widget will resize
     */
    public enum DimensionBehaviour {
        FIXED, WRAP_CONTENT, MATCH_CONSTRAINT, MATCH_PARENT
    }

    // The anchors available on the widget
    // note: all anchors should be added to the mAnchors array (see addAnchors())
    ConstraintAnchor mLeft = new ConstraintAnchor(this, ConstraintAnchor.Type.LEFT);
    ConstraintAnchor mTop = new ConstraintAnchor(this, ConstraintAnchor.Type.TOP);
    ConstraintAnchor mRight = new ConstraintAnchor(this, ConstraintAnchor.Type.RIGHT);
    ConstraintAnchor mBottom = new ConstraintAnchor(this, ConstraintAnchor.Type.BOTTOM);
    ConstraintAnchor mBaseline = new ConstraintAnchor(this, ConstraintAnchor.Type.BASELINE);
    ConstraintAnchor mCenterX = new ConstraintAnchor(this, ConstraintAnchor.Type.CENTER_X);
    ConstraintAnchor mCenterY = new ConstraintAnchor(this, ConstraintAnchor.Type.CENTER_Y);
    ConstraintAnchor mCenter = new ConstraintAnchor(this, ConstraintAnchor.Type.CENTER);

    protected static final int ANCHOR_LEFT = 0;
    protected static final int ANCHOR_RIGHT = 1;
    protected static final int ANCHOR_TOP = 2;
    protected static final int ANCHOR_BOTTOM = 3;
    protected static final int ANCHOR_BASELINE = 4;

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
    protected int mDimensionRatioSide = UNKNOWN;

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

    // Percentages used for biasing one connection over another when dual connections
    // of the same strength exist
    public static float DEFAULT_BIAS = 0.5f;
    float mHorizontalBiasPercent = DEFAULT_BIAS;
    float mVerticalBiasPercent = DEFAULT_BIAS;

    // The companion widget (typically, the real widget we represent)
    private Object mCompanionWidget;

    // This is used to possibly "skip" a position while inside a container. For example,
    // a container like ConstraintTableLayout can use this to implement empty cells
    // (the item positioned after the empty cell will have a skip value of 1)
    private int mContainerItemSkip = 0;

    // Contains the visibility status of the widget (VISIBLE, INVISIBLE, or GONE)
    private int mVisibility = VISIBLE;

    private String mDebugName = null;
    private String mType = null;

    int mDistToTop;
    int mDistToLeft;
    int mDistToRight;
    int mDistToBottom;
    boolean mLeftHasCentered;
    boolean mRightHasCentered;
    boolean mTopHasCentered;
    boolean mBottomHasCentered;
    boolean mHorizontalWrapVisited;
    boolean mVerticalWrapVisited;

    // Chain support
    int mHorizontalChainStyle = CHAIN_SPREAD;
    int mVerticalChainStyle = CHAIN_SPREAD;
    boolean mHorizontalChainFixedPosition;
    boolean mVerticalChainFixedPosition;

    float[] mWeight = {0, 0};

    protected ConstraintWidget[] mListNextMatchConstraintsWidget = {null, null};
    protected ConstraintWidget[] mListNextVisibleWidget = {null, null};

    ConstraintWidget mHorizontalNextWidget = null;
    ConstraintWidget mVerticalNextWidget = null;

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
        mCircleConstraintAngle = 0;
        mWidth = 0;
        mHeight = 0;
        mDimensionRatio = 0;
        mDimensionRatioSide = UNKNOWN;
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
        mHorizontalBiasPercent = DEFAULT_BIAS;
        mVerticalBiasPercent = DEFAULT_BIAS;
        mListDimensionBehaviors[DIMENSION_HORIZONTAL] = DimensionBehaviour.FIXED;
        mListDimensionBehaviors[DIMENSION_VERTICAL] = DimensionBehaviour.FIXED;
        mCompanionWidget = null;
        mContainerItemSkip = 0;
        mVisibility = VISIBLE;
        mDebugName = null;
        mType = null;
        mHorizontalWrapVisited = false;
        mVerticalWrapVisited = false;
        mHorizontalChainStyle = CHAIN_SPREAD;
        mVerticalChainStyle = CHAIN_SPREAD;
        mHorizontalChainFixedPosition = false;
        mVerticalChainFixedPosition = false;
        mWeight[DIMENSION_HORIZONTAL] = 0;
        mWeight[DIMENSION_VERTICAL] = 0;
        mHorizontalResolution = UNKNOWN;
        mVerticalResolution = UNKNOWN;
        mMaxDimension[HORIZONTAL] = Integer.MAX_VALUE;
        mMaxDimension[VERTICAL] = Integer.MAX_VALUE;
        mMatchConstraintDefaultWidth = MATCH_CONSTRAINT_SPREAD;
        mMatchConstraintDefaultHeight = MATCH_CONSTRAINT_SPREAD;
        mMatchConstraintPercentWidth = 1;
        mMatchConstraintPercentHeight = 1;
        mMatchConstraintMaxWidth = Integer.MAX_VALUE;
        mMatchConstraintMaxHeight = Integer.MAX_VALUE;
        mMatchConstraintMinWidth = 0;
        mMatchConstraintMinHeight = 0;
        mResolvedDimensionRatioSide = UNKNOWN;
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
     * Constructor
     *
     * @param x      x position
     * @param y      y position
     * @param width  width of the layout
     * @param height height of the layout
     */
    public ConstraintWidget(int x, int y, int width, int height) {
        mX = x;
        mY = y;
        mWidth = width;
        mHeight = height;
        addAnchors();
        forceUpdateDrawPosition();
    }

    /**
     * Constructor
     *
     * @param width  width of the layout
     * @param height height of the layout
     */
    public ConstraintWidget(int width, int height) {
        this(0, 0, width, height);
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
     * Returns true if the widget is a root container in the hierarchy,
     * or the root widget itself
     *
     * @return true if root container
     */
    public boolean isRootContainer() {
        return (this instanceof ConstraintWidgetContainer)
               && (mParent == null || !(mParent instanceof ConstraintWidgetContainer));
    }

    /**
     * Returns true if the widget is contained in a ConstraintLayout
     *
     * @return
     */
    public boolean isInsideConstraintLayout() {
        ConstraintWidget widget = getParent();
        if (widget == null) {
            return false;
        }
        while (widget != null) {
            if (widget instanceof ConstraintWidgetContainer) {
                return true;
            }
            widget = widget.getParent();
        }
        return false;
    }

    /**
     * Return true if the widget is one of our ancestor
     *
     * @param widget widget we want to check
     * @return true if the given widget is one of our ancestor, false otherwise
     */
    public boolean hasAncestor(ConstraintWidget widget) {
        ConstraintWidget parent = getParent();
        if (parent == widget) {
            return true;
        }
        if (parent == widget.getParent()) {
            return false; // the widget is one of our sibling
        }
        while (parent != null) {
            if (parent == widget) {
                return true;
            }
            if (parent == widget.getParent()) {
                // the widget is a sibling of one of our ancestor
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    /**
     * Returns the top-level container, regardless if it's a ConstraintWidgetContainer
     * or a simple WidgetContainer.
     *
     * @return top-level WidgetContainer
     */
    public WidgetContainer getRootWidgetContainer() {
        ConstraintWidget root = this;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        if (root instanceof WidgetContainer) {
            return (WidgetContainer)root;
        }
        return null;
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
     * Keep track of wrap_content for width
     *
     * @param widthWrapContent
     */
    public void setWidthWrapContent(boolean widthWrapContent) {
        this.mIsWidthWrapContent = widthWrapContent;
    }

    /**
     * Returns true if width is set to wrap_content
     *
     * @return
     */
    public boolean isWidthWrapContent() {
        return mIsWidthWrapContent;
    }

    /**
     * Keep track of wrap_content for height
     *
     * @param heightWrapContent
     */
    public void setHeightWrapContent(boolean heightWrapContent) {
        this.mIsHeightWrapContent = heightWrapContent;
    }

    /**
     * Returns true if height is set to wrap_content
     *
     * @return
     */
    public boolean isHeightWrapContent() {
        return mIsHeightWrapContent;
    }

    /**
     * Set a circular constraint
     *
     * @param target the target widget we will use as the center of the circle
     * @param angle  the angle (from 0 to 360)
     * @param radius the radius used
     */
    public void connectCircularConstraint(ConstraintWidget target, float angle, int radius) {
        immediateConnect(ConstraintAnchor.Type.CENTER, target, ConstraintAnchor.Type.CENTER,
                         radius, 0);
        mCircleConstraintAngle = angle;
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

    int getInternalDrawX() {
        return mDrawX;
    }

    int getInternalDrawY() {
        return mDrawY;
    }

    public int getInternalDrawRight() {
        return mDrawX + mDrawWidth;
    }

    public int getInternalDrawBottom() {
        return mDrawY + mDrawHeight;
    }


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
        if (mVisibility == ConstraintWidget.GONE) {
            return 0;
        }
        return mWidth;
    }

    public int getOptimizerWrapWidth() {
        int w = mWidth;
        if (mListDimensionBehaviors[DIMENSION_HORIZONTAL] == DimensionBehaviour.MATCH_CONSTRAINT) {
            if (mMatchConstraintDefaultWidth == MATCH_CONSTRAINT_WRAP) {
                w = Math.max(mMatchConstraintMinWidth, w);
            }
            else if (mMatchConstraintMinWidth > 0) {
                w = mMatchConstraintMinWidth;
                mWidth = w;
            }
            else {
                w = 0;
            }
            if (mMatchConstraintMaxWidth > 0 && mMatchConstraintMaxWidth < w) {
                w = mMatchConstraintMaxWidth;
            }
        }
        return w;
    }

    public int getOptimizerWrapHeight() {
        int h = mHeight;
        if (mListDimensionBehaviors[DIMENSION_VERTICAL] == DimensionBehaviour.MATCH_CONSTRAINT) {
            if (mMatchConstraintDefaultHeight == MATCH_CONSTRAINT_WRAP) {
                h = Math.max(mMatchConstraintMinHeight, h);
            }
            else if (mMatchConstraintMinHeight > 0) {
                h = mMatchConstraintMinHeight;
                mHeight = h;
            }
            else {
                h = 0;
            }
            if (mMatchConstraintMaxHeight > 0 && mMatchConstraintMaxHeight < h) {
                h = mMatchConstraintMaxHeight;
            }
        }
        return h;
    }

    /**
     * Return the wrap width of the widget
     *
     * @return the wrap width
     */
    public int getWrapWidth() {
        return mWrapWidth;
    }

    /**
     * Return the height of the widget
     *
     * @return height height
     */
    public int getHeight() {
        if (mVisibility == ConstraintWidget.GONE) {
            return 0;
        }
        return mHeight;
    }

    /**
     * Return the wrap height of the widget
     *
     * @return the wrap height
     */
    public int getWrapHeight() {
        return mWrapHeight;
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
     * Return the bottom position of the widget, relative to the root
     *
     * @return bottom position of the widget
     */
    public int getDrawBottom() {
        return getDrawY() + mDrawHeight;
    }

    /**
     * Return the right position of the widget, relative to the root
     *
     * @return right position of the widget
     */
    public int getDrawRight() {
        return getDrawX() + mDrawWidth;
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
     * Return the horizontal percentage bias that is used when two opposite connections
     * exist of the same strengh.
     *
     * @return horizontal percentage bias
     */
    public float getHorizontalBiasPercent() {
        return mHorizontalBiasPercent;
    }

    /**
     * Return the vertical percentage bias that is used when two opposite connections
     * exist of the same strengh.
     *
     * @return vertical percentage bias
     */
    public float getVerticalBiasPercent() {
        return mVerticalBiasPercent;
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
     * Return the baseline distance relative to the top of the widget
     *
     * @return baseline
     */
    public int getBaselineDistance() {
        return mBaselineDistance;
    }

    /**
     * Return the companion widget. Typically, this would be the real
     * widget we represent with this instance of ConstraintWidget.
     *
     * @return the companion widget, if set.
     */
    public Object getCompanionWidget() {
        return mCompanionWidget;
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
     * Set both the origin in (x, y) of the widget, relative to its container
     *
     * @param x x position
     * @param y y position
     */
    public void setOrigin(int x, int y) {
        mX = x;
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
     * Set the margin to be used when connected to a widget with a visibility of GONE
     *
     * @param type       the anchor to set the margin on
     * @param goneMargin the margin value to use
     */
    public void setGoneMargin(ConstraintAnchor.Type type, int goneMargin) {
        switch (type) {
            case LEFT: {
                mLeft.mGoneMargin = goneMargin;
            }
            break;
            case TOP: {
                mTop.mGoneMargin = goneMargin;
            }
            break;
            case RIGHT: {
                mRight.mGoneMargin = goneMargin;
            }
            break;
            case BOTTOM: {
                mBottom.mGoneMargin = goneMargin;
            }
            break;
            case BASELINE:
            case CENTER:
            case CENTER_X:
            case CENTER_Y:
            case NONE:
                break;
        }
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
     * Update the draw positition immediately to match the true position
     */
    public void forceUpdateDrawPosition() {
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
     * Set both the origin in (x, y) of the widget, relative to the root
     *
     * @param x x position
     * @param y y position
     */
    public void setDrawOrigin(int x, int y) {
        mDrawX = x - mOffsetX;
        mDrawY = y - mOffsetY;
        mX = mDrawX;
        mY = mDrawY;
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
     * Set the horizontal style when MATCH_CONSTRAINT is set
     *
     * @param horizontalMatchStyle MATCH_CONSTRAINT_SPREAD or MATCH_CONSTRAINT_WRAP
     * @param min                  minimum value
     * @param max                  maximum value
     * @param percent
     */
    public void setHorizontalMatchStyle(int horizontalMatchStyle, int min, int max, float percent) {
        mMatchConstraintDefaultWidth = horizontalMatchStyle;
        mMatchConstraintMinWidth = min;
        mMatchConstraintMaxWidth = max;
        mMatchConstraintPercentWidth = percent;
        if (percent < 1 && mMatchConstraintDefaultWidth == MATCH_CONSTRAINT_SPREAD) {
            mMatchConstraintDefaultWidth = MATCH_CONSTRAINT_PERCENT;
        }
    }

    /**
     * Set the vertical style when MATCH_CONSTRAINT is set
     *
     * @param verticalMatchStyle MATCH_CONSTRAINT_SPREAD or MATCH_CONSTRAINT_WRAP
     * @param min                minimum value
     * @param max                maximum value
     * @param percent
     */
    public void setVerticalMatchStyle(int verticalMatchStyle, int min, int max, float percent) {
        mMatchConstraintDefaultHeight = verticalMatchStyle;
        mMatchConstraintMinHeight = min;
        mMatchConstraintMaxHeight = max;
        mMatchConstraintPercentHeight = percent;
        if (percent < 1 && mMatchConstraintDefaultHeight == MATCH_CONSTRAINT_SPREAD) {
            mMatchConstraintDefaultHeight = MATCH_CONSTRAINT_PERCENT;
        }
    }

    /**
     * Set the ratio of the widget from a given string of format [H|V],[float|x:y] or [float|x:y]
     *
     * @param ratio
     */
    public void setDimensionRatio(String ratio) {
        if (ratio == null || ratio.length() == 0) {
            mDimensionRatio = 0;
            return;
        }
        int dimensionRatioSide = UNKNOWN;
        float dimensionRatio = 0;
        int len = ratio.length();
        int commaIndex = ratio.indexOf(',');
        if (commaIndex > 0 && commaIndex < len - 1) {
            String dimension = ratio.substring(0, commaIndex);
            if (dimension.equalsIgnoreCase("W")) {
                dimensionRatioSide = HORIZONTAL;
            }
            else if (dimension.equalsIgnoreCase("H")) {
                dimensionRatioSide = VERTICAL;
            }
            commaIndex++;
        }
        else {
            commaIndex = 0;
        }
        int colonIndex = ratio.indexOf(':');

        if (colonIndex >= 0 && colonIndex < len - 1) {
            String nominator = ratio.substring(commaIndex, colonIndex);
            String denominator = ratio.substring(colonIndex + 1);
            if (nominator.length() > 0 && denominator.length() > 0) {
                try {
                    float nominatorValue = Float.parseFloat(nominator);
                    float denominatorValue = Float.parseFloat(denominator);
                    if (nominatorValue > 0 && denominatorValue > 0) {
                        if (dimensionRatioSide == VERTICAL) {
                            dimensionRatio = Math.abs(denominatorValue / nominatorValue);
                        }
                        else {
                            dimensionRatio = Math.abs(nominatorValue / denominatorValue);
                        }
                    }
                }
                catch (NumberFormatException e) {
                    // Ignore
                }
            }
        }
        else {
            String r = ratio.substring(commaIndex);
            if (r.length() > 0) {
                try {
                    dimensionRatio = Float.parseFloat(r);
                }
                catch (NumberFormatException e) {
                    // Ignore
                }
            }
        }

        if (dimensionRatio > 0) {
            mDimensionRatio = dimensionRatio;
            mDimensionRatioSide = dimensionRatioSide;
        }
    }

    /**
     * Set the ratio of the widget
     * The ratio will be applied if at least one of the dimension (width or height) is set to a behaviour
     * of DimensionBehaviour.MATCH_CONSTRAINT -- the dimension's value will be set to the other dimension * ratio.
     */
    public void setDimensionRatio(float ratio, int dimensionRatioSide) {
        mDimensionRatio = ratio;
        mDimensionRatioSide = dimensionRatioSide;
    }

    /**
     * Return the current ratio of this widget
     *
     * @return the dimension ratio
     */
    public float getDimensionRatio() {
        return mDimensionRatio;
    }

    /**
     * Return the current side on which ratio will be applied
     *
     * @return HORIZONTAL, VERTICAL, or UNKNOWN
     */
    public int getDimensionRatioSide() {
        return mDimensionRatioSide;
    }

    /**
     * Set the horizontal bias percent to apply when we have two opposite constraints of
     * equal strength
     *
     * @param horizontalBiasPercent the percentage used
     */
    public void setHorizontalBiasPercent(float horizontalBiasPercent) {
        mHorizontalBiasPercent = horizontalBiasPercent;
    }

    /**
     * Set the vertical bias percent to apply when we have two opposite constraints of
     * equal strength
     *
     * @param verticalBiasPercent the percentage used
     */
    public void setVerticalBiasPercent(float verticalBiasPercent) {
        mVerticalBiasPercent = verticalBiasPercent;
    }

    /**
     * Set the minimum width of the widget
     *
     * @param w minimum width
     */
    public void setMinWidth(int w) {
        if (w < 0) {
            mMinWidth = 0;
        }
        else {
            mMinWidth = w;
        }
    }

    /**
     * Set the minimum height of the widget
     *
     * @param h minimum height
     */
    public void setMinHeight(int h) {
        if (h < 0) {
            mMinHeight = 0;
        }
        else {
            mMinHeight = h;
        }
    }

    /**
     * Set the wrap content width of the widget
     *
     * @param w wrap content width
     */
    public void setWrapWidth(int w) {
        mWrapWidth = w;
    }

    /**
     * Set the wrap content height of the widget
     *
     * @param h wrap content height
     */
    public void setWrapHeight(int h) {
        mWrapHeight = h;
    }

    /**
     * Set both width and height of the widget
     *
     * @param w width
     * @param h height
     */
    public void setDimension(int w, int h) {
        mWidth = w;
        if (mWidth < mMinWidth) {
            mWidth = mMinWidth;
        }
        mHeight = h;
        if (mHeight < mMinHeight) {
            mHeight = mMinHeight;
        }
    }

    /**
     * Set the position+dimension of the widget given left/top/right/bottom
     *
     * @param left   left side position of the widget
     * @param top    top side position of the widget
     * @param right  right side position of the widget
     * @param bottom bottom side position of the widget
     */
    public void setFrame(int left, int top, int right, int bottom) {
        int w = right - left;
        int h = bottom - top;

        mX = left;
        mY = top;

        if (mVisibility == ConstraintWidget.GONE) {
            mWidth = 0;
            mHeight = 0;
            return;
        }

        // correct dimensional instability caused by rounding errors
        if (mListDimensionBehaviors[DIMENSION_HORIZONTAL] == DimensionBehaviour.FIXED && w < mWidth) {
            w = mWidth;
        }
        if (mListDimensionBehaviors[DIMENSION_VERTICAL] == DimensionBehaviour.FIXED && h < mHeight) {
            h = mHeight;
        }

        mWidth = w;
        mHeight = h;

        if (mHeight < mMinHeight) {
            mHeight = mMinHeight;
        }
        if (mWidth < mMinWidth) {
            mWidth = mMinWidth;
        }
    }

    /**
     * Set the positions for the horizontal dimension only
     *
     * @param left
     * @param right
     */
    public void setHorizontalDimension(int left, int right) {
        mX = left;
        mWidth = right - left;
        if (mWidth < mMinWidth) {
            mWidth = mMinWidth;
        }
    }

    /**
     * Set the positions for the vertical dimension only
     *
     * @param top
     * @param bottom
     */
    public void setVerticalDimension(int top, int bottom) {
        mY = top;
        mHeight = bottom - top;
        if (mHeight < mMinHeight) {
            mHeight = mMinHeight;
        }
    }

    /**
     * Set the baseline distance relative to the top of the widget
     *
     * @param baseline the distance of the baseline relative to the widget's top
     */
    public void setBaselineDistance(int baseline) {
        mBaselineDistance = baseline;
    }

    /**
     * Set the companion widget. Typically, this would be the real widget we
     * represent with this instance of ConstraintWidget.
     *
     * @param companion
     */
    public void setCompanionWidget(Object companion) {
        mCompanionWidget = companion;
    }

    /**
     * Set the skip value for this widget. This can be used when a widget is in a container,
     * so that container can position the widget as if it was positioned further in the list
     * of widgets. For example, with ConstraintTableLayout, this is used to skip empty cells
     * (the widget after an empty cell will have a skip value of one)
     *
     * @param skip
     */
    public void setContainerItemSkip(int skip) {
        if (skip >= 0) {
            mContainerItemSkip = skip;
        }
        else {
            mContainerItemSkip = 0;
        }
    }

    /**
     * Accessor for the skip value
     *
     * @return skip value
     */
    public int getContainerItemSkip() {
        return mContainerItemSkip;
    }

    /**
     * Set the horizontal weight (only used in chains)
     *
     * @param horizontalWeight
     */
    public void setHorizontalWeight(float horizontalWeight) {
        mWeight[DIMENSION_HORIZONTAL] = horizontalWeight;
    }

    /**
     * Set the vertical weight (only used in chains)
     *
     * @param verticalWeight
     */
    public void setVerticalWeight(float verticalWeight) {
        mWeight[DIMENSION_VERTICAL] = verticalWeight;
    }

    /**
     * Set the chain starting from this widget to be packed.
     * The horizontal bias will control how elements of the chain are positioned.
     *
     * @param horizontalChainStyle
     */
    public void setHorizontalChainStyle(int horizontalChainStyle) {
        mHorizontalChainStyle = horizontalChainStyle;
    }

    /**
     * get the chain starting from this widget to be packed.
     * The horizontal bias will control how elements of the chain are positioned.
     *
     * @return Horizontal Chain Style
     */
    public int getHorizontalChainStyle() {
        return mHorizontalChainStyle;
    }

    /**
     * Set the chain starting from this widget to be packed.
     * The vertical bias will control how elements of the chain are positioned.
     *
     * @param verticalChainStyle
     */
    public void setVerticalChainStyle(int verticalChainStyle) {
        mVerticalChainStyle = verticalChainStyle;
    }

    /**
     * Set the chain starting from this widget to be packed.
     * The vertical bias will control how elements of the chain are positioned.
     *
     * @return
     */
    public int getVerticalChainStyle() {
        return mVerticalChainStyle;
    }

    /*-----------------------------------------------------------------------*/
    // Connections
    /*-----------------------------------------------------------------------*/

    /**
     * Callback when a widget connects to us
     *
     * @param source
     */
    public void connectedTo(ConstraintWidget source) {
        // do nothing by default
    }

    /**
     * Immediate connection to an anchor without any checks.
     *
     * @param startType
     * @param target
     * @param endType
     * @param margin
     * @param goneMargin
     */
    public void immediateConnect(ConstraintAnchor.Type startType, ConstraintWidget target,
                                 ConstraintAnchor.Type endType, int margin, int goneMargin) {
        ConstraintAnchor startAnchor = getAnchor(startType);
        ConstraintAnchor endAnchor = target.getAnchor(endType);
        startAnchor.connect(endAnchor, margin, goneMargin, ConstraintAnchor.Strength.STRONG,
                            ConstraintAnchor.USER_CREATOR, true);
    }

    /**
     * Connect the given anchors together (the from anchor should be owned by this widget)
     *
     * @param from    the anchor we are connecting from (of this widget)
     * @param to      the anchor we are connecting to
     * @param margin  how much margin we want to have
     * @param creator who created the connection
     */
    public void connect(ConstraintAnchor from, ConstraintAnchor to, int margin, int creator) {
        connect(from, to, margin, ConstraintAnchor.Strength.STRONG, creator);
    }

    public void connect(ConstraintAnchor from, ConstraintAnchor to, int margin) {
        connect(from, to, margin, ConstraintAnchor.Strength.STRONG, ConstraintAnchor.USER_CREATOR);
    }

    public void connect(ConstraintAnchor from, ConstraintAnchor to, int margin,
                        ConstraintAnchor.Strength strength, int creator) {
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
    public void connect(ConstraintAnchor.Type constraintFrom, ConstraintWidget target,
                        ConstraintAnchor.Type constraintTo, int margin) {
        connect(constraintFrom, target, constraintTo, margin,
                ConstraintAnchor.Strength.STRONG);
    }

    /**
     * Connect a given anchor of this widget to another anchor of a target widget
     *
     * @param constraintFrom which anchor of this widget to connect from
     * @param target         the target widget
     * @param constraintTo   the target anchor on the target widget
     * @return the undo operation
     */
    public void connect(ConstraintAnchor.Type constraintFrom,
                        ConstraintWidget target,
                        ConstraintAnchor.Type constraintTo) {
        connect(constraintFrom, target, constraintTo, 0, ConstraintAnchor.Strength.STRONG);
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
    public void connect(ConstraintAnchor.Type constraintFrom,
                        ConstraintWidget target,
                        ConstraintAnchor.Type constraintTo, int margin,
                        ConstraintAnchor.Strength strength) {
        connect(constraintFrom, target, constraintTo, margin, strength,
                ConstraintAnchor.USER_CREATOR);
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
    public void connect(ConstraintAnchor.Type constraintFrom,
                        ConstraintWidget target,
                        ConstraintAnchor.Type constraintTo, int margin,
                        ConstraintAnchor.Strength strength, int creator) {
        if (constraintFrom == ConstraintAnchor.Type.CENTER) {
            // If we have center, we connect instead to the corresponding
            // left/right or top/bottom pairs
            if (constraintTo == ConstraintAnchor.Type.CENTER) {
                ConstraintAnchor left = getAnchor(ConstraintAnchor.Type.LEFT);
                ConstraintAnchor right = getAnchor(ConstraintAnchor.Type.RIGHT);
                ConstraintAnchor top = getAnchor(ConstraintAnchor.Type.TOP);
                ConstraintAnchor bottom = getAnchor(ConstraintAnchor.Type.BOTTOM);
                boolean centerX = false;
                boolean centerY = false;
                if ((left != null && left.isConnected())
                    || (right != null && right.isConnected())) {
                    // don't apply center here
                }
                else {
                    connect(ConstraintAnchor.Type.LEFT, target,
                            ConstraintAnchor.Type.LEFT, 0, strength, creator);
                    connect(ConstraintAnchor.Type.RIGHT, target,
                            ConstraintAnchor.Type.RIGHT, 0, strength, creator);
                    centerX = true;
                }
                if ((top != null && top.isConnected())
                    || (bottom != null && bottom.isConnected())) {
                    // don't apply center here
                }
                else {
                    connect(ConstraintAnchor.Type.TOP, target,
                            ConstraintAnchor.Type.TOP, 0, strength, creator);
                    connect(ConstraintAnchor.Type.BOTTOM, target,
                            ConstraintAnchor.Type.BOTTOM, 0, strength, creator);
                    centerY = true;
                }
                if (centerX && centerY) {
                    ConstraintAnchor center = getAnchor(ConstraintAnchor.Type.CENTER);
                    center.connect(target.getAnchor(ConstraintAnchor.Type.CENTER), 0, creator);
                }
                else if (centerX) {
                    ConstraintAnchor center = getAnchor(ConstraintAnchor.Type.CENTER_X);
                    center.connect(target.getAnchor(ConstraintAnchor.Type.CENTER_X), 0, creator);
                }
                else if (centerY) {
                    ConstraintAnchor center = getAnchor(ConstraintAnchor.Type.CENTER_Y);
                    center.connect(target.getAnchor(ConstraintAnchor.Type.CENTER_Y), 0, creator);
                }
            }
            else if ((constraintTo == ConstraintAnchor.Type.LEFT)
                     || (constraintTo == ConstraintAnchor.Type.RIGHT)) {
                connect(ConstraintAnchor.Type.LEFT, target,
                        constraintTo, 0, strength, creator);
                connect(ConstraintAnchor.Type.RIGHT, target,
                        constraintTo, 0, strength, creator);
                ConstraintAnchor center = getAnchor(ConstraintAnchor.Type.CENTER);
                center.connect(target.getAnchor(constraintTo), 0, creator);
            }
            else if ((constraintTo == ConstraintAnchor.Type.TOP)
                     || (constraintTo == ConstraintAnchor.Type.BOTTOM)) {
                connect(ConstraintAnchor.Type.TOP, target,
                        constraintTo, 0, strength, creator);
                connect(ConstraintAnchor.Type.BOTTOM, target,
                        constraintTo, 0, strength, creator);
                ConstraintAnchor center = getAnchor(ConstraintAnchor.Type.CENTER);
                center.connect(target.getAnchor(constraintTo), 0, creator);
            }
        }
        else if (constraintFrom == ConstraintAnchor.Type.CENTER_X
                 && (constraintTo == ConstraintAnchor.Type.LEFT
                     || constraintTo == ConstraintAnchor.Type.RIGHT)) {
            ConstraintAnchor left = getAnchor(ConstraintAnchor.Type.LEFT);
            ConstraintAnchor targetAnchor = target.getAnchor(constraintTo);
            ConstraintAnchor right = getAnchor(ConstraintAnchor.Type.RIGHT);
            left.connect(targetAnchor, 0, creator);
            right.connect(targetAnchor, 0, creator);
            ConstraintAnchor centerX = getAnchor(ConstraintAnchor.Type.CENTER_X);
            centerX.connect(targetAnchor, 0, creator);
        }
        else if (constraintFrom == ConstraintAnchor.Type.CENTER_Y
                 && (constraintTo == ConstraintAnchor.Type.TOP
                     || constraintTo == ConstraintAnchor.Type.BOTTOM)) {
            ConstraintAnchor targetAnchor = target.getAnchor(constraintTo);
            ConstraintAnchor top = getAnchor(ConstraintAnchor.Type.TOP);
            top.connect(targetAnchor, 0, creator);
            ConstraintAnchor bottom = getAnchor(ConstraintAnchor.Type.BOTTOM);
            bottom.connect(targetAnchor, 0, creator);
            ConstraintAnchor centerY = getAnchor(ConstraintAnchor.Type.CENTER_Y);
            centerY.connect(targetAnchor, 0, creator);
        }
        else if (constraintFrom == ConstraintAnchor.Type.CENTER_X
                 && constraintTo == ConstraintAnchor.Type.CENTER_X) {
            // Center X connection will connect left & right
            ConstraintAnchor left = getAnchor(ConstraintAnchor.Type.LEFT);
            ConstraintAnchor leftTarget = target.getAnchor(ConstraintAnchor.Type.LEFT);
            left.connect(leftTarget, 0, creator);
            ConstraintAnchor right = getAnchor(ConstraintAnchor.Type.RIGHT);
            ConstraintAnchor rightTarget = target.getAnchor(ConstraintAnchor.Type.RIGHT);
            right.connect(rightTarget, 0, creator);
            ConstraintAnchor centerX = getAnchor(ConstraintAnchor.Type.CENTER_X);
            centerX.connect(target.getAnchor(constraintTo), 0, creator);
        }
        else if (constraintFrom == ConstraintAnchor.Type.CENTER_Y
                 && constraintTo == ConstraintAnchor.Type.CENTER_Y) {
            // Center Y connection will connect top & bottom.
            ConstraintAnchor top = getAnchor(ConstraintAnchor.Type.TOP);
            ConstraintAnchor topTarget = target.getAnchor(ConstraintAnchor.Type.TOP);
            top.connect(topTarget, 0, creator);
            ConstraintAnchor bottom = getAnchor(ConstraintAnchor.Type.BOTTOM);
            ConstraintAnchor bottomTarget = target.getAnchor(ConstraintAnchor.Type.BOTTOM);
            bottom.connect(bottomTarget, 0, creator);
            ConstraintAnchor centerY = getAnchor(ConstraintAnchor.Type.CENTER_Y);
            centerY.connect(target.getAnchor(constraintTo), 0, creator);
        }
        else {
            ConstraintAnchor fromAnchor = getAnchor(constraintFrom);
            ConstraintAnchor toAnchor = target.getAnchor(constraintTo);
            if (fromAnchor.isValidConnection(toAnchor)) {
                // make sure that the baseline takes precedence over top/bottom
                // and reversely, reset the baseline if we are connecting top/bottom
                if (constraintFrom == ConstraintAnchor.Type.BASELINE) {
                    ConstraintAnchor top = getAnchor(ConstraintAnchor.Type.TOP);
                    ConstraintAnchor bottom = getAnchor(ConstraintAnchor.Type.BOTTOM);
                    if (top != null) {
                        top.reset();
                    }
                    if (bottom != null) {
                        bottom.reset();
                    }
                    margin = 0;
                }
                else if ((constraintFrom == ConstraintAnchor.Type.TOP)
                         || (constraintFrom == ConstraintAnchor.Type.BOTTOM)) {
                    ConstraintAnchor baseline = getAnchor(ConstraintAnchor.Type.BASELINE);
                    if (baseline != null) {
                        baseline.reset();
                    }
                    ConstraintAnchor center = getAnchor(ConstraintAnchor.Type.CENTER);
                    if (center.getTarget() != toAnchor) {
                        center.reset();
                    }
                    ConstraintAnchor opposite = getAnchor(constraintFrom).getOpposite();
                    ConstraintAnchor centerY = getAnchor(ConstraintAnchor.Type.CENTER_Y);
                    if (centerY.isConnected()) {
                        opposite.reset();
                        centerY.reset();
                    }
                    else {
                        if (AUTOTAG_CENTER) {
                            // let's see if we need to mark center_y as connected
                            if (opposite.isConnected() && opposite.getTarget().getOwner()
                                                          == toAnchor.getOwner()) {
                                ConstraintAnchor targetCenterY = toAnchor.getOwner().getAnchor(
                                  ConstraintAnchor.Type.CENTER_Y);
                                centerY.connect(targetCenterY, 0, creator);
                            }
                        }
                    }
                }
                else if ((constraintFrom == ConstraintAnchor.Type.LEFT)
                         || (constraintFrom == ConstraintAnchor.Type.RIGHT)) {
                    ConstraintAnchor center = getAnchor(ConstraintAnchor.Type.CENTER);
                    if (center.getTarget() != toAnchor) {
                        center.reset();
                    }
                    ConstraintAnchor opposite = getAnchor(constraintFrom).getOpposite();
                    ConstraintAnchor centerX = getAnchor(ConstraintAnchor.Type.CENTER_X);
                    if (centerX.isConnected()) {
                        opposite.reset();
                        centerX.reset();
                    }
                    else {
                        if (AUTOTAG_CENTER) {
                            // let's see if we need to mark center_x as connected
                            if (opposite.isConnected() && opposite.getTarget().getOwner()
                                                          == toAnchor.getOwner()) {
                                ConstraintAnchor targetCenterX = toAnchor.getOwner().getAnchor(
                                  ConstraintAnchor.Type.CENTER_X);
                                centerX.connect(targetCenterX, 0, creator);
                            }
                        }
                    }
                }
                fromAnchor.connect(toAnchor, margin, strength, creator);
                toAnchor.getOwner().connectedTo(fromAnchor.getOwner());
            }
        }
    }

    /**
     * Reset all the constraints set on this widget
     */
    public void resetAllConstraints() {
        resetAnchors();
        setVerticalBiasPercent(DEFAULT_BIAS);
        setHorizontalBiasPercent(DEFAULT_BIAS);
        if (this instanceof ConstraintWidgetContainer) {
            return;
        }
        if (getHorizontalDimensionBehaviour() == ConstraintWidget.DimensionBehaviour.MATCH_CONSTRAINT) {
            if (getWidth() == getWrapWidth()) {
                setHorizontalDimensionBehaviour(WRAP_CONTENT);
            }
            else if (getWidth() > getMinWidth()) {
                setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.FIXED);
            }
        }
        if (getVerticalDimensionBehaviour() == ConstraintWidget.DimensionBehaviour.MATCH_CONSTRAINT) {
            if (getHeight() == getWrapHeight()) {
                setVerticalDimensionBehaviour(WRAP_CONTENT);
            }
            else if (getHeight() > getMinHeight()) {
                setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.FIXED);
            }
        }
    }

    /**
     * Reset the given anchor
     *
     * @param anchor the anchor we want to reset
     * @return the undo operation
     */
    public void resetAnchor(ConstraintAnchor anchor) {
        if (getParent() != null) {
            if (getParent() instanceof ConstraintWidgetContainer) {
                ConstraintWidgetContainer parent = (ConstraintWidgetContainer)getParent();
                if (parent.handlesInternalConstraints()) {
                    return;
                }
            }
        }
        ConstraintAnchor left = getAnchor(ConstraintAnchor.Type.LEFT);
        ConstraintAnchor right = getAnchor(ConstraintAnchor.Type.RIGHT);
        ConstraintAnchor top = getAnchor(ConstraintAnchor.Type.TOP);
        ConstraintAnchor bottom = getAnchor(ConstraintAnchor.Type.BOTTOM);
        ConstraintAnchor center = getAnchor(ConstraintAnchor.Type.CENTER);
        ConstraintAnchor centerX = getAnchor(ConstraintAnchor.Type.CENTER_X);
        ConstraintAnchor centerY = getAnchor(ConstraintAnchor.Type.CENTER_Y);

        if (anchor == center) {
            if (left.isConnected() && right.isConnected()
                && left.getTarget() == right.getTarget()) {
                left.reset();
                right.reset();
            }
            if (top.isConnected() && bottom.isConnected()
                && top.getTarget() == bottom.getTarget()) {
                top.reset();
                bottom.reset();
            }
            mHorizontalBiasPercent = 0.5f;
            mVerticalBiasPercent = 0.5f;
        }
        else if (anchor == centerX) {
            if (left.isConnected() && right.isConnected()
                && left.getTarget().getOwner() == right.getTarget().getOwner()) {
                left.reset();
                right.reset();
            }
            mHorizontalBiasPercent = 0.5f;
        }
        else if (anchor == centerY) {
            if (top.isConnected() && bottom.isConnected()
                && top.getTarget().getOwner() == bottom.getTarget().getOwner()) {
                top.reset();
                bottom.reset();
            }
            mVerticalBiasPercent = 0.5f;
        }
        else if (anchor == left || anchor == right) {
            if (left.isConnected() && left.getTarget() == right.getTarget()) {
                center.reset();
            }
        }
        else if (anchor == top || anchor == bottom) {
            if (top.isConnected() && top.getTarget() == bottom.getTarget()) {
                center.reset();
            }
        }
        anchor.reset();
    }

    /**
     * Reset all connections
     */
    public void resetAnchors() {
        ConstraintWidget parent = getParent();
        if (parent != null && parent instanceof ConstraintWidgetContainer) {
            ConstraintWidgetContainer parentContainer = (ConstraintWidgetContainer)getParent();
            if (parentContainer.handlesInternalConstraints()) {
                return;
            }
        }
        for (int i = 0, mAnchorsSize = mAnchors.size(); i < mAnchorsSize; i++) {
            final ConstraintAnchor anchor = mAnchors.get(i);
            anchor.reset();
        }
    }

    /**
     * Reset all connections that have this connectCreator
     */
    public void resetAnchors(int connectionCreator) {
        ConstraintWidget parent = getParent();
        if (parent != null && parent instanceof ConstraintWidgetContainer) {
            ConstraintWidgetContainer parentContainer = (ConstraintWidgetContainer)getParent();
            if (parentContainer.handlesInternalConstraints()) {
                return;
            }
        }
        for (int i = 0, mAnchorsSize = mAnchors.size(); i < mAnchorsSize; i++) {
            final ConstraintAnchor anchor = mAnchors.get(i);
            if (connectionCreator == anchor.getConnectionCreator()) {
                if (anchor.isVerticalAnchor()) {
                    setVerticalBiasPercent(ConstraintWidget.DEFAULT_BIAS);
                }
                else {
                    setHorizontalBiasPercent(ConstraintWidget.DEFAULT_BIAS);
                }
                anchor.reset();
            }
        }
    }

    /**
     * Disconnect this widget if we have a connection to it
     *
     * @param widget the widget we are removing
     */
    public void disconnectWidget(ConstraintWidget widget) {
        final ArrayList<ConstraintAnchor> anchors = getAnchors();
        for (int i = 0, anchorsSize = anchors.size(); i < anchorsSize; i++) {
            final ConstraintAnchor anchor = anchors.get(i);
            if (anchor.isConnected() && (anchor.getTarget().getOwner() == widget)) {
                anchor.reset();
            }
        }
    }

    /**
     * Disconnect this widget if we have a auto connection to it
     *
     * @param widget the widget we are removing
     */
    public void disconnectUnlockedWidget(ConstraintWidget widget) {
        final ArrayList<ConstraintAnchor> anchors = getAnchors();
        for (int i = 0, anchorsSize = anchors.size(); i < anchorsSize; i++) {
            final ConstraintAnchor anchor = anchors.get(i);
            if (anchor.isConnected() && (anchor.getTarget().getOwner() == widget)
                && anchor.getConnectionCreator() == ConstraintAnchor.AUTO_CONSTRAINT_CREATOR) {
                anchor.reset();
            }
        }
    }

    /**
     * Given a type of anchor, returns the corresponding anchor.
     *
     * @param anchorType type of the anchor (LEFT, TOP, RIGHT, BOTTOM, BASELINE, CENTER_X, CENTER_Y)
     * @return the matching anchor
     */
    public ConstraintAnchor getAnchor(ConstraintAnchor.Type anchorType) {
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

    /**
     * Accessor for the horizontal dimension behaviour
     *
     * @return dimension behaviour
     */
    public DimensionBehaviour getHorizontalDimensionBehaviour() {
        return mListDimensionBehaviors[DIMENSION_HORIZONTAL];
    }

    /**
     * Accessor for the vertical dimension behaviour
     *
     * @return dimension behaviour
     */
    public DimensionBehaviour getVerticalDimensionBehaviour() {
        return mListDimensionBehaviors[DIMENSION_VERTICAL];
    }

    /**
     * Set the widget's behaviour for the horizontal dimension
     *
     * @param behaviour the horizontal dimension's behaviour
     */
    public void setHorizontalDimensionBehaviour(DimensionBehaviour behaviour) {
        mListDimensionBehaviors[DIMENSION_HORIZONTAL] = behaviour;
        if (behaviour == WRAP_CONTENT) {
            setWidth(mWrapWidth);
        }
    }

    /**
     * Set the widget's behaviour for the vertical dimension
     *
     * @param behaviour the vertical dimension's behaviour
     */
    public void setVerticalDimensionBehaviour(DimensionBehaviour behaviour) {
        mListDimensionBehaviors[DIMENSION_VERTICAL] = behaviour;
        if (behaviour == WRAP_CONTENT) {
            setHeight(mWrapHeight);
        }
    }

    /**
     * test if you are in a Horizontal chain
     *
     * @return
     */
    public boolean isInHorizontalChain() {
        if ((mLeft.mTarget != null && mLeft.mTarget.mTarget == mLeft)
            || (mRight.mTarget != null && mRight.mTarget.mTarget == mRight)) {
            return true;
        }
        return false;
    }

    /**
     * if in a horizontal chain return the left most widget in the chain.
     *
     * @return left most widget in chain or null
     */
    public ConstraintWidget getHorizontalChainControlWidget() {
        ConstraintWidget found = null;
        if (isInHorizontalChain()) {
            ConstraintWidget tmp = this;

            while (found == null && tmp != null) {
                ConstraintAnchor anchor = tmp.getAnchor(ConstraintAnchor.Type.LEFT);
                ConstraintAnchor targetOwner = (anchor == null) ? null : anchor.getTarget();
                ConstraintWidget target = (targetOwner == null) ? null : targetOwner.getOwner();
                if (target == getParent()) {
                    found = tmp;
                    break;
                }
                ConstraintAnchor targetAnchor = (target == null) ? null : target.getAnchor(ConstraintAnchor.Type.RIGHT).getTarget();
                if (targetAnchor != null && targetAnchor.getOwner() != tmp) {
                    found = tmp;
                }
                else {
                    tmp = target;
                }
            }
        }
        return found;
    }


    /**
     * test if you are in a vertical chain
     *
     * @return
     */
    public boolean isInVerticalChain() {
        if ((mTop.mTarget != null && mTop.mTarget.mTarget == mTop)
            || (mBottom.mTarget != null && mBottom.mTarget.mTarget == mBottom)) {
            return true;
        }
        return false;
    }

    /**
     * return the top most widget in the control chain
     *
     * @return
     */
    public ConstraintWidget getVerticalChainControlWidget() {
        ConstraintWidget found = null;
        if (isInVerticalChain()) {
            ConstraintWidget tmp = this;
            while (found == null && tmp != null) {
                ConstraintAnchor anchor = tmp.getAnchor(ConstraintAnchor.Type.TOP);
                ConstraintAnchor targetOwner = (anchor == null) ? null : anchor.getTarget();
                ConstraintWidget target = (targetOwner == null) ? null : targetOwner.getOwner();
                if (target == getParent()) {
                    found = tmp;
                    break;
                }
                ConstraintAnchor targetAnchor = (target == null) ? null : target.getAnchor(ConstraintAnchor.Type.BOTTOM).getTarget();
                if (targetAnchor != null && targetAnchor.getOwner() != tmp) {
                    found = tmp;
                }
                else {
                    tmp = target;
                }
            }
        }
        return found;
    }
}