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
 * A container of ConstraintWidget
 */
public class WidgetContainer extends ConstraintWidget {
    protected ArrayList<ConstraintWidget> mChildren = new ArrayList<>();

    /*-----------------------------------------------------------------------*/
    // Construction
    /*-----------------------------------------------------------------------*/

    /**
     * Default constructor
     */
    public WidgetContainer() {
    }

    @Override
    public void reset() {
        mChildren.clear();
        super.reset();
    }

    /**
     * Add a child widget
     *
     * @param widget to add
     */
    public void add(ConstraintWidget widget) {
        mChildren.add(widget);
        if (widget.getParent() != null) {
            WidgetContainer container = (WidgetContainer)widget.getParent();
            container.remove(widget);
        }
        widget.setParent(this);
    }

    /**
     * Remove a child widget
     *
     * @param widget to remove
     */
    public void remove(ConstraintWidget widget) {
        mChildren.remove(widget);
        widget.setParent(null);
    }

    /**
     * Access the children
     *
     * @return the array of children
     */
    public ArrayList<ConstraintWidget> getChildren() {
        return mChildren;
    }

    /**
     * Return the bounds of the selected group of widgets
     *
     * @param widgets
     * @return
     */
    public static Rectangle getBounds(ArrayList<ConstraintWidget> widgets) {
        Rectangle bounds = new Rectangle();
        if (widgets.isEmpty()) {
            return bounds;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = 0;
        int minY = Integer.MAX_VALUE;
        int maxY = 0;
        for (int i = 0, widgetsSize = widgets.size(); i < widgetsSize; i++) {
            final ConstraintWidget widget = widgets.get(i);
            if (widget.getX() < minX) {
                minX = widget.getX();
            }
            if (widget.getY() < minY) {
                minY = widget.getY();
            }
            if (widget.getRight() > maxX) {
                maxX = widget.getRight();
            }
            if (widget.getBottom() > maxY) {
                maxY = widget.getBottom();
            }
        }
        bounds.setBounds(minX, minY, maxX - minX, maxY - minY);
        return bounds;
    }

    /*-----------------------------------------------------------------------*/
    // Overloaded methods from ConstraintWidget
    /*-----------------------------------------------------------------------*/

    /**
     * Set the offset of this widget relative to the root widget.
     * We then set the offset of our children as well.
     *
     * @param x horizontal offset
     * @param y vertical offset
     */
    @Override
    public void setOffset(int x, int y) {
        super.setOffset(x, y);
        final int count = mChildren.size();
        for (int i = 0; i < count; i++) {
            ConstraintWidget widget = mChildren.get(i);
            widget.setOffset(getRootX(), getRootY());
        }
    }

    /**
     * Update the draw position
     * Recursive call to the children
     */
    @Override
    public void updateDrawPosition() {
        super.updateDrawPosition();
        if (mChildren == null) {
            return;
        }
        final int count = mChildren.size();
        for (int i = 0; i < count; i++) {
            ConstraintWidget widget = mChildren.get(i);
            widget.setOffset(getDrawX(), getDrawY());
            if (!(widget instanceof ConstraintWidgetContainer)) {
                widget.updateDrawPosition();
            }
        }
    }

    /**
     * Function implemented by ConstraintWidgetContainer
     */
    public void layout() {
        updateDrawPosition();
        if (mChildren == null) {
            return;
        }
        final int count = mChildren.size();
        for (int i = 0; i < count; i++) {
            ConstraintWidget widget = mChildren.get(i);
            if (widget instanceof WidgetContainer) {
                ((WidgetContainer)widget).layout();
            }
        }
    }
}