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
import java.util.Arrays;

/**
 * A container of ConstraintWidget that can layout its children
 */
public class ConstraintWidgetContainer extends WidgetContainer {

    int mPaddingLeft;
    int mPaddingTop;
    int mPaddingRight;
    int mPaddingBottom;

    int mHorizontalChainsSize = 0;
    int mVerticalChainsSize = 0;

    ConstraintWidget[] mVerticalChainsArray = new ConstraintWidget[4];
    ConstraintWidget[] mHorizontalChainsArray = new ConstraintWidget[4];

    private boolean mWidthMeasuredTooSmall = false;
    private boolean mHeightMeasuredTooSmall = false;

    /*-----------------------------------------------------------------------*/
    // Construction
    /*-----------------------------------------------------------------------*/

    /**
     * Default constructor
     */
    public ConstraintWidgetContainer() {
    }

    /**
     * Constructor
     *
     * @param x      x position
     * @param y      y position
     * @param width  width of the layout
     * @param height height of the layout
     */
    public ConstraintWidgetContainer(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    /**
     * Constructor
     *
     * @param width  width of the layout
     * @param height height of the layout
     */
    public ConstraintWidgetContainer(int width, int height) {
        super(width, height);
    }

    /**
     * Specify the xml type for the container
     *
     * @return
     */
    @Override
    public String getType() {
        return "ConstraintLayout";
    }

    @Override
    public void reset() {
        mPaddingLeft = 0;
        mPaddingRight = 0;
        mPaddingTop = 0;
        mPaddingBottom = 0;
        super.reset();
    }

    /**
     * Return true if the width given is too small for the content layed out
     */
    public boolean isWidthMeasuredTooSmall() { return mWidthMeasuredTooSmall; }

    /**
     * Return true if the height given is too small for the content layed out
     */
    public boolean isHeightMeasuredTooSmall() { return mHeightMeasuredTooSmall; }

    /*-----------------------------------------------------------------------*/
    // Overloaded methods from ConstraintWidget
    /*-----------------------------------------------------------------------*/

    /**
     * Set the padding on this container. It will apply to the position of the children.
     *
     * @param left   left padding
     * @param top    top padding
     * @param right  right padding
     * @param bottom bottom padding
     */
    public void setPadding(int left, int top, int right, int bottom) {
        mPaddingLeft = left;
        mPaddingTop = top;
        mPaddingRight = right;
        mPaddingBottom = bottom;
    }

    /**
     * Indicates if the container knows how to layout its content on its own
     *
     * @return true if the container does the layout, false otherwise
     */
    public boolean handlesInternalConstraints() {
        return false;
    }

    /*-----------------------------------------------------------------------*/
    // Guidelines
    /*-----------------------------------------------------------------------*/

    /**
     * Accessor to the vertical guidelines contained in the table.
     *
     * @return array of guidelines
     */
    public ArrayList<Guideline> getVerticalGuidelines() {
        ArrayList<Guideline> guidelines = new ArrayList<>();
        for (int i = 0, mChildrenSize = mChildren.size(); i < mChildrenSize; i++) {
            final ConstraintWidget widget = mChildren.get(i);
            if (widget instanceof Guideline) {
                Guideline guideline = (Guideline) widget;
                if (guideline.getOrientation() == Guideline.VERTICAL) {
                    guidelines.add(guideline);
                }
            }
        }
        return guidelines;
    }

    /**
     * Accessor to the horizontal guidelines contained in the table.
     *
     * @return array of guidelines
     */
    public ArrayList<Guideline> getHorizontalGuidelines() {
        ArrayList<Guideline> guidelines = new ArrayList<>();
        for (int i = 0, mChildrenSize = mChildren.size(); i < mChildrenSize; i++) {
            final ConstraintWidget widget = mChildren.get(i);
            if (widget instanceof Guideline) {
                Guideline guideline = (Guideline) widget;
                if (guideline.getOrientation() == Guideline.HORIZONTAL) {
                    guidelines.add(guideline);
                }
            }
        }
        return guidelines;
    }

    /*-----------------------------------------------------------------------*/
    // Chains
    /*-----------------------------------------------------------------------*/

    /**
     * Reset the chains array. Need to be called before layout.
     */
    private void resetChains() {
        mHorizontalChainsSize = 0;
        mVerticalChainsSize = 0;
    }

    /**
     * Add the chain which constraintWidget is part of. Called by ConstraintWidget::addToSolver()
     *
     * @param constraintWidget
     * @param type             HORIZONTAL or VERTICAL chain
     */
    void addChain(ConstraintWidget constraintWidget, int type) {
        ConstraintWidget widget = constraintWidget;
        if (type == HORIZONTAL) {
            // find the left most widget that doesn't have a dual connection (i.e., start of chain)
            while (widget.mLeft.mTarget != null
                    && widget.mLeft.mTarget.mOwner.mRight.mTarget != null
                    && widget.mLeft.mTarget.mOwner.mRight.mTarget == widget.mLeft
                    && widget.mLeft.mTarget.mOwner != widget) {
                widget = widget.mLeft.mTarget.mOwner;
            }
            addHorizontalChain(widget);
        } else if (type == VERTICAL) {
            // find the top most widget that doesn't have a dual connection (i.e., start of chain)
            while (widget.mTop.mTarget != null
                    && widget.mTop.mTarget.mOwner.mBottom.mTarget != null
                    && widget.mTop.mTarget.mOwner.mBottom.mTarget == widget.mTop
                    && widget.mTop.mTarget.mOwner != widget) {
                widget = widget.mTop.mTarget.mOwner;
            }
            addVerticalChain(widget);
        }
    }

    /**
     * Add a widget to the list of horizontal chains. The widget is the left-most widget
     * of the chain which doesn't have a left dual connection.
     *
     * @param widget widget starting the chain
     */
    private void addHorizontalChain(ConstraintWidget widget) {
        for (int i = 0; i < mHorizontalChainsSize; i++) {
            if (mHorizontalChainsArray[i] == widget) {
                return;
            }
        }
        if (mHorizontalChainsSize + 1 >= mHorizontalChainsArray.length) {
            mHorizontalChainsArray = Arrays.copyOf(mHorizontalChainsArray, mHorizontalChainsArray.length * 2);
        }
        mHorizontalChainsArray[mHorizontalChainsSize] = widget;
        mHorizontalChainsSize++;
    }

    /**
     * Add a widget to the list of vertical chains. The widget is the top-most widget
     * of the chain which doesn't have a top dual connection.
     *
     * @param widget widget starting the chain
     */
    private void addVerticalChain(ConstraintWidget widget) {
        for (int i = 0; i < mVerticalChainsSize; i++) {
            if (mVerticalChainsArray[i] == widget) {
                return;
            }
        }
        if (mVerticalChainsSize + 1 >= mVerticalChainsArray.length) {
            mVerticalChainsArray = Arrays.copyOf(mVerticalChainsArray, mVerticalChainsArray.length * 2);
        }
        mVerticalChainsArray[mVerticalChainsSize] = widget;
        mVerticalChainsSize++;
    }
}