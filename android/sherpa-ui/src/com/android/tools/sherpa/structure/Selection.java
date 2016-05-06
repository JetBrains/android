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

package com.android.tools.sherpa.structure;

import com.android.tools.sherpa.interaction.ResizeHandle;
import com.android.tools.sherpa.drawing.decorator.WidgetDecorator;
import com.google.tnt.solver.widgets.ConstraintAnchor;
import com.google.tnt.solver.widgets.ConstraintWidget;
import com.google.tnt.solver.widgets.Guideline;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;

/**
 * This class keeps track of the current selection of widgets and helps us manipulate it
 */
public class Selection {

    private ArrayList<Element> mSelectedWidgets = new ArrayList<>();
    private ArrayList<ConstraintWidget> mModifiedWidgets = new ArrayList<>();
    private ArrayList<SelectionListener> mSelectionListeners = new ArrayList<>();

    private ConstraintAnchor mSelectedAnchor = null;
    private ConstraintAnchor mConnectionCandidateAnchor = null;
    private ConstraintAnchor mLastConnectedAnchor = null;

    // the target of the selected anchor when we first mouse press
    private ConstraintAnchor mSelectedAnchorInitialTarget;

    // selected resize handle if any
    private ResizeHandle mSelectedResizeHandle;

    // selected guideline if any
    // TODO: should be refactored out!
    Guideline mSelectedGuideline;

    public static final int DIRECTION_UNLOCKED = 0;
    public static final int DIRECTION_LOCKED_X = 1;
    public static final int DIRECTION_LOCKED_Y = 2;

    private Element mBounds;
    private Rectangle mOriginalWidgetBounds = new Rectangle();

    /*-----------------------------------------------------------------------*/
    // Initialization
    /*-----------------------------------------------------------------------*/

    /**
     * Simple selection listener interface
     */
    public interface SelectionListener {
        void onSelectionChanged(Selection selection);
    }

    /**
     * Small internal class to encapsulate a selected widget and its position at
     * selection time
     */
    public static class Element {
        public ConstraintWidget widget;
        public Point origin = new Point();
        public int directionLocked = DIRECTION_UNLOCKED;

        public Element(ConstraintWidget widget) {
            this.widget = widget;
            this.origin.setLocation(widget.getDrawX(), widget.getDrawY());
        }

        public void updatePosition() {
            this.origin.setLocation(this.widget.getDrawX(), this.widget.getDrawY());
        }
    }

    /**
     * Constructor
     *
     * @param listener takes a listener that will be called when the selection changes
     */
    public Selection(SelectionListener listener) {
        if (listener != null) {
            mSelectionListeners.add(listener);
        }
    }

    /*-----------------------------------------------------------------------*/
    // Selection list manipulation
    /*-----------------------------------------------------------------------*/

    /**
     * Clear the current selection
     */
    public void clear() {
        mSelectedWidgets.clear();
        mModifiedWidgets.clear();
        selectionHasChanged();
    }

    /**
     * Clear the list of modified widgets
     */
    public void clearModifiedWidgets() {
        mModifiedWidgets.clear();
    }

    /**
     * Check if the selection is empty
     *
     * @return true if the selection is empty
     */
    public boolean isEmpty() {
        return mSelectedWidgets.size() == 0;
    }

    /**
     * Check if the selection has a single element
     *
     * @return true if the selection has a single element
     */
    public boolean hasSingleElement() {
        return mSelectedWidgets.size() == 1;
    }

    /**
     * Returns the first element, if it exists, of the selection
     *
     * @return return the first element, or null if the selection is empty
     */
    public Element getFirstElement() {
        if (mSelectedWidgets.size() == 0) {
            return null;
        }
        return mSelectedWidgets.get(0);
    }

    /**
     * Accessor to the list of elements in the selection
     *
     * @return the list of selected elements
     */
    public ArrayList<Element> getElements() {
        return mSelectedWidgets;
    }

    /**
     * Accessor to the list of modified widgets
     *
     * @return list of modified widgets
     */
    public ArrayList<ConstraintWidget> getModifiedWidgets() {
        return mModifiedWidgets;
    }

    /**
     * Add a widget to the list of modified widgets
     *
     * @param widget a widget we modified
     */
    public void addModifiedWidget(ConstraintWidget widget) {
        if (mModifiedWidgets.contains(widget)) {
            return;
        }
        mModifiedWidgets.add(widget);
    }

    /**
     * Return the current selection's size
     *
     * @return the number of selected elements
     */
    public int size() {
        return mSelectedWidgets.size();
    }

    /**
     * Check if the given widget is part of the selection
     *
     * @param widget the widget we want to check
     * @return true if the widget is in the selection, false otherwise
     */
    public boolean contains(ConstraintWidget widget) {
        for (Element w : mSelectedWidgets) {
            if (w.widget == widget) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add a widget to the current selection
     *
     * @param widget widget to add to the selection
     */
    public void add(ConstraintWidget widget) {
        mSelectedWidgets.add(new Element(widget));
        selectionHasChanged();
    }

    /**
     * Remove the widget from the current selection
     *
     * @param widget widget to remove
     */
    public void remove(ConstraintWidget widget) {
        Element toUnselect = null;
        for (Element selection : mSelectedWidgets) {
            if (selection.widget == widget) {
                toUnselect = selection;
                break;
            }
        }
        if (toUnselect != null) {
            mSelectedWidgets.remove(toUnselect);
        }
        selectionHasChanged();
    }

    /**
     * Returns the element associated with the widget, if any
     *
     * @param widget the widget we are looking for
     * @return the element associated with the widget, null if the widget isn't
     * part of the selection
     */
    public Element get(ConstraintWidget widget) {
        for (Element element : mSelectedWidgets) {
            if (element.widget == widget) {
                return element;
            }
        }
        return null;
    }

    /**
     * Update the positions of the elements in the selection
     */
    public void updatePosition() {
        for (Element selection : mSelectedWidgets) {
            selection.updatePosition();
        }
    }

    /**
     * Construct and return an array containing the widgets in the selection
     *
     * @return array of selected widgets
     */
    public ArrayList<ConstraintWidget> getWidgets() {
        ArrayList<ConstraintWidget> widgets = new ArrayList<>();
        for (Element selection : mSelectedWidgets) {
            widgets.add(selection.widget);
        }
        return widgets;
    }

    /**
     * Function to call the listener when the selection has changed
     */
    public void selectionHasChanged() {
        for (SelectionListener selectionListener : mSelectionListeners) {
            selectionListener.onSelectionChanged(this);
        }
    }

    /**
     * Adds a selection listener
     *
     * @param selectionListener
     */
    public void addSelectionListener(SelectionListener selectionListener) {
        mSelectionListeners.add(selectionListener);
    }

    /**
     * Accessor for the current connection candidate anchor
     *
     * @return the connection candidate anchor (if any)
     */
    public ConstraintAnchor getConnectionCandidateAnchor() {
        return mConnectionCandidateAnchor;
    }

    /**
     * Setter for the connection candidate anchor
     *
     * @param anchor the anchor that will be our connection candidate
     */
    public void setConnectionCandidateAnchor(ConstraintAnchor anchor) {
        mConnectionCandidateAnchor = anchor;
    }

    /**
     * Setter for the last connected anchor
     * @param lastConnectedAnchor
     */
    public void setLastConnectedAnchor(ConstraintAnchor lastConnectedAnchor) {
        mLastConnectedAnchor = lastConnectedAnchor;
    }

    /**
     * Accessor for the last connected anchor
     */
    public ConstraintAnchor getLastConnectedAnchor() {
        return mLastConnectedAnchor;
    }

    /**
     * Accessor for the current selected anchor
     *
     * @return the selected anchor (if any)
     */
    public ConstraintAnchor getSelectedAnchor() {
        return mSelectedAnchor;
    }

    /**
     * Setter for the selected anchor
     *
     * @param anchor the selected anchor
     */
    public void setSelectedAnchor(ConstraintAnchor anchor) {
        mSelectedAnchor = anchor;
    }

    /**
     * Accessor for the selected anchor initial target
     *
     * @return the selected anchor initial target (if any)
     */
    public ConstraintAnchor getSelectedAnchorInitialTarget() {
        return mSelectedAnchorInitialTarget;
    }

    /**
     * Setter for the selected anchor initial target
     *
     * @param anchor the selected anchor initial target
     */
    public void setSelectedAnchorInitialTarget(ConstraintAnchor anchor) {
        mSelectedAnchorInitialTarget = anchor;
    }

    /**
     * Accessor for the selected resize handle
     *
     * @return the selected resize handle (if any)
     */
    public ResizeHandle getSelectedResizeHandle() {
        return mSelectedResizeHandle;
    }

    /**
     * Setter for the selected resize handle
     *
     * @param handle the selected resize handle
     */
    public void setSelectedResizeHandle(ResizeHandle handle) {
        mSelectedResizeHandle = handle;
        if (mSelectedResizeHandle != null) {
            ConstraintWidget widget = mSelectedResizeHandle.getOwner();
            mOriginalWidgetBounds.setBounds(
                    widget.getDrawX(), widget.getDrawY(),
                    widget.getWidth(), widget.getHeight());
        } else {
            mOriginalWidgetBounds.setBounds(0, 0, 0, 0);
        }
    }

    /**
     * Accessor for the original bounds of the selected widget
     * (used when resizing the widget)
     *
     * @return the original bounds
     */
    public Rectangle getOriginalWidgetBounds() {
        return mOriginalWidgetBounds;
    }

    /**
     * Accessor for the selected guideline
     *
     * @return the selected guideline (if any)
     */
    public Guideline getSelectedGuideline() {
        return mSelectedGuideline;
    }

    /**
     * Setter for the selected guideline
     *
     * @param guideline the selected guideline
     */
    public void setSelectedGuideline(Guideline guideline) {
        mSelectedGuideline = guideline;
    }

    /*-----------------------------------------------------------------------*/
    // multiple selection bounds support
    /*-----------------------------------------------------------------------*/

    /**
     * Clear the bounds object (typically, on mouseReleased())
     */
    public void clearBounds() {
        mBounds = null;
    }

    /**
     * Accessor for the bounds object
     *
     * @return an element representing the bounds of the entire selection, or null if the
     * selection only has one element.
     */
    public Element getSelectionBounds() {
        return mBounds;
    }

    /**
     * If the selection has more than one element, it will create a temporary
     * Element object as the bounds of the entire selection. We then use
     * this bounds object to snap the full selection on screen.
     */
    public void createBounds() {
        if (isEmpty() || hasSingleElement()) {
            mBounds = null;
            return;
        }
        int l = Integer.MAX_VALUE;
        int t = Integer.MAX_VALUE;
        int r = 0;
        int b = 0;
        for (Selection.Element selection : getElements()) {
            ConstraintWidget w = selection.widget;
            l = Math.min(w.getDrawX(), l);
            t = Math.min(w.getDrawY(), t);
            r = Math.max(w.getDrawRight(), r);
            b = Math.max(w.getDrawBottom(), b);
        }
        ConstraintWidget bounds = new ConstraintWidget(l, t, r - l, b - t);
        bounds.setCompanionWidget(WidgetCompanion.create(bounds));
        mBounds = new Element(bounds);
        updateOriginFromBounds();
    }

    /**
     * If we have multiple elements selected, let's repurpose the elements' origins
     * to be relative to the bounds object.
     */
    public void updatePositionsFromBounds() {
        if (mBounds == null) {
            return;
        }
        int x = mBounds.widget.getDrawX();
        int y = mBounds.widget.getDrawY();
        for (Selection.Element selection : getElements()) {
            selection.widget.setDrawX(x + selection.origin.x);
            selection.widget.setDrawY(y + selection.origin.y);
            addModifiedWidget(selection.widget);
        }
    }

    /**
     * Update the selection elements position using the bounds position
     */
    private void updateOriginFromBounds() {
        if (mBounds == null) {
            return;
        }
        int x = mBounds.origin.x;
        int y = mBounds.origin.y;
        for (Selection.Element selection : getElements()) {
            selection.origin.x = selection.widget.getDrawX() - x;
            selection.origin.y = selection.widget.getDrawY() - y;
        }
    }

}
