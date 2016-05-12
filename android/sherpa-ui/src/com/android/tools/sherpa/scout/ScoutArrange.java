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
package com.android.tools.sherpa.scout;

import android.support.constraint.solver.widgets.ConstraintAnchor;
import android.support.constraint.solver.widgets.ConstraintWidget;
import android.support.constraint.solver.widgets.Guideline;
import android.support.constraint.solver.widgets.WidgetContainer;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;

/**
 * This implements the standard arrange functions
 */
public class ScoutArrange {
    static Comparator<ConstraintWidget> sSortY = (w1, w2) -> w1.getY() - w2.getY();
    static Comparator<ConstraintWidget> sSortX = (w1, w2) -> w1.getX() - w2.getX();

    /**
     * Perform various types of arrangements on a selection of widgets
     *
     * @param type             the type of change
     * @param widgetList       the list of selected widgets
     * @param applyConstraints if true apply related constraints (except expand and pack)
     */
    public static void align(Scout.Arrange type, ArrayList<ConstraintWidget> widgetList,
            boolean applyConstraints) {
        int margin = Scout.getMargin();
        ConstraintWidget[] widgets = new ConstraintWidget[widgetList.size()];
        widgets = widgetList.toArray(widgets);
        switch (type) {
            case AlignHorizontallyCenter:
            case AlignHorizontallyLeft:
            case AlignHorizontallyRight:
                Arrays.sort(widgets, sSortY);
                if (rootDistance(widgets[0]) > rootDistance(widgets[widgets.length - 1])) {
                    reverse(widgets);
                }
                break;
            case DistributeVertically:
                Arrays.sort(widgets, sSortY);
                break;
            case AlignVerticallyTop:
            case AlignVerticallyMiddle:
            case AlignBaseline:
            case AlignVerticallyBottom:
                Arrays.sort(widgets, sSortX);
                if (rootDistance(widgets[0]) > rootDistance(widgets[widgets.length - 1])) {
                    reverse(widgets);
                }
                break;
            case DistributeHorizontally:
                Arrays.sort(widgets, sSortX);
                break;
        }

        switch (type) {
            case CenterHorizontally: {
                Rectangle rectangle = new Rectangle();
                WidgetContainer parent = (WidgetContainer) widgets[0].getParent();
                ConstraintWidget[] pears = new ConstraintWidget[parent.getChildren().size()];
                pears = parent.getChildren().toArray(pears);

                for (ConstraintWidget widget : widgets) {
                    rectangle.x = widget.getX();
                    rectangle.y = widget.getY();
                    rectangle.width = widget.getWidth();
                    rectangle.height = widget.getHeight();
                    int westDistance = gap(Direction.WEST, rectangle, pears);
                    int eastDistance = gap(Direction.EAST, rectangle, pears);
                    int x = widget.getX();

                    if (applyConstraints) {
                        ConstraintWidget westConnect =
                                gapWidget(Direction.WEST, rectangle, pears);
                        ConstraintWidget eastConnect =
                                gapWidget(Direction.EAST, rectangle, pears);

                        ConstraintAnchor.Type dir = ConstraintAnchor.Type.RIGHT;
                        if (westConnect == parent) {
                            dir = ConstraintAnchor.Type.LEFT;
                        }
                        widget.connect(ConstraintAnchor.Type.LEFT, westConnect, dir, 0);
                        dir = ConstraintAnchor.Type.LEFT;
                        if (eastConnect == parent) {
                            dir = ConstraintAnchor.Type.RIGHT;
                        }
                        widget.connect(ConstraintAnchor.Type.RIGHT, eastConnect, dir, 0);
                    } else {
                        widget.setX(x + (eastDistance - westDistance) / 2);
                    }
                }
            }
            break;
            case CenterVertically: {
                Rectangle rectangle = new Rectangle();
                WidgetContainer parent = (WidgetContainer) widgets[0].getParent();
                ConstraintWidget[] pears = new ConstraintWidget[parent.getChildren().size()];
                pears = parent.getChildren().toArray(pears);

                for (ConstraintWidget widget : widgets) {
                    rectangle.x = widget.getX();
                    rectangle.y = widget.getY();
                    rectangle.width = widget.getWidth();
                    rectangle.height = widget.getHeight();
                    int northDistance = gap(Direction.NORTH, rectangle, pears);
                    int southDistance = gap(Direction.SOUTH, rectangle, pears);
                    int Y = widget.getY();

                    if (applyConstraints) {
                        ConstraintWidget northConnect =
                                gapWidget(Direction.NORTH, rectangle, pears);
                        ConstraintWidget southConnect =
                                gapWidget(Direction.SOUTH, rectangle, pears);

                        ConstraintAnchor.Type dir = ConstraintAnchor.Type.BOTTOM;
                        if (northConnect == parent) {
                            dir = ConstraintAnchor.Type.TOP;
                        }
                        widget.connect(ConstraintAnchor.Type.TOP, northConnect, dir, 0);
                        dir = ConstraintAnchor.Type.TOP;
                        if (southConnect == parent) {
                            dir = ConstraintAnchor.Type.BOTTOM;
                        }
                        widget.connect(ConstraintAnchor.Type.BOTTOM, southConnect, dir, 0);
                    } else {
                        widget.setY(Y + (southDistance - northDistance) / 2);
                    }
                }
            }
            break;
            case CenterHorizontallyInParent: {
                for (ConstraintWidget widget : widgets) {
                    int parentWidth = widget.getParent().getWidth();
                    int width = widget.getWidth();
                    widget.setX((parentWidth - width) / 2);
                    if (applyConstraints) {
                        widget.connect(ConstraintAnchor.Type.CENTER_X, widget.getParent(),
                                ConstraintAnchor.Type.CENTER_X, 0);
                    }
                }
            }
            break;
            case CenterVerticallyInParent: {
                for (ConstraintWidget widget : widgets) {
                    int parentHeight = widget.getParent().getHeight();
                    int height = widget.getHeight();
                    widget.setY((parentHeight - height) / 2);
                    if (applyConstraints) {
                            widget.connect(ConstraintAnchor.Type.CENTER_Y, widget.getParent(),
                                    ConstraintAnchor.Type.CENTER_Y, 0);
                    }
                }
            }
            break;
            case AlignHorizontallyCenter: {
                int count = 0;
                float avg = 0;
                for (ConstraintWidget widget : widgets) {
                    avg += widget.getX() + widget.getWidth() / 2.0f;
                    count++;
                }
                avg /= count;

                ConstraintWidget previousWidget = null;
                for (ConstraintWidget widget : widgets) {
                    float current = widget.getWidth() / 2.0f;
                    widget.setX((int) (avg - current));
                    if (applyConstraints) {
                        if (previousWidget != null) {
                            widget.connect(ConstraintAnchor.Type.CENTER_X, previousWidget,
                                    ConstraintAnchor.Type.CENTER_X, 0);
                        }
                    }
                    previousWidget = widget;
                }
            }
            break;
            case AlignHorizontallyLeft: {
                int min = Integer.MAX_VALUE;
                for (ConstraintWidget widget : widgets) {
                    min = Math.min(min, widget.getX());
                }
                ConstraintWidget previousWidget = null;
                for (ConstraintWidget widget : widgets) {
                    widget.setX(min);
                    if (applyConstraints) {
                        if (previousWidget != null) {
                            widget.resetAnchor(widget.getAnchor(ConstraintAnchor.Type.RIGHT));
                            widget.connect(ConstraintAnchor.Type.LEFT, previousWidget,
                                    ConstraintAnchor.Type.LEFT, 0);
                        }
                    }
                    previousWidget = widget;
                }
            }
            break;
            case AlignHorizontallyRight: {
                int max = Integer.MIN_VALUE;
                for (ConstraintWidget widget : widgets) {
                    max = Math.max(max, widget.getX() + widget.getWidth());
                }
                ConstraintWidget previousWidget = null;
                for (ConstraintWidget widget : widgets) {
                    float current = widget.getWidth();
                    widget.setX((int) (max - current));
                    if (applyConstraints) {
                        if (previousWidget != null) {
                            widget.resetAnchor(widget.getAnchor(ConstraintAnchor.Type.LEFT));
                            widget.connect(ConstraintAnchor.Type.RIGHT, previousWidget,
                                    ConstraintAnchor.Type.RIGHT, 0);
                        }
                    }
                    previousWidget = widget;
                }
            }
            break;
            case AlignVerticallyTop: {
                int min = Integer.MAX_VALUE;
                for (ConstraintWidget widget : widgets) {
                    min = Math.min(min, widget.getY());
                }
                ConstraintWidget previousWidget = null;
                for (ConstraintWidget widget : widgets) {
                    widget.setY(min);
                    if (applyConstraints) {
                        if (previousWidget != null) {
                            widget.resetAnchor(widget.getAnchor(ConstraintAnchor.Type.BOTTOM));
                            widget.connect(ConstraintAnchor.Type.TOP, previousWidget,
                                    ConstraintAnchor.Type.TOP, 0);
                        }
                    }
                    previousWidget = widget;
                }
            }
            break;
            case AlignVerticallyMiddle: {
                int count = 0;
                float avg = 0;
                for (ConstraintWidget widget : widgets) {
                    avg += widget.getY() + widget.getHeight() / 2.0f;
                    count++;
                }
                avg /= count;
                ConstraintWidget previousWidget = null;
                for (ConstraintWidget widget : widgets) {
                    float current = widget.getHeight() / 2.0f;
                    widget.setY((int) (avg - current));
                    if (applyConstraints) {
                        if (previousWidget != null) {
                            widget.connect(ConstraintAnchor.Type.CENTER_Y, previousWidget,
                                    ConstraintAnchor.Type.CENTER_Y, 0);
                        }
                    }
                    previousWidget = widget;
                }
            }
            break;
            case AlignBaseline: {
                int count = 0;
                float avg = 0;
                int number_of_constrained = 0;
                ConstraintWidget fixedWidget = null;
                for (ConstraintWidget widget : widgets) {
                    if (isVerticallyConstrained(widget)) {
                        number_of_constrained++;
                        fixedWidget = widget;
                    }
                    avg += widget.getY() + widget.getBaselineDistance();
                    count++;
                }
                avg /= count;
                // if one is already constrained move the rest to it
                if (number_of_constrained == 1) {
                    avg = fixedWidget.getY() + fixedWidget.getBaselineDistance();
                }
                ConstraintWidget previousWidget = null;
                if (!applyConstraints || number_of_constrained == 0) {
                    for (ConstraintWidget widget : widgets) {
                        float baseline = widget.getBaselineDistance();
                        widget.setY((int) (avg - baseline));
                        if (applyConstraints) {
                            if (previousWidget != null) {
                                widget.connect(ConstraintAnchor.Type.BASELINE, previousWidget,
                                        ConstraintAnchor.Type.BASELINE, 0);
                            }
                        }
                        previousWidget = widget;
                    }
                } else { // if you are creating constraints and some are already constrained
                    // Build a list of constrained and unconstrained widgets
                    ArrayList<ConstraintWidget> unconstrained = new ArrayList<>();
                    ArrayList<ConstraintWidget> constrained = new ArrayList<>();
                    for (ConstraintWidget widget : widgets) {
                        if (isVerticallyConstrained(widget)) {
                            constrained.add(widget);
                        } else {
                            unconstrained.add(widget);
                        }
                    }
                    // one by one constrain widgets by finding the closest between the two list
                    while (!unconstrained.isEmpty()) {
                        ConstraintWidget to = null;
                        ConstraintWidget from = null;
                        int min = Integer.MAX_VALUE;

                        for (ConstraintWidget fromCandidate : unconstrained) {
                            for (ConstraintWidget toCandidate : constrained) {
                                int fromLeft = fromCandidate.getX();
                                int fromRight = fromLeft + fromCandidate.getWidth();
                                int toLeft = toCandidate.getX();
                                int toRight = toLeft + toCandidate.getWidth();
                                int dist = Math.abs(toLeft - fromLeft);
                                dist = Math.min(dist, Math.abs(toLeft - fromRight));
                                dist = Math.min(dist, Math.abs(toRight - fromRight));
                                dist = Math.min(dist, Math.abs(toRight - fromLeft));
                                if (dist < min) {
                                    min = dist;
                                    to = toCandidate;
                                    from = fromCandidate;
                                }
                            }
                        }
                        from.connect(ConstraintAnchor.Type.BASELINE, to,
                                ConstraintAnchor.Type.BASELINE, 0);
                        constrained.add(from);
                        unconstrained.remove(from);
                    }
                }
            }
            break;
            case AlignVerticallyBottom: {
                int max = Integer.MIN_VALUE;
                for (ConstraintWidget widget : widgets) {
                    max = Math.max(max, widget.getY() + widget.getHeight());
                }
                ConstraintWidget previousWidget = null;
                for (ConstraintWidget widget : widgets) {
                    float current = widget.getHeight();
                    widget.setY((int) (max - current));
                    if (applyConstraints) {
                        if (previousWidget != null) {
                            widget.resetAnchor(widget.getAnchor(ConstraintAnchor.Type.TOP));
                            widget.connect(ConstraintAnchor.Type.BOTTOM, previousWidget,
                                    ConstraintAnchor.Type.BOTTOM, 0);
                        }
                    }
                    previousWidget = widget;
                }
            }
            break;
            case DistributeVertically: {
                int count = 0;
                int sum = 0;
                int min = widgetList.get(0).getY();
                int max = widgetList.get(0).getY() + widgetList.get(0).getHeight();
                for (ConstraintWidget widget : widgets) {
                    int start = widget.getY();
                    int size = widget.getHeight();
                    int end = start + size;
                    sum += size;
                    min = Math.min(min, start);
                    max = Math.max(max, end);
                    count++;
                }
                int gaps = count - 1;
                int totalGap = max - min - sum;
                int lastY = min;
                boolean reverse =
                        rootDistanceY(widgets[0]) > rootDistanceY(widgets[widgets.length - 1]);
                for (int i = 0; i < count; i++) {
                    if (i > 0) {
                        int size = widgets[i - 1].getHeight();
                        min += size;
                        int pos = min + (totalGap * i) / gaps;
                        widgets[i].setY(pos);
                        if (applyConstraints) {
                            if (reverse) {
                                widgets[i - 1].connect(ConstraintAnchor.Type.BOTTOM, widgets[i],
                                        ConstraintAnchor.Type.TOP, pos - lastY - size);
                            } else {
                                widgets[i].connect(ConstraintAnchor.Type.TOP, widgets[i - 1],
                                        ConstraintAnchor.Type.BOTTOM, pos - lastY - size);
                            }
                            lastY = pos;
                        }
                    }
                }
            }
            break;
            case DistributeHorizontally: {
                int count = 0;
                int sum = 0;
                int min = widgetList.get(0).getX();
                int max = widgetList.get(0).getX() + widgetList.get(0).getHeight();
                for (ConstraintWidget widget : widgets) {
                    int start = widget.getX();
                    int size = widget.getWidth();
                    int end = start + size;
                    sum += size;
                    min = Math.min(min, start);
                    max = Math.max(max, end);
                    count++;
                }
                int gaps = count - 1;
                int totalGap = max - min - sum;
                int lastX = min;
                boolean reverse =
                        rootDistanceX(widgets[0]) > rootDistanceX(widgets[widgets.length - 1]);
                for (int i = 0; i < count; i++) {

                    if (i > 0) {
                        int size = widgets[i - 1].getWidth();
                        min += size;
                        int pos = min + (totalGap * i) / gaps;
                        widgets[i].setX(pos);
                        if (applyConstraints) {
                            if (reverse) {
                                widgets[i - 1].connect(ConstraintAnchor.Type.RIGHT, widgets[i],
                                        ConstraintAnchor.Type.LEFT, pos - lastX - size);
                            } else {
                                widgets[i].connect(ConstraintAnchor.Type.LEFT, widgets[i - 1],
                                        ConstraintAnchor.Type.RIGHT, pos - lastX - size);
                            }
                            lastX = pos;
                        }
                    }
                }
            }
            break;
            case VerticalPack: {
                Rectangle original = getBoundingBox(widgetList);
                ConstraintWidget[] wArray = new ConstraintWidget[widgetList.size()];
                wArray = widgetList.toArray(wArray);
                Arrays.sort(wArray, (w1, w2) -> Integer.compare(w1.getY(), w2.getY()));
                ScoutWidget[] list = ScoutWidget.getWidgetArray(
                        (WidgetContainer) widgetList.get(0).getParent());

                for (ConstraintWidget cw : wArray) {
                    for (ScoutWidget scoutWidget : list) {
                        if (scoutWidget.mConstraintWidget == cw) {
                            int gapN = scoutWidget.gap(Direction.NORTH, list);
                            int newY = margin + scoutWidget.mConstraintWidget.getY() - gapN;
                            newY = Math.max(newY, original.y);
                            scoutWidget.setY(newY);
                        }
                    }
                }
            }
            break;
            case HorizontalPack: {
                Rectangle original = getBoundingBox(widgetList);
                ConstraintWidget[] wArray = new ConstraintWidget[widgetList.size()];
                wArray = widgetList.toArray(wArray);
                Arrays.sort(wArray, (w1, w2) -> Integer.compare(w1.getX(), w2.getX()));
                ScoutWidget[] list = ScoutWidget.getWidgetArray(
                        (WidgetContainer) widgetList.get(0).getParent());

                for (ConstraintWidget cw : wArray) {
                    for (ScoutWidget scoutWidget : list) {
                        if (scoutWidget.mConstraintWidget == cw) {
                            int gapW = scoutWidget.gap(Direction.WEST, list);
                            int newX = margin + scoutWidget.mConstraintWidget.getX() - gapW;
                            newX = Math.max(newX, original.x);
                            scoutWidget.setX(newX);
                        }
                    }
                }
            }
            break;
            case ExpandVertically: {
                expandVertically(widgetList, margin);
            }
            break;
            case ExpandHorizontally: {
                expandHorizontally(widgetList, margin);
            }
            break;
        }
    }

    /**
     * Expands widgets vertically in an evenly spaced manner
     * @param widgetList
     * @param margin
     */
    private static void expandVertically(ArrayList<ConstraintWidget> widgetList, int margin) {
        WidgetContainer base = (WidgetContainer) widgetList.get(0).getParent();
        ConstraintWidget[] pears = new ConstraintWidget[base.getChildren().size()];
        pears = base.getChildren().toArray(pears);

        Rectangle selectBounds = getBoundingBox(widgetList);

        Rectangle clip = new Rectangle();
        int gapNorth = gap(Direction.NORTH, selectBounds, pears);
        int gapSouth = gap(Direction.SOUTH, selectBounds, pears);

        clip.y = selectBounds.y - gapNorth;
        clip.height = selectBounds.height + gapSouth + gapNorth;

        ArrayList<ConstraintWidget> selectedList = new ArrayList<>(widgetList);
        while (!selectedList.isEmpty()) {
            ConstraintWidget widget = selectedList.remove(0);
            ArrayList<ConstraintWidget> col = new ArrayList<>();
            col.add(widget);
            for (Iterator<ConstraintWidget> iterator = selectedList.iterator();
                    iterator.hasNext(); ) {
                ConstraintWidget elem = iterator.next();
                if (isSameColumn(widget, elem)) {
                    if (!col.contains(elem)) {
                        col.add(elem);
                    }
                    iterator.remove();
                }
            }
            ConstraintWidget[] colArray = new ConstraintWidget[col.size()];
            colArray = col.toArray(colArray);
            Arrays.sort(colArray, sSortY);
            int gaps = (colArray.length - 1) * margin;
            int totalHeight = (clip.height - gaps - 2 * margin);

            for (int i = 0; i < colArray.length; i++) {
                int y = margin * i + (i * (totalHeight)) / colArray.length;
                ConstraintWidget constraintWidget = colArray[i];
                constraintWidget.setY(y + clip.y + margin);
                int yend = margin * i + (totalHeight * (i + 1)) / colArray.length;
                constraintWidget
                        .setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.FIXED);
                constraintWidget.setHeight(yend - y);
            }
        }
    }

    /**
     * Expands widgets horizontally in an evenly spaced manner
     * @param widgetList
     * @param margin
     */
    public static void expandHorizontally(ArrayList<ConstraintWidget> widgetList, int margin) {
        WidgetContainer base = (WidgetContainer) widgetList.get(0).getParent();
        ConstraintWidget[] pears = new ConstraintWidget[base.getChildren().size()];
        pears = base.getChildren().toArray(pears);
        Rectangle selectBounds = getBoundingBox(widgetList);

        Rectangle clip = new Rectangle();
        int gapWest = gap(Direction.WEST, selectBounds, pears);
        int gapEast = gap(Direction.EAST, selectBounds, pears);
        clip.x = selectBounds.x - gapWest;
        clip.width = selectBounds.width + gapEast + gapWest;
        ArrayList<ConstraintWidget> selectedList;
        selectedList = new ArrayList<ConstraintWidget>(widgetList);
        while (!selectedList.isEmpty()) {
            ConstraintWidget widget = selectedList.remove(0);
            ArrayList<ConstraintWidget> row = new ArrayList<>();
            row.add(widget);
            for (Iterator<ConstraintWidget> iterator = selectedList.iterator();
                    iterator.hasNext(); ) {
                ConstraintWidget elem = iterator.next();
                if (isSameRow(widget, elem)) {
                    if (!row.contains(elem)) {
                        row.add(elem);
                    }
                    iterator.remove();
                }
            }

            ConstraintWidget[] rowArray = new ConstraintWidget[row.size()];
            rowArray = row.toArray(rowArray);
            Arrays.sort(rowArray, sSortX);
            int gaps = (rowArray.length - 1) * margin;
            int totalWidth = (clip.width - gaps - 2 * margin);

            for (int i = 0; i < rowArray.length; i++) {
                int x = margin * i + (i * (totalWidth)) / rowArray.length;
                ConstraintWidget constraintWidget = rowArray[i];
                constraintWidget.setX(x + clip.x + margin);
                int xend = margin * i + (totalWidth * (i + 1)) / rowArray.length;
                constraintWidget
                        .setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.FIXED);
                constraintWidget.setWidth(xend - x);
            }
        }
    }

    /**
     * are the two widgets in the same horizontal area
     * @param a
     * @param b
     * @return true if aligned
     */
    static boolean isSameRow(ConstraintWidget a, ConstraintWidget b) {
        return Math.max(a.getY(), b.getY()) <
                Math.min(a.getY() + a.getHeight(), b.getY() + b.getHeight());
    }
    /**
     * are the two widgets in the same vertical area
     * @param a
     * @param b
     * @return true if aligned
     */
    static boolean isSameColumn(ConstraintWidget a, ConstraintWidget b) {
        return Math.max(a.getX(), b.getX()) <
                Math.min(a.getX() + a.getWidth(), b.getX() + b.getWidth());
    }

    /**
     * get rectangle for widget
     * @param widget
     * @return
     */
    static Rectangle getRectangle(ConstraintWidget widget) {
        Rectangle rectangle = new Rectangle();
        rectangle.x = widget.getX();
        rectangle.y = widget.getY();
        rectangle.width = widget.getWidth();
        rectangle.height = widget.getHeight();
        return rectangle;
    }

    /**
     * Calculate the nearest widget
     *
     * @param direction the direction to check
     * @param list      list of other widgets (root == list[0])
     * @return the distance on that side
     */
    public static ConstraintWidget gapWidget(Direction direction, Rectangle region,
            ConstraintWidget[] list) {
        int rootWidth = list[0].getParent().getWidth();
        int rootHeight = list[0].getParent().getHeight();
        Rectangle rect = new Rectangle();

        switch (direction) {
            case NORTH: {
                rect.y = 0;
                rect.x = region.x + 1;
                rect.width = region.width - 2;
                rect.height = region.y;
            }
            break;
            case SOUTH: {
                rect.y = region.y + region.height + 1;
                rect.x = region.x + 1;
                rect.width = region.width - 2;
                rect.height = rootHeight - rect.y;
            }
            break;
            case WEST: {
                rect.y = region.y + 1;
                rect.x = 0;
                rect.width = region.x;
                rect.height = region.height - 2;

            }
            break;
            case EAST: {
                rect.y = region.y + 1;
                rect.x = region.x + region.width + 1;
                rect.width = rootWidth - rect.x;
                rect.height = region.height - 2;
            }
            break;

        }
        int min = Integer.MAX_VALUE;
        ConstraintWidget minWidget = null;
        for (ConstraintWidget widget : list) {
            Rectangle r = getRectangle(widget);
            if (r.intersects(rect)) {
                int dist = (int)distance(r, region);
                if (min > dist) {
                    minWidget = widget;
                    min = dist;
                }
            }
        }

        if (min > Math.max(rootHeight, rootWidth)) {
            return list[0].getParent();
        }
        return minWidget;
    }

    /**
     * Calculate the gap in to the nearest widget
     *
     * @param direction the direction to check
     * @param list      list of other widgets (root == list[0])
     * @return the distance on that side
     */
    public static int gap(Direction direction, Rectangle region, ConstraintWidget[] list) {
        int rootWidth = list[0].getParent().getWidth();
        int rootHeight = list[0].getParent().getHeight();
        Rectangle rect = new Rectangle();

        switch (direction) {
            case NORTH: {
                rect.y = 0;
                rect.x = region.x + 1;
                rect.width = region.width - 2;
                rect.height = region.y;
            }
            break;
            case SOUTH: {
                rect.y = region.y + region.height + 1;
                rect.x = region.x + 1;
                rect.width = region.width - 2;
                rect.height = rootHeight - rect.y;
            }
            break;
            case WEST: {
                rect.y = region.y + 1;
                rect.x = 0;
                rect.width = region.x;
                rect.height = region.height - 2;

            }
            break;
            case EAST: {
                rect.y = region.y + 1;
                rect.x = region.x + region.width + 1;
                rect.width = rootWidth - rect.x;
                rect.height = region.height - 2;
            }
            break;

        }
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < list.length; i++) {
            ConstraintWidget widget = list[i];

            Rectangle r = getRectangle(widget);
            if (r.intersects(rect)) {
                int dist = (int) distance(r, region);
                if (min > dist) {
                    min = dist;
                }
            }
        }

        if (min > Math.max(rootHeight, rootWidth)) {
            switch (direction) {
                case NORTH:
                    return region.y;
                case SOUTH:
                    return rootHeight - (region.y + region.height);
                case WEST:
                    return region.x;
                case EAST:
                    return rootWidth - (region.x + region.width);
            }
        }
        return min;
    }

    /**
     * calculates the distance between two widgets (assumed to be rectangles)
     *
     * @param a
     * @param b
     * @return the distance between two widgets at there closest point to each other
     */
    static float distance(Rectangle a, Rectangle b) {
        float ax1, ax2, ay1, ay2;
        float bx1, bx2, by1, by2;
        ax1 = a.x;
        ax2 = a.x + a.width;
        ay1 = a.y;
        ay2 = a.y + a.height;

        bx1 = b.x;
        bx2 = b.x + b.width;
        by1 = b.y;
        by2 = b.y + b.height;
        float xdiff11 = Math.abs(ax1 - bx1);
        float xdiff12 = Math.abs(ax1 - bx2);
        float xdiff21 = Math.abs(ax2 - bx1);
        float xdiff22 = Math.abs(ax2 - bx2);

        float ydiff11 = Math.abs(ay1 - by1);
        float ydiff12 = Math.abs(ay1 - by2);
        float ydiff21 = Math.abs(ay2 - by1);
        float ydiff22 = Math.abs(ay2 - by2);

        float xmin = Math.min(Math.min(xdiff11, xdiff12), Math.min(xdiff21, xdiff22));
        float ymin = Math.min(Math.min(ydiff11, ydiff12), Math.min(ydiff21, ydiff22));

        boolean yOverlap = ay1 <= by2 && by1 <= ay2;
        boolean xOverlap = ax1 <= bx2 && bx1 <= ax2;
        float xReturn = (yOverlap) ? xmin : (float) Math.hypot(xmin, ymin);
        float yReturn = (xOverlap) ? ymin : (float) Math.hypot(xmin, ymin);
        return Math.min(xReturn, yReturn);
    }

    /**
     * Get the distance to widget's parent in X
     *
     * @param widget
     * @return
     */
    static int rootDistanceX(ConstraintWidget widget) {
        int rootWidth = widget.getParent().getWidth();
        int aX = widget.getX();
        int aWidth = widget.getWidth();
        return Math.min(aX, rootWidth - (aX + aWidth));
    }

    /**
     * get the distance to widget's parent in Y
     *
     * @param widget
     * @return
     */
    static int rootDistanceY(ConstraintWidget widget) {
        int rootHeight = widget.getParent().getHeight();
        int aY = widget.getY();
        int aHeight = widget.getHeight();
        return Math.min(aY, rootHeight - (aY + aHeight));
    }

    /**
     * Get the bounding box around a list of widgets
     *
     * @param widgets
     * @return
     */
    static Rectangle getBoundingBox(ArrayList<ConstraintWidget> widgets) {
        Rectangle all = null;
        Rectangle tmp = new Rectangle();
        for (ConstraintWidget widget : widgets) {
            if (widget instanceof Guideline) {
                continue;
            }
            tmp.x = widget.getX();
            tmp.y = widget.getY();
            tmp.width = widget.getWidth();
            tmp.height = widget.getHeight();
            if (all == null) {
                all = new Rectangle(tmp);
            } else {
                all = all.union(tmp);
            }
        }
        return all;
    }

    /**
     * in place Reverses the order of the widgets
     *
     * @param widgets to reverse
     */
    private static void reverse(ConstraintWidget[] widgets) {
        for (int i = 0; i < widgets.length / 2; i++) {
            ConstraintWidget widget = widgets[i];
            widgets[i] = widgets[widgets.length - 1 - i];
            widgets[widgets.length - 1 - i] = widget;
        }
    }

    /**
     * Get the distance to the root for a widget
     *
     * @param widget to get the distance to root
     * @return
     */
    private static int rootDistance(ConstraintWidget widget) {
        int rootHeight = widget.getParent().getHeight();
        int rootWidth = widget.getParent().getWidth();
        int aX = widget.getX();
        int aY = widget.getY();
        int aWidth = widget.getWidth();
        int aHeight = widget.getHeight();
        int minx = Math.min(aX, rootWidth - (aX + aWidth));
        int miny = Math.min(aY, rootHeight - (aY + aHeight));
        return Math.min(minx, miny);
    }

    /**
     * true if top bottom or baseline are connected
     *
     * @param widget widget to test
     * @return true if it is constrained
     */
    private static boolean isVerticallyConstrained(ConstraintWidget widget) {
        if (widget.getAnchor(ConstraintAnchor.Type.BOTTOM).isConnected()) {
            return true;
        }
        if (widget.getAnchor(ConstraintAnchor.Type.TOP).isConnected()) {
            return true;
        }
        if (widget.getAnchor(ConstraintAnchor.Type.BASELINE).isConnected()) {
            return true;
        }
        return false;
    }

    /**
     * find the nearest widget in a list of widgets only considering the horizontal location
     *
     * @param nextTo find nearest to this widget
     * @param list   list to search
     * @return the nearest in the list
     */
    private static ConstraintWidget nearestHorizontal(ConstraintWidget nextTo,
            ArrayList<ConstraintWidget> list) {
        int min = Integer.MAX_VALUE;
        ConstraintWidget ret = null;
        int nextToLeft = nextTo.getX();
        int nextToRight = nextToLeft + nextTo.getWidth();
        for (ConstraintWidget widget : list) {
            if (widget == nextTo) {
                continue;
            }

            int left = widget.getX();
            int right = left + widget.getWidth();
            int dist = Math.abs(left - nextToLeft);
            dist = Math.min(dist, Math.abs(left - nextToRight));
            dist = Math.min(dist, Math.abs(right - nextToRight));
            dist = Math.min(dist, Math.abs(right - nextToLeft));
            if (dist < min) {
                min = dist;
                ret = widget;
            }
        }
        return ret;
    }
}
