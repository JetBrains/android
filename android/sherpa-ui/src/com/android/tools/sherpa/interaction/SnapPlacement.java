/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.google.tnt.solver.widgets.ConstraintAnchor;
import com.google.tnt.solver.widgets.ConstraintWidget;
import com.google.tnt.solver.widgets.Guideline;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Encapsulate the behaviour for snapping the widget to constrainted positions when being moved.
 */
public class SnapPlacement {

    private static final double MAX_CONNECTION_DISTANCE = 100; // in android coordinates
    private static final double MAX_SNAP_MARGIN_DISTANCE = 80; // in android coordinates
    private static final boolean ALLOWS_ALL_SNAP_MARGIN = true;
    private static final int SNAP_MARGIN_INCREMENT = 8; // in android coordinates
    private static final int SNAP_MARGIN_MAX = 56; // in android coordinates
    private static final int CONNECTION_SLOPE = 4; // in android coordinates
    private static final int DEFAULT_MARGIN = 16; // in android coordinates

    /**
     * Constraint a candidate position for a given widget.
     * If the widget has existing connections, we'll allow moving on the corresponding axis
     * by modifying the margin of the connection; we'll also snap to WidgetsView.GRID_SPACING
     * the margin's value.
     * If the widget did not have connections on that axis, we'll try to snap the new position to
     * closeby widget. Otherwise, we'll snap the position on the base grid,
     * using WidgetsView.GRID_SPACING. The function will also fill in the snapCandidates
     * array with any SnapCandidate used.
     *
     * @param widgets        the list of known widgets
     * @param widget         the widget we operate on
     * @param candidatePoint the candidate new position
     * @param useGridSnap    flag to use or not snapping on the base grid
     * @param snapCandidates an array that will contain the snap candidates if any.
     * @param transform      the view transform
     */
    public static void snapWidget(Collection<ConstraintWidget> widgets,
            ConstraintWidget widget, Point candidatePoint, boolean useGridSnap,
            ArrayList<SnapCandidate> snapCandidates, ViewTransform transform) {
        if (widget instanceof Guideline) {
            return;
        }
        ConstraintAnchor left = widget.getAnchor(ConstraintAnchor.Type.LEFT);
        ConstraintAnchor right = widget.getAnchor(ConstraintAnchor.Type.RIGHT);
        if (left.isConnected() && right.isConnected()) {
            // do nothing, as movement is constrained
        } else {
            widget.setDrawX(candidatePoint.x);
            WidgetCompanion widgetCompanion = (WidgetCompanion) widget.getCompanionWidget();
            WidgetInteractionTargets widgetInteraction =
                    widgetCompanion.getWidgetInteractionTargets();
            widgetInteraction.updatePosition(transform);

            if (!checkHorizontalMarginsSnap(snapCandidates, widget, candidatePoint,
                    DEFAULT_MARGIN)) {
                if (!SnapPlacement.snapExistingHorizontalMargin(widget, candidatePoint)) {
                    SnapCandidate candidate = new SnapCandidate();
                    findSnap(widgets, widget, candidate, true);
                    if (candidate.target == null
                            || candidate.target.getType() == ConstraintAnchor.Type.CENTER_X
                            || candidate.target.getType() == ConstraintAnchor.Type.CENTER) {
                        // no anchor found, let's try to find margins
                        for (int i = SNAP_MARGIN_INCREMENT; i <= SNAP_MARGIN_MAX;
                                i += SNAP_MARGIN_INCREMENT) {
                            findSnapMargin(widgets, widget, candidate, false,
                                    ConstraintAnchor.Type.LEFT,
                                    ConstraintAnchor.Type.RIGHT, i, CONNECTION_SLOPE);
                            findSnapMargin(widgets, widget, candidate, false,
                                    ConstraintAnchor.Type.RIGHT,
                                    ConstraintAnchor.Type.LEFT, -i, CONNECTION_SLOPE);
                        }
                    }
                    if (!SnapPlacement.snapToHorizontalAnchor(candidatePoint, widget, candidate)) {
                        if (useGridSnap) {
                            SnapPlacement.snapHorizontalGrid(widget, candidatePoint);
                        }
                    } else {
                        snapCandidates.add(candidate);
                    }
                }
            }
        }
        ConstraintAnchor top = widget.getAnchor(ConstraintAnchor.Type.TOP);
        ConstraintAnchor bottom = widget.getAnchor(ConstraintAnchor.Type.BOTTOM);
        ConstraintAnchor baseline = widget.getAnchor(ConstraintAnchor.Type.BASELINE);
        if (baseline.isConnected() || (top.isConnected() && bottom.isConnected())) {
            // do nothing, as movement is constrained
        } else {
            widget.setDrawY(candidatePoint.y);
            WidgetCompanion widgetCompanion = (WidgetCompanion) widget.getCompanionWidget();
            WidgetInteractionTargets widgetInteraction =
                    widgetCompanion.getWidgetInteractionTargets();
            widgetInteraction.updatePosition(transform);

            if (!checkVerticalMarginsSnap(snapCandidates, widget, candidatePoint,
                    DEFAULT_MARGIN)) {
                if (!SnapPlacement.snapExistingVerticalMargin(widget, candidatePoint)) {
                    SnapCandidate candidate = new SnapCandidate();
                    findSnap(widgets, widget, candidate, false);
                    if (candidate.target == null
                            || candidate.target.getType() == ConstraintAnchor.Type.CENTER_Y
                            || candidate.target.getType() == ConstraintAnchor.Type.CENTER) {
                        // no anchor found, let's try to find margins
                        for (int i = SNAP_MARGIN_INCREMENT; i <= SNAP_MARGIN_MAX;
                                i += SNAP_MARGIN_INCREMENT) {
                            findSnapMargin(widgets, widget, candidate, true,
                                    ConstraintAnchor.Type.TOP,
                                    ConstraintAnchor.Type.BOTTOM, i, CONNECTION_SLOPE);
                            findSnapMargin(widgets, widget, candidate, true,
                                    ConstraintAnchor.Type.BOTTOM,
                                    ConstraintAnchor.Type.TOP, -i, CONNECTION_SLOPE);
                        }
                    }
                    if (!SnapPlacement.snapToVerticalAnchor(candidatePoint, widget, candidate)) {
                        if (useGridSnap) {
                            SnapPlacement.snapVerticalGrid(widget, candidatePoint);
                        }
                    } else {
                        snapCandidates.add(candidate);
                    }
                }
            }
        }
    }

    /**
     * Try to find snapping candidates for the given anchor
     *
     * @param widgets   the list of known widgets
     * @param widget    the widget we operate on
     * @param anchor    the anchor we are trying to snap
     * @param candidate the current candidate
     */
    public static void snapAnchor(Collection<ConstraintWidget> widgets, ConstraintWidget widget,
            ConstraintAnchor anchor, SnapCandidate candidate) {
        if (widget.getParent() != null) {
            if (!anchor.isVerticalAnchor()) {
                checkHorizontalParentMarginSnap(anchor,
                        ConstraintAnchor.Type.RIGHT, -DEFAULT_MARGIN, candidate);
                checkHorizontalParentMarginSnap(anchor,
                        ConstraintAnchor.Type.LEFT, DEFAULT_MARGIN, candidate);
            } else {
                checkVerticalParentMarginSnap(anchor,
                        ConstraintAnchor.Type.BOTTOM, -DEFAULT_MARGIN, candidate);
                checkVerticalParentMarginSnap(anchor,
                        ConstraintAnchor.Type.TOP, DEFAULT_MARGIN, candidate);
            }
        }
        for (ConstraintWidget w : widgets) {
            if (w == widget) {
                continue;
            }
            ArrayList<ConstraintAnchor> anchorsTarget = w.getAnchors();
            for (ConstraintAnchor at : anchorsTarget) {
                snapCheck(anchor, at, candidate, CONNECTION_SLOPE);
            }
        }
    }

    /**
     * Given a margin and its orientation, gather all similar margins among the widgets.
     * the margins list will be filled with the margins we found.
     *
     * @param widgets    the list of known widgets
     * @param margins    the list of margins we found
     * @param margin     the value of the margin we are looking for
     * @param isVertical the orientation of the margin we are looking for
     */
    public static void gatherMargins(Collection<ConstraintWidget> widgets,
            ArrayList<SnapCandidate> margins, int margin, boolean isVertical) {
        margin = Math.abs(margin);
        if (margin == 0) {
            return;
        }
        // TODO: we should cache the margins found
        ArrayList<SnapCandidate> foundMargins = new ArrayList<SnapCandidate>();
        for (ConstraintWidget w1 : widgets) {
            for (ConstraintAnchor a1 : w1.getAnchors()) {
                if (!a1.isSideAnchor()) {
                    continue;
                }
                if (a1.isVerticalAnchor() != isVertical) {
                    continue;
                }
                for (ConstraintWidget w2 : widgets) {
                    for (ConstraintAnchor a2 : w2.getAnchors()) {
                        if (!a2.isSideAnchor()) {
                            continue;
                        }
                        if (!a2.isSimilarDimensionConnection(a1)) {
                            continue;
                        }
                        ConstraintHandle h1 = WidgetInteractionTargets.constraintHandle(a1);
                        ConstraintHandle h2 = WidgetInteractionTargets.constraintHandle(a2);
                        if (h1 == null || h2 == null) {
                            continue;
                        }
                        int currentMargin = h1.getStraightDistanceFrom(h2);
                        if (Math.abs(currentMargin) == margin) {
                            SnapCandidate candidate = new SnapCandidate();
                            candidate.source = a1;
                            candidate.target = a2;
                            candidate.margin = currentMargin;
                            foundMargins.add(candidate);
                        }
                    }
                }
            }
        }
        for (SnapCandidate c1 : foundMargins) {
            boolean insert = true;
            for (SnapCandidate c2 : margins) {
                // if we have the opposite margin, don't use it
                if ((Math.abs(c1.margin) == Math.abs(c2.margin))
                        && ((c2.source == c1.target && c2.target == c1.source)
                        || (c2.source == c1.source && c2.target == c1.target))) {
                    insert = false;
                    break;
                }
                // if we have margins for the same position, don't use them
                if (c1.source.isSimilarDimensionConnection(c2.source)
                        && c1.margin == c2.margin) {
                    ConstraintHandle sourceHandle1 =
                            WidgetInteractionTargets.constraintHandle(c1.source);
                    ConstraintHandle targetHandle1 =
                            WidgetInteractionTargets.constraintHandle(c1.target);
                    ConstraintHandle sourceHandle2 =
                            WidgetInteractionTargets.constraintHandle(c2.source);
                    ConstraintHandle targetHandle2 =
                            WidgetInteractionTargets.constraintHandle(c2.target);
                    if (c1.source.isVerticalAnchor()) {
                        if (Math.min(sourceHandle1.getDrawY(), targetHandle1.getDrawY())
                                == Math.min(sourceHandle2.getDrawY(), targetHandle2.getDrawY())) {
                            insert = false;
                            break;
                        }
                    } else if (Math.min(sourceHandle1.getDrawX(), targetHandle1.getDrawX())
                            == Math.min(sourceHandle2.getDrawX(), targetHandle2.getDrawX())) {
                        insert = false;
                        break;
                    }
                }
            }
            if (insert) {
                margins.add(c1);
            }
        }
    }

    /**
     * Internal utility function to create a snap candidate on the fly for a margin.
     *
     * @param widget the widget we operate on
     * @param type   the type of ConstraintAnchor we want to use for the target
     * @param x      the horizontal position for the ConstraintAnchor target
     * @param y      the vertical position for the ConstraintAnchor target
     * @return a new instance of SnapCandidate representing the margin
     */
    private static SnapCandidate createSnapCandidate(ConstraintWidget widget,
            ConstraintAnchor.Type type, int x, int y) {
        SnapCandidate candidate = new SnapCandidate();
        candidate.source = widget.getAnchor(type);
        ConstraintWidget owner = widget.getParent();
        ConstraintAnchor anchor = new ConstraintAnchor(owner, type);
        candidate.x = x;
        candidate.y = y;
        candidate.target = anchor;
        return candidate;
    }

    /**
     * Check the candidate position against the left and right margin of the widget's parent.
     *
     * @param snapCandidates an array that will contain the snap candidate if any.
     * @param widget         the widget we operate on
     * @param candidatePoint the candidate new position
     * @param margin         the value of the margin
     * @return true if we snapped against the margin, false otherwise
     */
    private static boolean checkHorizontalMarginsSnap(
            ArrayList<SnapCandidate> snapCandidates,
            ConstraintWidget widget,
            Point candidatePoint, int margin) {
        if (widget.getParent() == null) {
            return false;
        }
        int parentX1 = widget.getParent().getDrawX() + margin;
        int parentX2 = widget.getParent().getDrawRight() - margin;
        if (Math.abs(widget.getDrawX() - parentX1) < CONNECTION_SLOPE) {
            candidatePoint.x = parentX1;
            SnapCandidate candidate = createSnapCandidate(widget,
                    ConstraintAnchor.Type.LEFT, parentX1, 0);
            candidate.padding = margin;
            snapCandidates.add(candidate);
            return true;
        }
        if (Math.abs(widget.getDrawX() + widget.getDrawWidth() - parentX2) < CONNECTION_SLOPE) {
            candidatePoint.x = parentX2 - widget.getDrawWidth();
            SnapCandidate candidate = createSnapCandidate(widget,
                    ConstraintAnchor.Type.RIGHT, parentX2, 0);
            candidate.padding = margin;
            snapCandidates.add(candidate);
            return true;
        }
        return false;
    }

    /**
     * Check the candidate position against the top and bottom margin of the widget's parent.
     *
     * @param snapCandidates an array that will contain the snap candidate if any.
     * @param widget         the widget we operate on
     * @param candidatePoint the candidate new position
     * @param margin         the value of the margin
     * @return true if we snapped against the margin, false otherwise
     */
    private static boolean checkVerticalMarginsSnap(
            ArrayList<SnapCandidate> snapCandidates,
            ConstraintWidget widget,
            Point candidatePoint, int margin) {
        if (widget.getParent() == null) {
            return false;
        }
        int parentY1 = widget.getParent().getDrawY() + margin;
        int parentY2 = widget.getParent().getDrawBottom() - margin;
        if (Math.abs(widget.getDrawY() - parentY1) < CONNECTION_SLOPE) {
            candidatePoint.y = parentY1;
            SnapCandidate candidate = createSnapCandidate(widget,
                    ConstraintAnchor.Type.TOP, 0, parentY1);
            candidate.padding = margin;
            snapCandidates.add(candidate);
            return true;
        }
        if (Math.abs(widget.getDrawY() + widget.getHeight() - parentY2) < CONNECTION_SLOPE) {
            candidatePoint.y = parentY2 - widget.getHeight();
            SnapCandidate candidate = createSnapCandidate(widget,
                    ConstraintAnchor.Type.BOTTOM, 0, parentY2);
            candidate.padding = margin;
            snapCandidates.add(candidate);
            return true;
        }
        return false;
    }

    /**
     * Check to snap on the horizontal internal margins of a parent (used when resizing)
     *
     * @param anchor    the anchor we are trying to snap
     * @param type      the type of anchor on the parent that we want to check against
     * @param margin    the margin we'll use for the internal margin
     * @param candidate the current candidate that we can fill in
     */
    private static void checkHorizontalParentMarginSnap(ConstraintAnchor anchor,
            ConstraintAnchor.Type type,
            int margin, SnapCandidate candidate) {
        ConstraintWidget widget = anchor.getOwner();
        if (widget.getParent() == null) {
            return;
        }
        ConstraintAnchor targetParent = widget.getParent().getAnchor(type);
        ConstraintHandle targetParentHandle =
                WidgetInteractionTargets.constraintHandle(targetParent);
        ConstraintHandle anchorHandle = WidgetInteractionTargets.constraintHandle(anchor);

        ConstraintAnchor target = new ConstraintAnchor(widget.getParent(), type);
        int tx = targetParentHandle.getDrawX() + margin;
        int ty = targetParentHandle.getDrawY();

        int distance = Math.abs(anchorHandle.getDrawX() - tx);
        if (distance <= CONNECTION_SLOPE) {
            candidate.distance = distance;
            candidate.target = target;
            candidate.source = anchor;
            candidate.x = tx;
            candidate.y = ty;
        }
    }

    /**
     * Check to snap on the vertical internal margins of a parent (used when resizing)
     *
     * @param anchor    the anchor we are trying to snap
     * @param type      the type of anchor on the parent that we want to check against
     * @param margin    the margin we'll use for the internal margin
     * @param candidate the current candidate that we can fill in
     */
    private static void checkVerticalParentMarginSnap(ConstraintAnchor anchor,
            ConstraintAnchor.Type type,
            int margin, SnapCandidate candidate) {
        ConstraintWidget widget = anchor.getOwner();
        if (widget.getParent() == null) {
            return;
        }
        ConstraintAnchor targetParent = widget.getParent().getAnchor(type);
        ConstraintHandle targetParentHandle =
                WidgetInteractionTargets.constraintHandle(targetParent);
        ConstraintHandle anchorHandle = WidgetInteractionTargets.constraintHandle(anchor);
        ConstraintAnchor target = new ConstraintAnchor(widget.getParent(), type);
        int tx = targetParentHandle.getDrawX();
        int ty = targetParentHandle.getDrawY() + margin;
        int distance = Math.abs(anchorHandle.getDrawY() - ty);
        if (distance <= CONNECTION_SLOPE) {
            candidate.distance = distance;
            candidate.target = target;
            candidate.source = anchor;
            candidate.x = tx;
            candidate.y = ty;
        }
    }

    /**
     * Utility function iterating through the anchors of a widget, comparing their position
     * with all the anchors of other widgets, and calling the snapCheck() function.
     *
     * @param widgets         the list of known widgets
     * @param widget          the widget we operate on
     * @param candidate       the SnapCandidate instance we need to fill
     * @param checkHorizontal flag to decide which type of anchors we are checking. Set to
     *                        true to check only horizontal anchors, false to check only vertical
     *                        anchors.
     */
    private static void findSnap(Collection<ConstraintWidget> widgets,
            ConstraintWidget widget, SnapCandidate candidate, boolean checkHorizontal) {
        ArrayList<ConstraintAnchor> anchorsSource = widget.getAnchors();
        for (ConstraintWidget w : widgets) {
            if (w == widget) {
                continue;
            }
            ArrayList<ConstraintAnchor> anchorsTarget = w.getAnchors();
            for (ConstraintAnchor as : anchorsSource) {
                if (checkHorizontal && as.isVerticalAnchor()) {
                    // either filter vertical anchors...
                    continue;
                } else if (!checkHorizontal && !as.isVerticalAnchor()) {
                    // ...or filter horizontal anchors
                    continue;
                }
                for (ConstraintAnchor at : anchorsTarget) {
                    snapCheck(as, at, candidate, CONNECTION_SLOPE);
                }
            }
        }
    }

    /**
     * Utility function iterating through the widgets, comparing the specified anchors,
     * and finding snapping candidate on the indicated margin.
     * <p/>
     * TODO: iterate only on the closest widget (right now we iterate on all of them
     * and *then* check the distance)
     *
     * @param widgets      the list of known widgets
     * @param widget       the widget we operate on
     * @param candidate    the SnapCandidate instance we need to fill
     * @param isVertical   flag set indicating if we are looking at horizontal or vertical anchors
     * @param sourceAnchor the anchor type we are looking at on the widget
     * @param targetAnchor the anchor type we will compare to
     * @param margin       the margin value we are trying to snap on
     * @param slope        the allowed slope
     */
    private static void findSnapMargin(Collection<ConstraintWidget> widgets,
            ConstraintWidget widget, SnapCandidate candidate, boolean isVertical,
            ConstraintAnchor.Type sourceAnchor, ConstraintAnchor.Type targetAnchor,
            int margin, int slope) {
        if (widget instanceof Guideline) {
            return;
        }
        ConstraintAnchor source = widget.getAnchor(sourceAnchor);
        for (ConstraintWidget w : widgets) {
            if (w == widget) {
                continue;
            }
            ConstraintAnchor target = w.getAnchor(targetAnchor);
            if (target == null) { // for e.g. guidelines
                continue;
            }
            ConstraintHandle sourceHandle = WidgetInteractionTargets.constraintHandle(source);
            ConstraintHandle targetHandle = WidgetInteractionTargets.constraintHandle(target);
            if (sourceHandle == null || targetHandle == null) {
                continue;
            }
            int anchorDistance = sourceHandle.getDrawX() - targetHandle.getDrawX() - margin;
            if (isVertical) {
                anchorDistance = sourceHandle.getDrawY() - targetHandle.getDrawY() - margin;
            }
            if (anchorDistance < 0) {
                continue;
            }
            int minDistance = sourceHandle.getDistanceFrom(target.getOwner());
            double distance =
                    Math.sqrt(anchorDistance * anchorDistance + minDistance * minDistance);
            if (target.getOwner() instanceof Guideline) {
                distance = Math.sqrt(anchorDistance * anchorDistance);
            }
            if (anchorDistance < slope && distance <= candidate.distance
                    && (ALLOWS_ALL_SNAP_MARGIN || distance < MAX_SNAP_MARGIN_DISTANCE)) {
                if (candidate.target == null
                        || (candidate.margin > margin
                            || (candidate.target.getType() == ConstraintAnchor.Type.CENTER_X
                                || candidate.target.getType() == ConstraintAnchor.Type.CENTER
                                || candidate.target.getType() == ConstraintAnchor.Type.CENTER_Y))) {
                    candidate.distance = distance;
                    candidate.target = target;
                    candidate.source = source;
                    candidate.margin = margin;
                }
            }
        }
    }

    /**
     * Check the possibility of snapping between a source anchor and a target anchor. They
     * will snap if they are close enough (based on the slope value), and if their distance is
     * less than the current SnapCandidate.
     *
     * @param source    the source anchor we are checking
     * @param target    the target anchor we are looking at
     * @param candidate the current snap candidate information
     * @param slope     a value allowing some slope to consider a target to be close enough to the source
     */
    private static void snapCheck(ConstraintAnchor source, ConstraintAnchor target,
            SnapCandidate candidate, int slope) {
        if (!target.isSnapCompatibleWith(source) || target.getOwner() == source.getOwner()) {
            return;
        }
        ConstraintHandle handleSource = WidgetInteractionTargets.constraintHandle(source);
        ConstraintHandle handleTarget = WidgetInteractionTargets.constraintHandle(target);
        int anchorDistance = handleSource.getStraightDistanceFrom(handleTarget);
        int minDistance = handleSource.getDistanceFrom(target.getOwner());
        if (target.getOwner() instanceof Guideline) {
            minDistance = 0;
        }
        double distance = Math.sqrt(anchorDistance * anchorDistance + minDistance * minDistance);
        boolean targetBelongsToParent = source.getOwner().getParent() == target.getOwner();
        if (anchorDistance < slope && distance <= candidate.distance
                && (targetBelongsToParent || (distance < MAX_CONNECTION_DISTANCE))) {
            // we consider a target as a candidate if the anchor distance are below the slope
            // as well as smaller than the current candidate's distance.
            // We also limit candidates if they are further than MAX_CONNECTION_DISTANCE,
            // unless they belong to the parent of the source (i.e. we want the center connection
            // of the current window to always be considered as a candidate, regardless of the size
            // of the window...)
            if (candidate.target != null) {
                boolean currentTargetBelongsToParent =
                        source.getOwner().getParent() == candidate.target.getOwner();
                if (currentTargetBelongsToParent) {
                    // we'll prefer to keep the current candidate if it belongs to the parent...
                    return;
                }
                if (candidate.distance == distance) {
                    // if we have a similar distance, we need to check which candidate we currently
                    // have -- essentially, we want to prioritize anchors from the parent, as well
                    // as abiding by the anchor's priority level
                    if (!targetBelongsToParent
                            && candidate.target.getSnapPriorityLevel() >
                            target.getSnapPriorityLevel()) {
                        return;
                    }
                }
            }
            candidate.distance = distance;
            candidate.target = target;
            candidate.source = source;
        }
    }

    /**
     * Snap the candidate horizontal position to a candidate anchor if it exists.
     *
     * @param candidatePoint the candidate position we might modify
     * @param widget         the widget we are working on
     * @param candidate      the SnapCandidate we are evaluating
     * @return true if we did snap to an anchor
     */
    private static boolean snapToHorizontalAnchor(Point candidatePoint,
            ConstraintWidget widget, SnapCandidate candidate) {
        if (candidate.target == null) {
            return false;
        }
        int x1 = WidgetInteractionTargets.constraintHandle(candidate.source).getDrawX();
        int x2 = WidgetInteractionTargets.constraintHandle(candidate.target).getDrawX() +
                candidate.margin;
        int distance = x2 - x1;
        candidatePoint.x = widget.getDrawX() + distance;
        return true;
    }

    /**
     * Snap the candidate vertical position to a candidate anchor if it exists.
     *
     * @param candidatePoint the candidate position we might modify
     * @param widget         the widget we are working on
     * @param candidate      the SnapCandidate we are evaluating
     * @return true if we did snap to an anchor
     */
    private static boolean snapToVerticalAnchor(Point candidatePoint,
            ConstraintWidget widget, SnapCandidate candidate) {
        if (candidate.target == null) {
            return false;
        }
        int y1 = WidgetInteractionTargets.constraintHandle(candidate.source).getDrawY();
        int y2 = WidgetInteractionTargets.constraintHandle(candidate.target).getDrawY() +
                candidate.margin;
        int distance = y2 - y1;
        candidatePoint.y = widget.getDrawY() + distance;
        return true;
    }

    /**
     * Snap the widget on the horizontal axis to WidgetsView.GRID_SPACING
     *
     * @param widget         the widget we operate on
     * @param candidatePoint the candidate new position
     * @return true if we changed the position on the horizontal axis
     */
    private static boolean snapHorizontalGrid(ConstraintWidget widget, Point candidatePoint) {
        int x = candidatePoint.x;
        x = (x / SceneDraw.GRID_SPACING) * SceneDraw.GRID_SPACING;
        candidatePoint.x = x;
        return true;
    }

    /**
     * Snap the widget on the vertical axis to WidgetsView.GRID_SPACING
     *
     * @param widget         the widget we operate on
     * @param candidatePoint the candidate new position
     * @return true if we changed the position on the vertical axis
     */
    private static boolean snapVerticalGrid(ConstraintWidget widget, Point candidatePoint) {
        int y = candidatePoint.y;
        y = (y / SceneDraw.GRID_SPACING) * SceneDraw.GRID_SPACING;
        candidatePoint.y = y;
        return true;
    }

    /**
     * Try move the widget on the horizontal axis, snapping an existing margin
     * to WidgetsView.GRID_SPACING
     *
     * @param widget         the widget we operate on
     * @param candidatePoint the candidate new position
     * @return true if we changed the margin on the horizontal axis
     */
    private static boolean snapExistingHorizontalMargin(ConstraintWidget widget,
            Point candidatePoint) {
        int x = candidatePoint.x;
        ConstraintAnchor left = widget.getAnchor(ConstraintAnchor.Type.LEFT);
        ConstraintAnchor right = widget.getAnchor(ConstraintAnchor.Type.RIGHT);
        boolean snapped = false;
        if (left.isConnected() && right.isConnected()) {
            // we do nothing in this case.
        } else if (left != null && left.isConnected()) {
            int x1 = x;
            int x2 = WidgetInteractionTargets.constraintHandle(left.getTarget()).getDrawX();
            int margin = ((x1 - x2) / SceneDraw.GRID_SPACING) * SceneDraw.GRID_SPACING;
            if (margin < 0) {
                margin = 0;
            }
            left.setMargin(margin);
            snapped = true;
        } else if (right != null && right.isConnected()) {
            int x1 = x + widget.getDrawWidth();
            int x2 = WidgetInteractionTargets.constraintHandle(right.getTarget()).getDrawX();
            int margin = ((x2 - x1) / SceneDraw.GRID_SPACING) * SceneDraw.GRID_SPACING;
            if (margin < 0) {
                margin = 0;
            }
            right.setMargin(margin);
            snapped = true;
        }
        return snapped;
    }

    /**
     * Try move the widget on the vertical axis, snapping an existing margin
     * to WidgetsView.GRID_SPACING
     *
     * @param widget         the widget we operate on
     * @param candidatePoint the candidate new position
     * @return true if we changed the margin on the vertical axis
     */
    private static boolean snapExistingVerticalMargin(ConstraintWidget widget,
            Point candidatePoint) {
        int y = candidatePoint.y;
        boolean snapped = false;
        ConstraintAnchor top = widget.getAnchor(ConstraintAnchor.Type.TOP);
        ConstraintAnchor bottom = widget.getAnchor(ConstraintAnchor.Type.BOTTOM);
        if (top.isConnected() && bottom.isConnected()) {
            // we do nothing in this case
        } else if (top.isConnected()) {
            int y1 = y;
            int y2 = WidgetInteractionTargets.constraintHandle(top.getTarget()).getDrawY();
            int margin = ((y1 - y2) / SceneDraw.GRID_SPACING) * SceneDraw.GRID_SPACING;
            if (margin < 0) {
                margin = 0;
            }
            top.setMargin(margin);
            snapped = true;
        } else if (bottom.isConnected()) {
            int y1 = y + widget.getHeight();
            int y2 = WidgetInteractionTargets.constraintHandle(bottom.getTarget()).getDrawY();
            int margin = ((y2 - y1) / SceneDraw.GRID_SPACING) * SceneDraw.GRID_SPACING;
            if (margin < 0) {
                margin = 0;
            }
            bottom.setMargin(margin);
            snapped = true;
        }
        return snapped;
    }

}
