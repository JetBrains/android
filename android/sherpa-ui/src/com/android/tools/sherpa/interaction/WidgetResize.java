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

import com.google.tnt.solver.widgets.Animator;
import com.google.tnt.solver.widgets.ConstraintAnchor;
import com.google.tnt.solver.widgets.ConstraintWidget;
import com.google.tnt.solver.widgets.Guideline;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Encapsulate the resizing behaviour and resize snapping of widgets
 */
public class WidgetResize {

    private ArrayList<SnapCandidate> mSnapCandidates = new ArrayList<SnapCandidate>();

    /**
     * Resize the widget given the current mouse position
     *
     * @param widgets the list of known widgets
     * @param widget the widget we are resizing
     * @param posX   in android coordinate
     * @param posY   in android coordinate
     */
    public void resizeWidget(Collection<ConstraintWidget> widgets,
            ConstraintWidget widget, ResizeHandle handle,
            Rectangle originalBounds, int posX, int posY) {
        if (widget == null) {
            return;
        }
        mSnapCandidates.clear();
        Animator.setAnimationEnabled(false);
        int x = widget.getDrawX();
        int y = widget.getDrawY();
        int w = widget.getDrawWidth();
        int h = widget.getDrawHeight();

        ResizeHandle.Type resize = handle.getType();
        ConstraintAnchor left = widget.getAnchor(ConstraintAnchor.Type.LEFT);
        ConstraintAnchor top = widget.getAnchor(ConstraintAnchor.Type.TOP);
        ConstraintAnchor right = widget.getAnchor(ConstraintAnchor.Type.RIGHT);
        ConstraintAnchor bottom = widget.getAnchor(ConstraintAnchor.Type.BOTTOM);
        ConstraintAnchor baseline = widget.getAnchor(ConstraintAnchor.Type.BASELINE);
        boolean leftIsConnected = left != null && left.isConnected();
        boolean rightIsConnected = right != null && right.isConnected();
        boolean topIsConnected = top != null && top.isConnected();
        boolean bottomIsConnected = bottom != null && bottom.isConnected();
        boolean baselineIsConnected = baseline != null && baseline.isConnected();

        // Limits range of motion depending on which anchor is connected
        if (leftIsConnected && !rightIsConnected) {
            switch (resize) {
                case LEFT_TOP: {
                    resize = ResizeHandle.Type.TOP_SIDE;
                } break;
                case LEFT_BOTTOM: {
                    resize = ResizeHandle.Type.BOTTOM_SIDE;
                } break;
            }
        }
        if (rightIsConnected && !leftIsConnected) {
            switch (resize) {
                case RIGHT_TOP: {
                    resize = ResizeHandle.Type.TOP_SIDE;
                } break;
                case RIGHT_BOTTOM: {
                    resize = ResizeHandle.Type.BOTTOM_SIDE;
                } break;
            }
        }
        if ((topIsConnected || baselineIsConnected) && !bottomIsConnected) {
            switch (resize) {
                case LEFT_TOP: {
                    resize = ResizeHandle.Type.LEFT_SIDE;
                } break;
                case RIGHT_TOP: {
                    resize = ResizeHandle.Type.RIGHT_SIDE;
                } break;
            }
        }
        if (bottomIsConnected && !topIsConnected) {
            switch (resize) {
                case LEFT_BOTTOM: {
                    resize = ResizeHandle.Type.LEFT_SIDE;
                } break;
                case RIGHT_BOTTOM: {
                    resize = ResizeHandle.Type.RIGHT_SIDE;
                } break;
            }
        }
        switch (resize) {
            case LEFT_TOP: {
                int newX = Math.min(originalBounds.x + originalBounds.width
                        - widget.getMinWidth(), posX);
                newX = snapLeft(widgets, widget, newX, mSnapCandidates);
                int newY = Math.min(originalBounds.y + originalBounds.height
                        - widget.getMinHeight(), posY);
                newY = snapTop(widgets, widget, newY, mSnapCandidates);
                int newWidth = originalBounds.x + originalBounds.width - newX;
                int newHeight = originalBounds.y + originalBounds.height - newY;
                setNewFrame(widget, newX, newY, newWidth, newHeight);
            }
            break;
            case LEFT_BOTTOM: {
                int newX = Math.min(originalBounds.x + originalBounds.width
                        - widget.getMinWidth(), posX);
                newX = snapLeft(widgets, widget, newX, mSnapCandidates);
                int newWidth = originalBounds.x + originalBounds.width - newX;
                int newHeight = posY - originalBounds.y;
                newHeight = snapHeight(widgets, widget, newHeight, mSnapCandidates);
                setNewFrame(widget, newX, y, newWidth, newHeight);
            }
            break;
            case RIGHT_TOP: {
                int newY = Math.min(originalBounds.y + originalBounds.height
                        - widget.getMinHeight(), posY);
                newY = snapTop(widgets, widget, newY, mSnapCandidates);
                int newWidth = posX - originalBounds.x;
                newWidth = snapWidth(widgets, widget, newWidth, mSnapCandidates);
                int newHeight = originalBounds.y + originalBounds.height - newY;
                setNewFrame(widget, x, newY, newWidth, newHeight);
            }
            break;
            case RIGHT_BOTTOM: {
                int newWidth = posX - originalBounds.x;
                int newHeight = posY - originalBounds.y;
                newWidth = snapWidth(widgets, widget, newWidth, mSnapCandidates);
                newHeight = snapHeight(widgets, widget, newHeight, mSnapCandidates);
                setNewFrame(widget, x, y, newWidth, newHeight);
            }
            break;
            case LEFT_SIDE: {
                int newX = Math.min(originalBounds.x + originalBounds.width
                        - widget.getMinWidth(), posX);
                if (widget instanceof Guideline) {
                    newX = posX;
                }
                newX = snapLeft(widgets, widget, newX, mSnapCandidates);
                int newWidth = originalBounds.x + originalBounds.width - newX;
                setNewFrame(widget, newX, y, newWidth, h);
            }
            break;
            case RIGHT_SIDE: {
                int newWidth = posX - originalBounds.x;
                newWidth = snapWidth(widgets, widget, newWidth, mSnapCandidates);
                setNewFrame(widget, x, y, newWidth, h);
            }
            break;
            case TOP_SIDE: {
                int newY = Math.min(originalBounds.y + originalBounds.height
                        - widget.getMinHeight(), posY);
                if (widget instanceof Guideline) {
                    newY = posY;
                }
                newY = snapTop(widgets, widget, newY, mSnapCandidates);
                int newHeight = originalBounds.y + originalBounds.height - newY;
                setNewFrame(widget, x, newY, w, newHeight);
            }
            break;
            case BOTTOM_SIDE: {
                int newHeight = posY - originalBounds.y;
                newHeight = snapHeight(widgets, widget, newHeight, mSnapCandidates);
                setNewFrame(widget, x, y, w, newHeight);
            }
            break;
        }
    }

    /**
     * Convenience function to snap the left position
     * @param widgets list of known widgets
     * @param widget the widget we are operating on
     * @param left the left position we are evaluating
     * @param snapCandidates an array containing the list of snap candidates found
     * @return the new left position, possibly modified due to the snapping
     */
    private static int snapLeft(Collection<ConstraintWidget> widgets,
            ConstraintWidget widget, int left, ArrayList<SnapCandidate> snapCandidates) {
        return snapHorizontal(widgets, widget,
                widget.getAnchor(ConstraintAnchor.Type.LEFT),
                left, snapCandidates);
    }

    /**
     * Convenience function to snap the top position
     * @param widgets list of known widgets
     * @param widget the widget we are operating on
     * @param top the top position we are evaluating
     * @param snapCandidates an array containing the list of snap candidates found
     * @return the new top position, possibly modified due to the snapping
     */
    private static int snapTop(Collection<ConstraintWidget> widgets,
            ConstraintWidget widget, int top, ArrayList<SnapCandidate> snapCandidates) {
        return snapVertical(widgets, widget,
                widget.getAnchor(ConstraintAnchor.Type.TOP),
                top, snapCandidates);
    }

    /**
     * Convenience function to snap the width
     * @param widgets list of known widgets
     * @param widget the widget we are operating on
     * @param width the width we are evaluating
     * @param snapCandidates an array containing the list of snap candidates found
     * @return the new width, possibly modified due to the snapping
     */
    private static int snapWidth(Collection<ConstraintWidget> widgets,
            ConstraintWidget widget, int width, ArrayList<SnapCandidate> snapCandidates) {
        int rightPosition = widget.getDrawX() + width;
        rightPosition = snapHorizontal(widgets, widget,
                widget.getAnchor(ConstraintAnchor.Type.RIGHT),
                rightPosition, snapCandidates);
        width = rightPosition - widget.getDrawX();
        return width;
    }

    /**
     * Convenience function to snap the height
     * @param widgets list of known widgets
     * @param widget the widget we are operating on
     * @param height the height we are evaluating
     * @param snapCandidates an array containing the list of snap candidates found
     * @return the new height, possibly modified due to the snapping
     */
    private static int snapHeight(Collection<ConstraintWidget> widgets,
            ConstraintWidget widget, int height, ArrayList<SnapCandidate> snapCandidates) {
        int bottomPosition = widget.getDrawY() + height;
        bottomPosition = snapVertical(widgets, widget,
                widget.getAnchor(ConstraintAnchor.Type.BOTTOM),
                bottomPosition, snapCandidates);
        height = bottomPosition - widget.getDrawY();
        return height;
    }

    /**
     * Utility function to gather snap candidates on the horizontal axis
     *
     * @param widgets list of known widgets
     * @param widget the widget we are operating on
     * @param anchor the anchor we are operating on
     * @param position the position we are evaluating
     * @param snapCandidates an array containing the list of snap candidates found
     * @return the new position, possibly modified due to the snapping
     */
    private static int snapHorizontal(Collection<ConstraintWidget> widgets,
            ConstraintWidget widget, ConstraintAnchor anchor, int position,
            ArrayList<SnapCandidate> snapCandidates) {
        SnapCandidate candidate = new SnapCandidate();
        ConstraintHandle handle = WidgetInteractionTargets.constraintHandle(anchor);
        handle.setDrawX(position);
        SnapPlacement.snapAnchor(widgets, widget, anchor, candidate);
        if (candidate.target != null) {
            ConstraintHandle targetHandle = WidgetInteractionTargets.constraintHandle(candidate.target);
            int tx = candidate.x;
            if (targetHandle != null) {
                tx = targetHandle.getDrawX();
            }
            position = tx + candidate.margin;
            snapCandidates.add(candidate);
        }
        return position;
    }

    /**
     * Utility function to gather snap candidates on the vertical axis
     *
     * @param widgets list of known widgets
     * @param widget the widget we are operating on
     * @param anchor the anchor we are operating on
     * @param position the position we are evaluating
     * @param snapCandidates an array containing the list of snap candidates found
     * @return the new position, possibly modified due to the snapping
     */
    private static int snapVertical(Collection<ConstraintWidget> widgets,
            ConstraintWidget widget, ConstraintAnchor anchor, int position,
            ArrayList<SnapCandidate> snapCandidates) {
        SnapCandidate candidate = new SnapCandidate();
        ConstraintHandle handle = WidgetInteractionTargets.constraintHandle(anchor);
        if (handle == null) {
            return position;
        }
        handle.setDrawY(position);
        SnapPlacement.snapAnchor(widgets, widget, anchor, candidate);
        if (candidate.target != null) {
            ConstraintHandle targetHandle = WidgetInteractionTargets.constraintHandle(candidate.target);
            int ty = candidate.y;
            if (targetHandle != null) {
                ty = targetHandle.getDrawY();
            }
            position = ty + candidate.margin;
            snapCandidates.add(candidate);
        }
        return position;
    }

    /**
     * Utility function to apply a new frame to a widget
     *
     * @param widget    the widget we are resizing
     * @param newX
     * @param newY
     * @param newWidth
     * @param newHeight
     */
    private static void setNewFrame(ConstraintWidget widget, int newX, int newY,
            int newWidth, int newHeight) {
        if (newWidth < 0) {
            newWidth = 0;
        }
        if (newHeight < 0) {
            newHeight = 0;
        }
        widget.setDrawOrigin(newX, newY);
        widget.setDimension(newWidth, newHeight);
        if (widget.getWidth() <= widget.getMinWidth()) {
            widget.setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.WRAP_CONTENT);
        } else {
            widget.setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.FIXED);
        }
        if (widget.getHeight() <= widget.getMinHeight()) {
            widget.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.WRAP_CONTENT);
        } else {
            widget.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.FIXED);
        }
    }

    /**
     * Accessor to the gathered list of potential snap candidates. We need to access this
     * to display the snap candidates as we resize the widget
     *
     * @return list of snap candidates
     */
    public ArrayList<SnapCandidate> getSnapCandidates() {
        return mSnapCandidates;
    }

    /**
     * Need to be called when the mouse is released
     */
    public void mouseReleased() {
        mSnapCandidates.clear();
    }
}
