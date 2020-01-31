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

    /**
     * Constructor
     *
     * @param x      x position
     * @param y      y position
     * @param width  width of the layout
     * @param height height of the layout
     */
    public WidgetContainer(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    /**
     * Constructor
     *
     * @param width  width of the layout
     * @param height height of the layout
     */
    public WidgetContainer(int width, int height) {
        super(width, height);
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
     * Return the top-level ConstraintWidgetContainer
     *
     * @return top-level ConstraintWidgetContainer
     */
    public ConstraintWidgetContainer getRootConstraintContainer() {
        ConstraintWidget item = this;
        ConstraintWidget parent = item.getParent();
        ConstraintWidgetContainer container = null;
        if (item instanceof ConstraintWidgetContainer) {
            container = (ConstraintWidgetContainer)this;
        }
        while (parent != null) {
            item = parent;
            parent = item.getParent();
            if (item instanceof ConstraintWidgetContainer) {
                container = (ConstraintWidgetContainer)item;
            }
        }
        return container;
    }

    /*-----------------------------------------------------------------------*/
    // Operations on children
    /*-----------------------------------------------------------------------*/

    /**
     * Find a widget at the coordinate (x, y)
     *
     * @param x x position
     * @param y y position
     * @return a widget if found, null otherwise
     */
    public ConstraintWidget findWidget(float x, float y) {
        ConstraintWidget found = null;
        int l = getDrawX();
        int t = getDrawY();
        int r = l + getWidth();
        int b = t + getHeight();
        if (x >= l && x <= r && y >= t && y <= b) {
            found = this;
        }
        for (int i = 0, mChildrenSize = mChildren.size(); i < mChildrenSize; i++) {
            final ConstraintWidget widget = mChildren.get(i);
            if (widget instanceof WidgetContainer) {
                ConstraintWidget f = ((WidgetContainer) widget).findWidget(x, y);
                if (f != null) {
                    found = f;
                }
            } else {
                l = widget.getDrawX();
                t = widget.getDrawY();
                r = l + widget.getWidth();
                b = t + widget.getHeight();
                if (x >= l && x <= r && y >= t && y <= b) {
                    found = widget;
                }
            }
        }
        return found;
    }

    /**
     * Gather all the widgets contained in the area specified and return them as an array
     *
     * @param x      x position of the selection area
     * @param y      y position of the selection area
     * @param width  width of the selection area
     * @param height height of the selection area
     * @return an array containing the widgets inside the selection area
     */
    public ArrayList<ConstraintWidget> findWidgets(int x, int y, int width, int height) {
        ArrayList<ConstraintWidget> found = new ArrayList<ConstraintWidget>();
        Rectangle area = new Rectangle();
        area.setBounds(x, y, width, height);
        for (int i = 0, mChildrenSize = mChildren.size(); i < mChildrenSize; i++) {
            final ConstraintWidget widget = mChildren.get(i);
            Rectangle bounds = new Rectangle();
            bounds.setBounds(widget.getDrawX(), widget.getDrawY(),
                    widget.getWidth(), widget.getHeight());
            if (area.intersects(bounds)) {
                found.add(widget);
            }
        }
        return found;
    }

    /**
     * Return the bounds of the selected group of widgets
     *
     * @param widgets
     * @return
     */
    public static Rectangle getBounds(ArrayList<ConstraintWidget> widgets) {
        Rectangle bounds = new Rectangle();
        if (widgets.size() == 0) {
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

    public void removeAllChildren() {
        mChildren.clear();
    }
}