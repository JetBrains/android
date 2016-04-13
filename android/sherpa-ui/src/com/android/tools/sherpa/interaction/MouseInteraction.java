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

import com.android.tools.sherpa.animation.AnimatedDestroyCircle;
import com.android.tools.sherpa.animation.AnimatedDestroyLine;
import com.android.tools.sherpa.drawing.SceneDraw;
import com.android.tools.sherpa.drawing.SnapDraw;
import com.android.tools.sherpa.drawing.ViewTransform;
import com.android.tools.sherpa.drawing.decorator.WidgetDecorator;
import com.android.tools.sherpa.structure.Selection;
import com.android.tools.sherpa.structure.WidgetsScene;
import com.google.tnt.solver.widgets.ConstraintAnchor;
import com.google.tnt.solver.widgets.ConstraintWidget;
import com.google.tnt.solver.widgets.Guideline;
import com.google.tnt.solver.widgets.Snapshot;

import javax.swing.SwingUtilities;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

/**
 * Encapsulate the mouse interactions
 */
public class MouseInteraction {

    // Used as a margin value
    private static int sMargin = 8;

    private boolean mIsControlDown;
    private boolean mIsShiftDown;
    private boolean mIsAltDown;
    private boolean mMouseDown = false;
    private boolean mMoveOnlyMode = false;
    private final ViewTransform mViewTransform;
    private final WidgetsScene mWidgetsScene;
    private final SceneDraw mSceneDraw;
    private final Selection mSelection;
    private final WidgetMotion mWidgetMotion;
    private final WidgetResize mWidgetResize;

    // Points used for the selection / dragging of widgets

    private Point mStartPoint = new Point();
    private Point mLastMousePosition = new Point();

    private Snapshot mSnapshot = null;

    private boolean mUseDefinedMargin = true;
    private boolean mAutoConnect = true;

    public static void setMargin(int margin) {
        sMargin = margin;
    }

    // Represent the different mouse interaction modes
    enum MouseMode {
        INACTIVE, RESIZE, MOVE, CONNECT
    }

    private MouseMode mMouseMode = MouseMode.INACTIVE;

    /**
     * Base constructor
     *
     * @param transform    the view transform
     * @param widgetsScene
     */
    public MouseInteraction(ViewTransform transform,
            WidgetsScene widgetsScene, Selection selection,
            WidgetMotion widgetMotion, WidgetResize widgetResize,
            SceneDraw sceneDraw) {
        mViewTransform = transform;
        mWidgetsScene = widgetsScene;
        mSelection = selection;
        mWidgetMotion = widgetMotion;
        mWidgetResize = widgetResize;
        mSceneDraw = sceneDraw;
    }

    /*-----------------------------------------------------------------------*/
    // Accessors
    /*-----------------------------------------------------------------------*/

    /**
     * Accessor for the start mouse point
     *
     * @return start point
     */
    public Point getStartPoint() {
        return mStartPoint;
    }

    /**
     * Accessor for the last mouse point
     *
     * @return last point
     */
    public Point getLastPoint() {
        return mLastMousePosition;
    }

    /**
     * Accessor for control down check
     *
     * @return true if control is currently pressed
     */
    public boolean isControlDown() {
        return mIsControlDown;
    }

    /**
     * Setter for control down
     *
     * @param value
     */
    public void setIsControlDown(boolean value) {
        mIsControlDown = value;
    }

    /**
     * Accessor for shift down check
     *
     * @return true if shift is currently pressed
     */
    public boolean isShiftDown() {
        return mIsShiftDown;
    }

    /**
     * Setter for shift down
     *
     * @param value
     */
    public void setIsShiftDown(boolean value) {
        mIsShiftDown = value;
    }

    /**
     * Accessor for alt down check
     *
     * @return true if alt is currently pressed
     */
    public boolean isAltDown() {
        return mIsAltDown;
    }

    /**
     * Setter for alt down
     *
     * @param value
     */
    public void setIsAltDown(boolean value) {
        mIsAltDown = value;
    }

    /**
     * Accessor for mouse down check
     *
     * @return true if mouse is currently down (i.e. we are interacting)
     */
    public boolean isMouseDown() {
        return mMouseDown;
    }

    /**
     * Setter for the snapshot of the selected widget
     *
     * @param snapshot
     */
    public void setSnapshot(Snapshot snapshot) {
        mSnapshot = snapshot;
    }

    /**
     * Getter for the snapshot of the selected widget
     *
     * @return
     */
    public Snapshot getSnapshot() {
        return mSnapshot;
    }

    /**
     * Setter for the start point (needed to reposition the mouse point on anchor click)
     *
     * @param x x coordinate
     * @param y y coordinate
     */
    public void setStartPoint(int x, int y) {
        mStartPoint.setLocation(x, y);
    }

    /**
     * Getter returning true if we'll use the defined margin value, false if we'll use
     * the current distance between anchors as a margin.
     *
     * @return true if we'll use the defined margin value
     */
    public boolean isUseDefinedMargin() {
        return mUseDefinedMargin;
    }

    /**
     * Setter for deciding to use or not the defined margin value. Pass true to use it,
     * false to use instead the current distance between anchors as a margin when making a connection.
     *
     * @param useDefinedMargin
     */
    public void setUseDefinedMargin(boolean useDefinedMargin) {
        mUseDefinedMargin = useDefinedMargin;
    }

    /**
     * Getter returning true if we are in auto connect mode
     *
     * @return true if doing automatic connections
     */
    public boolean isAutoConnect() {
        return mAutoConnect;
    }

    /**
     * Setter for deciding to automatically connect elements when dragging them
     *
     * @param autoConnect set to true to automatically connect, false otherwise
     */
    public void setAutoConnect(boolean autoConnect) {
        mAutoConnect = autoConnect;
    }

    /*-----------------------------------------------------------------------*/
    // Mouse handling
    /*-----------------------------------------------------------------------*/

    /**
     * Mouse press handling
     *
     * @param x            mouse x coordinate
     * @param y            mouse y coordinate
     * @param isRightClick
     */
    public void mousePressed(float x, float y, boolean isRightClick) {
        mMouseDown = true;
        mStartPoint.setLocation(x, y);
        mLastMousePosition.setLocation(x, y);

        mSelection.setConnectionCandidateAnchor(null);
        mSelection.setSelectedAnchor(null);
        mSelection.setSelectedGuideline(null);
        mSelection.setLastConnectedAnchor(null);

        mMouseMode = MouseMode.INACTIVE;

        // check for widget, anchors, resize handle hits

        mWidgetsScene.updatePositions(mViewTransform);
        ConstraintWidget widget = mWidgetsScene.findWidget(mWidgetsScene.getRoot(), x, y);
        ConstraintAnchor anchor = mWidgetsScene.findAnchor(x, y, false, true, mViewTransform);
        ResizeHandle resizeHandle = mWidgetsScene.findResizeHandle(x, y, mViewTransform);

        // don't allow direct interactions with root
        if (widget == mWidgetsScene.getRoot()) {
            widget = null;
        }

        if (!isAltDown() ^ mMoveOnlyMode) { // alt down only accept moving
            if (anchor != null) {
                widget = anchor.getOwner();
                if (mSelection.contains(widget)) {
                    ConstraintHandle handle = WidgetInteractionTargets.constraintHandle(anchor);
                    setStartPoint(handle.getDrawX(), handle.getDrawY());
                    mSelection.clear();
                    mSelection.add(widget);
                    mSelection.setSelectedAnchor(anchor);
                    mMouseMode = MouseMode.CONNECT;
                }
            } else if (resizeHandle != null) {
                widget = resizeHandle.getOwner();
                if (mSelection.contains(widget)) {
                    mSelection.clear();
                    mSelection.add(widget);
                    mSelection.setSelectedResizeHandle(resizeHandle);
                    mMouseMode = MouseMode.RESIZE;
                }
            }
        }

        // If we hit a widget, update the selection
        if (widget != null) {
            if (mMouseMode == MouseMode.INACTIVE) {
                if (!mSelection.contains(widget)) {
                    // replace the current selection
                    if (!(isShiftDown() || isControlDown())) {
                        mSelection.clear();
                    }
                    mSelection.add(widget);
                } else if (isControlDown()) {
                    mSelection.remove(widget);
                }
                mMouseMode = MouseMode.MOVE;
            }
        }

        ///////////////////////////////////////////////////////////////////////
        // let's check for guidelines...
        // TODO: switch to the WidgetDecorator model
        ///////////////////////////////////////////////////////////////////////
        for (ConstraintWidget w : mWidgetsScene.getWidgets()) {
            // Check if we hit a guideline head
            if (w instanceof Guideline) {
                Guideline guideline = (Guideline) w;
                Rectangle head = guideline.getHead();
                if (head.contains(x, y)) {
                    mSelection.setSelectedGuideline(guideline);
                    break;
                }
            }
        }

        if (mSelection.getSelectedGuideline() != null) {
            mSelection.clear();
            mSelection.setSelectedResizeHandle(resizeHandle);
            mSelection.add(mSelection.getSelectedGuideline());
            widget = mSelection.getSelectedGuideline();
            if (mMouseMode == MouseMode.INACTIVE) {
                mMouseMode = MouseMode.MOVE;
            }
        }
        ///////////////////////////////////////////////////////////////////////

        // give a chance to widgets to respond to a mouse press even if out of bounds
        for (ConstraintWidget w : mWidgetsScene.getWidgets()) {
            WidgetDecorator decorator = (WidgetDecorator) w.getCompanionWidget();
            ConstraintWidget widgetHit = decorator.mousePressed(x, y, mViewTransform, mSelection);
            if (widgetHit != null && widget == null) {
                widget = widgetHit;
            }
        }

        if (widget == null) {
            // clear the selection as no widget were found
            mSelection.clear();
        }

        if (mSelection.getSelectedAnchor() != null) {
            mSelection.setSelectedAnchorInitialTarget(
                    mSelection.getSelectedAnchor().getTarget());
        }

        mSelection.updatePosition();

        // if the selection is multiple, compute the bounds
        mSelection.createBounds();

        if (mSelection.hasSingleElement()) {
            setSnapshot(new Snapshot(mSelection.getFirstElement().widget));
        } else {
            setSnapshot(null);
        }

        if (isRightClick) {
            mMouseMode = MouseMode.INACTIVE;
        }
        mSceneDraw.setCurrentUnderneathAnchor(mSelection.getSelectedAnchor());
        mSceneDraw.onMousePress(mSelection.getSelectedAnchor());
    }

    /**
     * Mouse release handling
     *
     * @param x mouse x coordinate
     * @param y mouse y coordinate
     */
    public void mouseReleased(int x, int y) {

        if (mAutoConnect) {
            // Auto-connect to candidates
            for (SnapCandidate candidate : mWidgetMotion.getSnapCandidates()) {
                if (!candidate.source.isConnectionAllowed(candidate.target.getOwner())) {
                    continue;
                }
                int margin = candidate.margin;
                if (candidate.padding != 0) {
                    margin = candidate.padding;
                }
                margin = Math.abs(margin);
                candidate.source.getOwner().connect(
                        candidate.source, candidate.target, margin,
                        ConstraintAnchor.AUTO_CONSTRAINT_CREATOR);
            }
        }

        mWidgetMotion.mouseReleased();
        mWidgetResize.mouseReleased();
        mSceneDraw.mouseReleased();

        // First check anchors that are not guidelines, to deal with the case
        // where we want to delete the connection
        ConstraintAnchor anchor = mWidgetsScene.findAnchor(
                getLastPoint().x, getLastPoint().y, false, false, mViewTransform);
        if (mSelection.getSelectedAnchor() != null
                && mSelection.getConnectionCandidateAnchor() == null
                && anchor == mSelection.getSelectedAnchor()) {
            // delete the anchor connection
            if (mSelection.getSelectedAnchor().isConnected()
                    && mSelection.getSelectedAnchor().getTarget()
                    == mSelection.getSelectedAnchorInitialTarget()) {
                mSelection.getSelectedAnchor().getOwner().resetAnchor(
                        mSelection.getSelectedAnchor());
                ConstraintAnchor selectedAnchor = mSelection.getSelectedAnchor();
                ConstraintHandle selectedHandle =
                        WidgetInteractionTargets.constraintHandle(selectedAnchor);
                if (mSelection.getSelectedAnchor().getType() == ConstraintAnchor.Type.BASELINE) {
                    mSceneDraw.getChoreographer()
                            .addAnimation(new AnimatedDestroyLine(selectedHandle));
                } else {
                    mSceneDraw.getChoreographer().addAnimation(
                            new AnimatedDestroyCircle(selectedHandle));
                }
                mSelection.addModifiedWidget(
                        mSelection.getSelectedAnchor().getOwner());
            }
        }

        if (mSelection.isEmpty() && mSelection.getSelectedAnchor() == null) {
            int x1 = Math.min(getStartPoint().x, getLastPoint().x);
            int x2 = Math.max(getStartPoint().x, getLastPoint().x);
            int y1 = Math.min(getStartPoint().y, getLastPoint().y);
            int y2 = Math.max(getStartPoint().y, getLastPoint().y);
            Rectangle selectionRect = new Rectangle(x1, y1, x2 - x1, y2 - y1);
            if (selectionRect.getWidth() > 0 && selectionRect.getHeight() > 0) {
                ArrayList<ConstraintWidget> selection = mWidgetsScene.findWidgets(
                        mWidgetsScene.getRoot(),
                        selectionRect.x, selectionRect.y,
                        selectionRect.width, selectionRect.height);
                for (ConstraintWidget widget : selection) {
                    mSelection.add(widget);
                }
            }
        }

        if (mSelection.getSelectedGuideline() != null) {
            Rectangle head = mSelection.getSelectedGuideline().getHead();
            if (head.contains(getStartPoint().x, getStartPoint().y)) {
                Selection.Element element = mSelection.get(mSelection.getSelectedGuideline());
                if (element != null) {
                    if (mSelection.getSelectedGuideline().getOrientation() ==
                            Guideline.HORIZONTAL) {
                        if (element.origin.x == mSelection.getSelectedGuideline().getDrawX()) {
                            mSelection.getSelectedGuideline().cyclePosition();
                        }
                    } else {
                        if (element.origin.y == mSelection.getSelectedGuideline().getDrawY()) {
                            mSelection.getSelectedGuideline().cyclePosition();
                        }
                    }
                }
            }
        }

        // give a chance to widgets to respond to a mouse press
        for (Selection.Element selection : mSelection.getElements()) {
            WidgetDecorator decorator = (WidgetDecorator) selection.widget.getCompanionWidget();
            decorator.mouseRelease(x, y, mViewTransform, mSelection);
        }

        for (Selection.Element selection : mSelection.getElements()) {
            selection.directionLocked = Selection.DIRECTION_UNLOCKED;
        }

        mSceneDraw.setCurrentUnderneathAnchor(null);
        mMouseMode = MouseMode.INACTIVE;
        mSelection.setSelectedAnchor(null);
        mSelection.setSelectedResizeHandle(null);
        mSelection.setConnectionCandidateAnchor(null);
        mSelection.clearBounds();
        mSelection.selectionHasChanged(); // in case something did change...
        mLastMousePosition.setLocation(0, 0);
        mSnapshot = null;
        mMouseDown = false;
    }

    /**
     * Mouse dragged handling
     *
     * @param x mouse x coordinate
     * @param y mouse y coordinate
     * @return the type of direction (locked in x/y or not)
     */
    public int mouseDragged(int x, int y) {
        mLastMousePosition.setLocation(x, y);
        int directionLockedStatus = Selection.DIRECTION_UNLOCKED;
        switch (mMouseMode) {
            case MOVE: {
                if (!mSelection.isEmpty()) {
                    // Remove any constraints auto-created
                    for (Selection.Element selection : mSelection.getElements()) {
                        for (ConstraintAnchor anchor : selection.widget.getAnchors()) {
                            if (anchor.isConnected()
                                    && anchor.getConnectionCreator()
                                    == ConstraintAnchor.AUTO_CONSTRAINT_CREATOR) {
                                anchor.getOwner().resetAnchor(anchor);
                            }
                        }
                    }
                    // Dragging the widget is no anchors or resize handles are selected
                    boolean snapPosition = mSelection.hasSingleElement();
                    if (!mSelection.hasSingleElement() && mSelection.getSelectionBounds() != null) {
                        Selection.Element bounds = mSelection.getSelectionBounds();
                        bounds.widget.setParent(mWidgetsScene.getRoot());
                        directionLockedStatus = mWidgetMotion.dragWidget(getStartPoint(), bounds,
                                x, y, true, isShiftDown(), mViewTransform);
                        mSelection.updatePositionsFromBounds();
                    } else {
                        for (Selection.Element selection : mSelection.getElements()) {
                            directionLockedStatus =
                                    mWidgetMotion.dragWidget(getStartPoint(), selection, x, y,
                                            snapPosition, isShiftDown(), mViewTransform);
                            mSelection.addModifiedWidget(selection.widget);
                        }
                    }
                }
            }
            break;
            case RESIZE: {
                if (mSelection.getSelectedResizeHandle() != null) {
                    // if we have a resize handle selected, let's resize!
                    Selection.Element selection = mSelection.getFirstElement();
                    if (mSelection.getSelectedResizeHandle() != null &&
                            !selection.widget.isRoot()) {
                        ArrayList<ConstraintWidget> widgetsToCheck =
                                new ArrayList<ConstraintWidget>();
                        for (ConstraintWidget w : mWidgetsScene.getWidgets()) {
                            widgetsToCheck.add(w);
                        }
                        mWidgetResize.resizeWidget(widgetsToCheck, selection.widget,
                                mSelection.getSelectedResizeHandle(),
                                mSelection.getOriginalWidgetBounds(), x, y);
                        mSelection.addModifiedWidget(selection.widget);
                    }
                }
            }
            break;
            case CONNECT: {
                if (mSelection.getSelectedAnchor() != null && mSelection.hasSingleElement()) {
                    // we have a selected anchor, let's check against other available anchors
                    ConstraintWidget selectedWidget = mSelection.getFirstElement().widget;
                    ConstraintAnchor anchor = mWidgetsScene
                            .findAnchor(getLastPoint().x, getLastPoint().y, true, false,
                                    mViewTransform);
                    if (anchor != null
                            && anchor != mSelection.getSelectedAnchor()
                            && mSelection.getSelectedAnchor().isValidConnection(anchor)
                            &&
                            mSelection.getSelectedAnchor().isConnectionAllowed(anchor.getOwner())) {
                        if (mSelection.getConnectionCandidateAnchor() != anchor) {
                            if (mSelection.getConnectionCandidateAnchor() != null) {
                                if (getSnapshot() != null) {
                                    getSnapshot().applyTo(selectedWidget);
                                    mSelection.addModifiedWidget(selectedWidget);
                                }
                            }
                            mSelection.setConnectionCandidateAnchor(anchor);
                        }
                        if (mSelection.getSelectedAnchor().getTarget() !=
                                mSelection.getConnectionCandidateAnchor()) {
                            int margin = 0;
                            boolean useExistingDistance = !mUseDefinedMargin;
                            if (isControlDown()) {
                                useExistingDistance = !useExistingDistance;
                            }
                            ConstraintHandle handle =
                                    WidgetInteractionTargets.constraintHandle(
                                            mSelection.getSelectedAnchor());
                            ConstraintHandle handleTarget =
                                    WidgetInteractionTargets.constraintHandle(
                                            mSelection.getConnectionCandidateAnchor());
                            int existingDistance = handle.getCreationMarginFrom(handleTarget);
                            if (useExistingDistance) {
                                margin = existingDistance;
                            } else {
                                if (existingDistance >= sMargin) {
                                    margin = sMargin;
                                }
                            }
                            ConstraintAnchor.Strength strength = ConstraintAnchor.Strength.STRONG;
                            if (isShiftDown()) {
                                strength = ConstraintAnchor.Strength.WEAK;
                            }
                            ConstraintWidget widget = mSelection.getSelectedAnchor().getOwner();
                            widget.connect(
                                    mSelection.getSelectedAnchor(),
                                    mSelection.getConnectionCandidateAnchor(), margin, strength,
                                    ConstraintAnchor.USER_CREATOR);
                            mSelection.addModifiedWidget(widget);
                            mSelection.setLastConnectedAnchor(mSelection.getSelectedAnchor());
                        }
                    } else {
                        if (mSelection.getConnectionCandidateAnchor() != null) {
                            mSelection.setConnectionCandidateAnchor(null);
                            if (getSnapshot() != null) {
                                getSnapshot().applyTo(selectedWidget);
                                mSelection.addModifiedWidget(selectedWidget);
                            }
                        }
                    }
                }
            }
            break;
        }
        return directionLockedStatus;
    }

    /**
     * Mouse moved handling
     *
     * @param x mouse x coordinate
     * @param y mouse y coordinate
     */
    public void mouseMoved(float x, float y) {
        if (mMoveOnlyMode) {
            return;
        }
        // In Mouse Moved, find any anchors we are hovering above
        ConstraintAnchor anchor =
                mWidgetsScene.findAnchorInSelection(x, y, false, true, mViewTransform);
        mSceneDraw.setCurrentUnderneathAnchor(anchor);
    }

    /*-----------------------------------------------------------------------*/
    // Mouse events
    /*-----------------------------------------------------------------------*/

    /**
     * Mouse press handling
     *
     * @param e mouse event
     */
    public void mousePressed(MouseEvent e) {
        mIsControlDown = e.isControlDown();
        mIsShiftDown = e.isShiftDown();
        mIsAltDown = e.isAltDown();
        float x = mViewTransform.getAndroidFX(e.getX());
        float y = mViewTransform.getAndroidFY(e.getY());
        mousePressed(x, y, SwingUtilities.isRightMouseButton(e));
    }

    /**
     * Mouse release handling
     *
     * @param e mouse event
     */
    public void mouseReleased(MouseEvent e) {
        int x = mViewTransform.getAndroidX(e.getX());
        int y = mViewTransform.getAndroidY(e.getY());
        mouseReleased(x, y);
    }

    /**
     * Mouse dragged handling
     *
     * @param e mouse event
     * @return the type of direction (locked in x/y or not)
     */
    public int mouseDragged(MouseEvent e) {
        mIsControlDown = e.isControlDown();
        mIsShiftDown = e.isShiftDown();
        mIsAltDown = e.isAltDown();
        int x = mViewTransform.getAndroidX(e.getX());
        int y = mViewTransform.getAndroidY(e.getY());
        return mouseDragged(x, y);
    }

    /**
     * Mouse entered handling
     *
     * @param e mouse event
     */
    public void mouseEntered(MouseEvent e) {
        mIsControlDown = e.isControlDown();
        mIsShiftDown = e.isShiftDown();
        mIsAltDown = e.isAltDown();
    }

    /**
     * Mouse moved handling
     *
     * @param e mouse event
     */
    public void mouseMoved(MouseEvent e) {
        mIsControlDown = e.isControlDown();
        mIsShiftDown = e.isShiftDown();
        mIsAltDown = e.isAltDown();
        float x = mViewTransform.getAndroidFX(e.getX());
        float y = mViewTransform.getAndroidFY(e.getY());
        mouseMoved(x, y);
    }

    /*-----------------------------------------------------------------------*/
    // Key events
    /*-----------------------------------------------------------------------*/

    public void keyPressed(KeyEvent e) {
        mIsControlDown = e.isControlDown();
        mIsShiftDown = e.isShiftDown();
        mIsAltDown = e.isAltDown();
    }

    public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
            mIsControlDown = false;
        }
        if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
            mIsShiftDown = false;
        }
    }

    public boolean isMoveOnlyMode() {
        return mMoveOnlyMode;
    }

    public void setMoveOnlyMode(boolean moveOnlyMode) {
        mMoveOnlyMode = moveOnlyMode;
    }
}
