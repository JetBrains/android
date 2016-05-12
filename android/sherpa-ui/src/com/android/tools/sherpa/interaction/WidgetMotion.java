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

import com.android.tools.sherpa.drawing.SceneDraw;
import com.android.tools.sherpa.drawing.ViewTransform;
import com.android.tools.sherpa.drawing.decorator.WidgetDecorator;
import com.android.tools.sherpa.structure.WidgetCompanion;
import com.android.tools.sherpa.structure.WidgetsScene;
import com.android.tools.sherpa.structure.Selection;
import android.constraint.solver.widgets.Animator;
import android.constraint.solver.widgets.ConstraintAnchor;
import android.constraint.solver.widgets.ConstraintWidget;

import java.awt.Point;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

/**
 * This class encapsulates the dragging/moving behaviour for widgets, snapping to other
 * widgets and gathering similar margins
 */
public class WidgetMotion {

    private static final int SLOPE = 4;

    private final WidgetsScene mWidgetsScene;
    private final Selection mSelection;

    // will contains a list of candidates and margins as we drag the widgets
    private ArrayList<SnapCandidate> mSnapCandidates = new ArrayList<>();
    private ArrayList<SnapCandidate> mSimilarMargins = new ArrayList<>();

    // flag to indicate we are dragging a widget and we want to hide its decorations
    private boolean mShowDecorations = true;

    private static final int GRID_SPACING = 8; // Material Design 8dp grid
    private SceneDraw mSceneDraw;

    private enum Direction {
        LEFT,
        UP,
        RIGHT,
        DOWN
    }

    /**
     * Base constructor, takes the current list of widgets and the selection
     *
     * @param widgetsScene list of widgets
     * @param selection    current selection
     */
    public WidgetMotion(WidgetsScene widgetsScene, Selection selection) {
        mWidgetsScene = widgetsScene;
        mSelection = selection;
    }

    /**
     * Drag widget to a new position
     *
     * @param widget      widget we are moving
     * @param x           in android coordinate
     * @param y           in android coordinate
     * @param snap        true if we want to snap this widget against others
     * @param isShiftDown true if the shift button is pressed
     * @param transform   the view transform
     * @return the type of direction (locked in x/y or not)
     */
    public int dragWidget(Point startPoint, Selection.Element widget, int x, int y, boolean snap,
            boolean isShiftDown, ViewTransform transform) {
        int directionLockedStatus = Selection.DIRECTION_UNLOCKED;
        if (widget == null) {
            return directionLockedStatus;
        }
        Animator.setAnimationEnabled(false);
        int dX = startPoint.x - widget.origin.x;
        int dY = startPoint.y - widget.origin.y;
        int dragX = Math.abs(widget.widget.getDrawX() - widget.origin.x);
        int dragY = Math.abs(widget.widget.getDrawY() - widget.origin.y);
        if (dragX > SLOPE || dragY > SLOPE) {
            // Let's not show the anchors and resize handles if we are dragging
            mShowDecorations = false;
        }
        if (isShiftDown) {
            // check which overall direction we are going after enough drag
            if (widget.directionLocked == Selection.DIRECTION_UNLOCKED) {
                if (dragX > SLOPE || dragY > SLOPE) {
                    if (dragX > dragY) {
                        // lock in x
                        widget.directionLocked = Selection.DIRECTION_LOCKED_X;
                        directionLockedStatus = Selection.DIRECTION_LOCKED_X;
                    } else {
                        widget.directionLocked = Selection.DIRECTION_LOCKED_Y;
                        directionLockedStatus = Selection.DIRECTION_LOCKED_Y;
                    }
                } else {
                    snap = false; // prevent snapping while we are figuring out the locked axis
                }
            }
        } else {
            widget.directionLocked = Selection.DIRECTION_UNLOCKED;
        }
        Point candidatePoint = new Point(x - dX, y - dY);
        if (candidatePoint.x < 0) {
            candidatePoint.x = 0;
        }
        if (candidatePoint.y < 0) {
            candidatePoint.y = 0;
        }
        mSnapCandidates.clear();
        ArrayList<ConstraintWidget> widgetsToCheck = new ArrayList<>();
        for (ConstraintWidget w : mWidgetsScene.getWidgets()) {
            if (w.hasAncestor(widget.widget)) {
                continue;
            }
            if (mSelection.contains(w)) {
                continue;
            }
            widgetsToCheck.add(w);
        }
        // lock direction before applying the snap
        if (widget.directionLocked == Selection.DIRECTION_LOCKED_X) {
            candidatePoint.y = widget.origin.y;
        } else if (widget.directionLocked == Selection.DIRECTION_LOCKED_Y) {
            candidatePoint.x = widget.origin.x;
        }
        if (snap) {
            SnapPlacement.snapWidget(widgetsToCheck, widget.widget,
                    candidatePoint, false, mSnapCandidates, transform);
        }

        WidgetCompanion widgetCompanion = (WidgetCompanion) widget.widget.getCompanionWidget();
        WidgetInteractionTargets widgetInteraction = widgetCompanion.getWidgetInteractionTargets();

        // check if we have centered connections, if so allow moving and snapping
        // on specific percentage positions
        snapBias(widget.widget, candidatePoint);

        widget.widget.setDrawOrigin(candidatePoint.x, candidatePoint.y);
        widget.widget.forceUpdateDrawPosition();
        widgetInteraction.updatePosition(transform);

        mSimilarMargins.clear();
        for (SnapCandidate candidate : mSnapCandidates) {
            if (candidate.margin != 0) {
                mSimilarMargins.add(candidate);
            }
        }
        for (SnapCandidate candidate : mSnapCandidates) {
            SnapPlacement
                    .gatherMargins(mWidgetsScene.getWidgets(), mSimilarMargins,
                            candidate.margin, candidate.source.isVerticalAnchor());
        }
        return directionLockedStatus;
    }

    /**
     * Snap the widget's horizontal or vertical bias if we have horizontal/vertical
     * centered connections
     *
     * @param widget         the current widget
     * @param candidatePoint the candidate point containing the current location
     */
    private void snapBias(ConstraintWidget widget, Point candidatePoint) {
        WidgetCompanion widgetCompanion = (WidgetCompanion) widget.getCompanionWidget();
        int currentStyle = WidgetDecorator.BLUEPRINT_STYLE;
        if (mSceneDraw != null) {
            currentStyle = mSceneDraw.getCurrentStyle();
        }
        WidgetDecorator decorator = widgetCompanion.getWidgetDecorator(currentStyle);

        ConstraintAnchor leftAnchor = widget.getAnchor(ConstraintAnchor.Type.LEFT);
        ConstraintAnchor rightAnchor = widget.getAnchor(ConstraintAnchor.Type.RIGHT);
        if (leftAnchor != null && rightAnchor != null
                && leftAnchor.isConnected() && rightAnchor.isConnected()
                && leftAnchor.getTarget() != rightAnchor.getTarget()) {
            int begin =
                    WidgetInteractionTargets.constraintHandle(leftAnchor.getTarget()).getDrawX();
            int end = WidgetInteractionTargets.constraintHandle(rightAnchor.getTarget()).getDrawX();
            int width = widget.getDrawWidth();
            int delta = candidatePoint.x - begin;
            float percent = delta / (float) (end - begin - width);
            percent = Math.max(0, Math.min(1, percent));
            percent = snapPercent(percent);
            widget.setHorizontalBiasPercent(percent);
            decorator.updateBias();
        }
        ConstraintAnchor topAnchor = widget.getAnchor(ConstraintAnchor.Type.TOP);
        ConstraintAnchor bottomAnchor = widget.getAnchor(ConstraintAnchor.Type.BOTTOM);
        if (topAnchor != null && bottomAnchor != null
                && topAnchor.isConnected() && bottomAnchor.isConnected()
                && topAnchor.getTarget() != bottomAnchor.getTarget()) {
            int begin = WidgetInteractionTargets.constraintHandle(topAnchor.getTarget()).getDrawY();
            int end =
                    WidgetInteractionTargets.constraintHandle(bottomAnchor.getTarget()).getDrawY();
            int height = widget.getDrawHeight();
            int delta = candidatePoint.y - begin;
            float percent = delta / (float) (end - begin - height);
            percent = Math.max(0, Math.min(1, percent));
            percent = snapPercent(percent);
            widget.setVerticalBiasPercent(percent);
            decorator.updateBias();
        }
    }

    /**
     * Snap the percent value to common values
     *
     * @param percent
     * @return the modified percent value
     */
    private static float snapPercent(float percent) {
        // We'll snap on the following values:
        // 1/4, 1/3, 1/2, 2/3, 3/4
        // as well as percents
        int value = (int) (percent * 100);
        int slope = 2;
        if (Math.abs(value - 25) <= slope) {
            value = 25;
        }
        if (Math.abs(value - 33) <= slope) {
            value = 33;
        }
        if (Math.abs(value - 50) <= slope) {
            value = 50;
        }
        if (Math.abs(value - 66) <= slope) {
            value = 66;
        }
        if (Math.abs(value - 75) <= slope) {
            value = 75;
        }
        return (value / 100f);
    }

    /**
     * Need to be called when the mouse is released
     */
    public void mouseReleased() {
        mShowDecorations = true;
        mSnapCandidates.clear();
        mSimilarMargins.clear();
    }

    /**
     * Setter for the SceneDraw
     *
     * @param sceneDraw
     */
    public void setSceneDraw(SceneDraw sceneDraw) {
        mSceneDraw = sceneDraw;
    }

    /**
     * Indicates if we want to show decorations (anchor points, resize handles).
     * Typically we want to hide decorations when dragging a widget
     *
     * @return true if decorations can be shown, false otherwise
     */
    public boolean needToShowDecorations() {
        return mShowDecorations;
    }

    /**
     * Accessor to the gathered list of margins similar to the current one. We need to
     * access this to display them as we drag the widgets
     *
     * @return list of similar margins
     */
    public ArrayList<SnapCandidate> getSimilarMargins() {
        return mSimilarMargins;
    }

    /**
     * Accessor to the gathered list of potential snap candidates. We need to access this
     * to display the snap candidates as we drag the widgets
     *
     * @return list of snap candidates
     */
    public ArrayList<SnapCandidate> getSnapCandidates() {
        return mSnapCandidates;
    }

    /**
     * Returns true if the specified key event maps to one of the 4 keyboard
     * arrows (non-numeric keypad)
     */
    public static boolean isArrowKey(KeyEvent e) {
        int code = e.getKeyCode();
        return code >= KeyEvent.VK_LEFT && code <= KeyEvent.VK_DOWN;
    }

    /**
     * Returns a direction corresponding to the specified arrow key.
     * If the event does not map to one of the 4 keyboard arrows,
     * the returned direction will be either left or down.
     */
    public static Direction directionForArrowKey(KeyEvent e) {
        int index = Math.max(0, Math.min(e.getKeyCode(), KeyEvent.VK_DOWN) - KeyEvent.VK_LEFT);
        return Direction.values()[index];
    }

    /**
     * Move widget in the specified direction.
     *
     * @param widget     the widget to move
     * @param direction  the movement direction
     * @param snapToGrid snap to the material design grid
     */
    public void moveWidget(ConstraintWidget widget, Direction direction, boolean snapToGrid) {
        if (widget == null) {
            return;
        }

        int x = widget.getDrawX();
        int y = widget.getDrawY();

        int xOffset = 0;
        int yOffset = 0;

        switch (direction) {
            case LEFT:
                xOffset = -1;
                break;
            case UP:
                yOffset = -1;
                break;
            case RIGHT:
                xOffset = 1;
                break;
            case DOWN:
                yOffset = 1;
                break;
        }

        if (snapToGrid) {
            xOffset *= GRID_SPACING;
            yOffset *= GRID_SPACING;
        }

        widget.setDrawOrigin(x + xOffset, y + yOffset);
    }

}
