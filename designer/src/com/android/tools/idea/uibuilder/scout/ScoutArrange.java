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
package com.android.tools.idea.uibuilder.scout;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities;

import java.awt.Rectangle;
import java.util.*;

import static com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities.*;

/**
 * This implements the standard arrange functions
 */
public class ScoutArrange {
    static Comparator<NlComponent> sSortY = (w1, w2) -> ConstraintComponentUtilities.getDpY(w1)  - ConstraintComponentUtilities.getDpY(w2);
    static Comparator<NlComponent> sSortX = (w1, w2) -> getDpX(w1)  - getDpX(w2);

    /**
     * Perform various types of arrangements on a selection of widgets
     *
     * @param type             the type of change
     * @param widgetList       the list of selected widgets
     * @param applyConstraints if true apply related constraints (except expand and pack)
     */
    public static void align(Scout.Arrange type, List<NlComponent> widgetList,
            boolean applyConstraints) {
        if (widgetList == null || widgetList.size() == 0) {
            return;
        }
        int margin = Scout.getMargin();
        NlComponent[] widgets = new NlComponent[widgetList.size()];
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
                NlComponent parent =   widgets[0].getParent();
                NlComponent[] peers = new NlComponent[parent.getChildren().size()];
                peers = parent.getChildren().toArray(peers);

                for (NlComponent widget : widgets) {
                    rectangle.x = getDpX(widget);
                    rectangle.y = getDpY(widget);
                    rectangle.width = getDpWidth(widget);
                    rectangle.height = getDpHeight(widget);
                    int westDistance = gap(Direction.LEFT, rectangle, peers);
                    int eastDistance = gap(Direction.RIGHT, rectangle, peers);
                    int x = getDpX(widget);

                    if (applyConstraints) {
                        NlComponent westConnect =
                                gapWidget(Direction.LEFT, rectangle, peers);
                        NlComponent eastConnect =
                                gapWidget(Direction.RIGHT, rectangle, peers);

                        Direction dir = Direction.RIGHT;
                        if (westConnect == parent) {
                            dir = Direction.LEFT;
                        }
                        scoutConnect(widget, Direction.LEFT, westConnect, dir, 0);
                        dir = Direction.LEFT;
                        if (eastConnect == parent) {
                            dir = Direction.RIGHT;
                        }
                        scoutConnect(widget, Direction.RIGHT, eastConnect, dir, 0);
                        setScoutHorizontalBiasPercent(widget, .5f);

                    } else {
                        setScoutAbsoluteDpX(widget, x + (eastDistance - westDistance) / 2);
                    }
                }
            }
            break;
            case CenterVertically: {
                Rectangle rectangle = new Rectangle();
                NlComponent parent = widgets[0].getParent();
                NlComponent[] pears = new NlComponent[parent.getChildren().size()];
                pears = parent.getChildren().toArray(pears);

                for (NlComponent widget : widgets) {
                    rectangle.x = getDpX(widget);
                    rectangle.y = getDpY(widget);
                    rectangle.width = getDpWidth(widget);
                    rectangle.height = getDpHeight(widget);
                    int northDistance = gap(Direction.TOP, rectangle, pears);
                    int southDistance = gap(Direction.BOTTOM, rectangle, pears);
                    int Y = getDpY(widget);

                    if (applyConstraints) {
                        NlComponent northConnect =
                                gapWidget(Direction.TOP, rectangle, pears);
                        NlComponent southConnect =
                                gapWidget(Direction.BOTTOM, rectangle, pears);

                        Direction dir = Direction.BOTTOM;
                        if (northConnect == parent) {
                            dir = Direction.TOP;
                        }
                        scoutConnect(widget, Direction.TOP, northConnect, dir, 0);
                        dir = Direction.TOP;
                        if (southConnect == parent) {
                            dir = Direction.BOTTOM;
                        }
                        scoutConnect(widget, Direction.BOTTOM, southConnect, dir, 0);
                        setScoutVerticalBiasPercent(widget, .5f);
                    } else {
                        setScoutAbsoluteDpY(widget, Y + (southDistance - northDistance) / 2);
                    }
                }
            }
            break;
            case CenterHorizontallyInParent: {
                for (NlComponent widget : widgets) {
                    int parentWidth = getDpWidth(widget.getParent());
                    int width = getDpWidth(widget);
                    setScoutAbsoluteDpX(widget, (parentWidth - width) / 2);
                    if (applyConstraints) {
                        scoutConnect(widget, Direction.LEFT, widget.getParent(), Direction.LEFT, 0);
                        scoutConnect(widget, Direction.RIGHT, widget.getParent(), Direction.RIGHT, 0);
                        setScoutHorizontalBiasPercent(widget, .5f);
                    }
                }
            }
            break;
            case CenterVerticallyInParent: {
                for (NlComponent widget : widgets) {
                    int parentHeight = getDpHeight(widget.getParent());
                    int height = getDpHeight(widget);
                    setScoutAbsoluteDpY(widget, (parentHeight - height) / 2);
                    if (applyConstraints) {
                        scoutConnect(widget, Direction.TOP, widget.getParent(), Direction.TOP, 0);
                        scoutConnect(widget, Direction.BOTTOM, widget.getParent(), Direction.BOTTOM, 0);
                        setScoutVerticalBiasPercent(widget, .5f);
                    }
                }
            }
            break;
            case AlignHorizontallyCenter: {
                int count = 0;
                float avg = 0;
                for (NlComponent widget : widgets) {
                    avg += getDpX(widget) + getDpWidth(widget) / 2.0f;
                    count++;
                }
                avg /= count;

                NlComponent previousWidget = null;
                for (NlComponent widget : widgets) {
                    float current = getDpWidth(widget) / 2.0f;
                    setScoutAbsoluteDpX(widget, (int)(avg - current));
                    if (applyConstraints) {
                        if (previousWidget != null) {
                            scoutConnect(widget, Direction.LEFT, previousWidget, Direction.LEFT, 0);
                            scoutConnect(widget, Direction.RIGHT, previousWidget, Direction.RIGHT, 0);
                        }
                    }
                    previousWidget = widget;
                }
            }
            break;
            case AlignHorizontallyLeft: {
                int min = Integer.MAX_VALUE;
                for (NlComponent widget : widgets) {
                    min = Math.min(min, getDpX(widget));
                }
                NlComponent previousWidget = null;
                for (NlComponent widget : widgets) {
                    setScoutAbsoluteDpX(widget, min);
                    if (applyConstraints) {
                        if (previousWidget != null) {
                            scoutClearAttributes(widget, ourRightAttributes);
                            scoutConnect(widget, Direction.LEFT, previousWidget,
                                         Direction.LEFT, 0);
                        }
                    }
                    previousWidget = widget;
                }
            }
            break;
            case AlignHorizontallyRight: {
                int max = Integer.MIN_VALUE;
                for (NlComponent widget : widgets) {
                    max = Math.max(max, getDpX(widget) + getDpWidth(widget));
                }
                NlComponent previousWidget = null;
                for (NlComponent widget : widgets) {
                    float current = getDpWidth(widget);
                    setScoutAbsoluteDpX(widget, (int) (max - current));
                    if (applyConstraints) {
                        if (previousWidget != null) {
                            scoutClearAttributes(widget, ourLeftAttributes);
                            scoutConnect(widget, Direction.RIGHT, previousWidget,
                                         Direction.RIGHT, 0);
                        }
                    }
                    previousWidget = widget;
                }
            }
            break;
            case AlignVerticallyTop: {
                int min = Integer.MAX_VALUE;
                for (NlComponent widget : widgets) {
                    min = Math.min(min, getDpY(widget));
                }
                NlComponent previousWidget = null;
                for (NlComponent widget : widgets) {
                    setScoutAbsoluteDpY(widget, min);
                    if (applyConstraints) {
                        if (previousWidget != null) {
                            scoutClearAttributes(widget, ourBottomAttributes);
                            scoutConnect(widget, Direction.TOP, previousWidget,
                                         Direction.TOP, 0);
                        }
                    }
                    previousWidget = widget;
                }
            }
            break;
            case AlignVerticallyMiddle: {
                int count = 0;
                float avg = 0;
                for (NlComponent widget : widgets) {
                    avg += getDpY(widget) + getDpHeight(widget) / 2.0f;
                    count++;
                }
                avg /= count;
                NlComponent previousWidget = null;
                for (NlComponent widget : widgets) {
                    float current = getDpHeight(widget) / 2.0f;
                    setScoutAbsoluteDpY(widget, (int) (avg - current));
                    if (applyConstraints) {
                        if (previousWidget != null) {
                            scoutConnect(widget, Direction.TOP, previousWidget, Direction.TOP, 0);
                            scoutConnect(widget, Direction.BOTTOM, previousWidget, Direction.BOTTOM, 0);
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
                NlComponent fixedWidget = null;
                for (NlComponent widget : widgets) {
                    if (isVerticallyConstrained(widget)) {
                        number_of_constrained++;
                        fixedWidget = widget;
                    }
                    avg += getDpY(widget) + getDpBaseline(widget);
                    count++;
                }
                avg /= count;
                // if one is already constrained move the rest to it
                if (number_of_constrained == 1) {
                    avg = getDpX(fixedWidget) + getDpBaseline(fixedWidget);
                }
                NlComponent previousWidget = null;
                if (!applyConstraints || number_of_constrained == 0) {
                    for (NlComponent widget : widgets) {
                        float baseline = getDpBaseline(widget);
                        setScoutAbsoluteDpY(widget, (int) (avg - baseline));
                        if (applyConstraints) {
                            if (previousWidget != null) {
                                scoutConnect(widget, Direction.BASELINE, previousWidget,
                                             Direction.BASELINE, 0);
                            }
                        }
                        previousWidget = widget;
                    }
                } else { // if you are creating constraints and some are already constrained
                    // Build a list of constrained and unconstrained widgets
                    ArrayList<NlComponent> unconstrained = new ArrayList<>();
                    ArrayList<NlComponent> constrained = new ArrayList<>();
                    for (NlComponent widget : widgets) {
                        if (isVerticallyConstrained(widget)) {
                            constrained.add(widget);
                        } else {
                            unconstrained.add(widget);
                        }
                    }
                    // one by one constrain widgets by finding the closest between the two list
                    while (!unconstrained.isEmpty()) {
                        NlComponent to = null;
                        NlComponent from = null;
                        int min = Integer.MAX_VALUE;

                        for (NlComponent fromCandidate : unconstrained) {
                            for (NlComponent toCandidate : constrained) {
                                int fromLeft = getDpX(fromCandidate);
                                int fromRight = fromLeft + getDpWidth(fromCandidate);
                                int toLeft = getDpX(toCandidate);
                                int toRight = toLeft + getDpWidth(toCandidate);
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
                        scoutConnect(from, Direction.BASELINE, to,
                                     Direction.BASELINE, 0);
                        constrained.add(from);
                        unconstrained.remove(from);
                    }
                }
            }
            break;
            case AlignVerticallyBottom: {
                int max = Integer.MIN_VALUE;
                for (NlComponent widget : widgets) {
                    max = Math.max(max, getDpY(widget) + getDpHeight(widget));
                }
                NlComponent previousWidget = null;
                for (NlComponent widget : widgets) {
                    float current = getDpHeight(widget);
                    setScoutAbsoluteDpY(widget, (int) (max - current));
                    if (applyConstraints) {
                        if (previousWidget != null) {
                            scoutClearAttributes(widget, ourTopAttributes);
                            scoutConnect(widget, Direction.BOTTOM, previousWidget,
                                         Direction.BOTTOM, 0);
                        }
                    }
                    previousWidget = widget;
                }
            }
            break;
            case DistributeVertically: {
                int count = 0;
                int sum = 0;
                int min = getDpY(widgetList.get(0));
                int max = getDpY(widgetList.get(0)) + getDpHeight(widgetList.get(0));
                for (NlComponent widget : widgets) {
                    int start = getDpY(widget);
                    int size = getDpHeight(widget);
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
                        int size  = getDpHeight( widgets[i - 1]);
                        min += size;
                        int pos = min + (totalGap * i) / gaps;
                        setScoutAbsoluteDpY(widgets[i], pos);
                        if (applyConstraints) {
                            if (reverse) {
                                scoutConnect(widgets[i - 1], Direction.BOTTOM, widgets[i],
                                             Direction.TOP, pos - lastY - size);
                            } else {
                                scoutConnect(widgets[i], Direction.TOP, widgets[i - 1],
                                             Direction.BOTTOM, pos - lastY - size);
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
                int min = getDpX(widgetList.get(0));
                int max = getDpX(widgetList.get(0)) + getDpHeight(widgetList.get(0));
                for (NlComponent widget : widgets) {
                    int start = getDpX(widget);
                    int size = getDpWidth(widget);
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
                        int size = getDpWidth(widgets[i - 1]);
                        min += size;
                        int pos = min + (totalGap * i) / gaps;
                        setScoutAbsoluteDpX(widgets[i], pos);
                        if (applyConstraints) {
                            if (reverse) {
                                scoutConnect(widgets[i - 1], Direction.RIGHT, widgets[i],
                                             Direction.LEFT, pos - lastX - size);
                            } else {
                                scoutConnect(widgets[i], Direction.LEFT, widgets[i - 1],
                                             Direction.RIGHT, pos - lastX - size);
                            }
                            lastX = pos;
                        }
                    }
                }
            }
            break;
            case VerticalPack: {
                Rectangle original = getBoundingBox(widgetList);
                NlComponent[] wArray = new NlComponent[widgetList.size()];
                wArray = widgetList.toArray(wArray);
                Arrays.sort(wArray, (w1, w2) -> Integer.compare(getDpY(w1), getDpY(w2)));
                ScoutWidget[] list = ScoutWidget.getWidgetArray(
                         widgetList.get(0).getParent());

                for (NlComponent cw : wArray) {
                    for (ScoutWidget scoutWidget : list) {
                        if (scoutWidget.mNlComponent == cw) {
                            int gapN = scoutWidget.gap(Direction.TOP, list);
                            int newY = margin + getDpY(scoutWidget.mNlComponent) - gapN;
                            newY = Math.max(newY, original.y);
                            scoutWidget.setY(newY);
                        }
                    }
                }
            }
            break;
            case HorizontalPack: {
                Rectangle original = getBoundingBox(widgetList);
                NlComponent[] wArray = new NlComponent[widgetList.size()];
                wArray = widgetList.toArray(wArray);
                Arrays.sort(wArray, (w1, w2) -> Integer.compare(getDpX(w1), getDpX(w2)));
                ScoutWidget[] list = ScoutWidget.getWidgetArray(
                         widgetList.get(0).getParent());

                for (NlComponent cw : wArray) {
                    for (ScoutWidget scoutWidget : list) {
                        if (scoutWidget.mNlComponent == cw) {
                            int gapW = scoutWidget.gap(Direction.LEFT, list);
                            int newX = margin + getDpX(scoutWidget.mNlComponent) - gapW;
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
    private static void expandVertically(List<NlComponent> widgetList, int margin) {
        NlComponent base =   widgetList.get(0).getParent();
        NlComponent[] pears = new NlComponent[base.getChildren().size()];
        pears = base.getChildren().toArray(pears);

        Rectangle selectBounds = getBoundingBox(widgetList);

        Rectangle clip = new Rectangle();
        int gapNorth = gap(Direction.TOP, selectBounds, pears);
        int gapSouth = gap(Direction.BOTTOM, selectBounds, pears);

        clip.y = selectBounds.y - gapNorth;
        clip.height = selectBounds.height + gapSouth + gapNorth;

        ArrayList<NlComponent> selectedList = new ArrayList<>(widgetList);
        while (!selectedList.isEmpty()) {
            NlComponent widget = selectedList.remove(0);
            ArrayList<NlComponent> col = new ArrayList<>();
            col.add(widget);
            for (Iterator<NlComponent> iterator = selectedList.iterator();
                    iterator.hasNext(); ) {
                NlComponent elem = iterator.next();
                if (isSameColumn(widget, elem)) {
                    if (!col.contains(elem)) {
                        col.add(elem);
                    }
                    iterator.remove();
                }
            }
            NlComponent[] colArray = new NlComponent[col.size()];
            colArray = col.toArray(colArray);
            Arrays.sort(colArray, sSortY);
            int gaps = (colArray.length - 1) * margin;
            int totalHeight = (clip.height - gaps - 2 * margin);

            for (int i = 0; i < colArray.length; i++) {
                int y = margin * i + (i * (totalHeight)) / colArray.length;
                NlComponent constraintWidget = colArray[i];
                setScoutAbsoluteDpY(constraintWidget, y + clip.y + margin);
                int yend = margin * i + (totalHeight * (i + 1)) / colArray.length;
                setScoutAbsoluteDpHeight(constraintWidget, yend - y);
            }
        }
    }

    /**
     * Expands widgets horizontally in an evenly spaced manner
     * @param widgetList
     * @param margin
     */
    public static void expandHorizontally(List<NlComponent> widgetList, int margin) {
        NlComponent base =  widgetList.get(0).getParent();
        NlComponent[] pears = new NlComponent[base.getChildren().size()];
        pears = base.getChildren().toArray(pears);
        Rectangle selectBounds = getBoundingBox(widgetList);

        Rectangle clip = new Rectangle();
        int gapWest = gap(Direction.LEFT, selectBounds, pears);
        int gapEast = gap(Direction.RIGHT, selectBounds, pears);
        clip.x = selectBounds.x - gapWest;
        clip.width = selectBounds.width + gapEast + gapWest;
        ArrayList<NlComponent> selectedList;
        selectedList = new ArrayList<NlComponent>(widgetList);
        while (!selectedList.isEmpty()) {
            NlComponent widget = selectedList.remove(0);
            ArrayList<NlComponent> row = new ArrayList<>();
            row.add(widget);
            for (Iterator<NlComponent> iterator = selectedList.iterator();
                    iterator.hasNext(); ) {
                NlComponent elem = iterator.next();
                if (isSameRow(widget, elem)) {
                    if (!row.contains(elem)) {
                        row.add(elem);
                    }
                    iterator.remove();
                }
            }

            NlComponent[] rowArray = new NlComponent[row.size()];
            rowArray = row.toArray(rowArray);
            Arrays.sort(rowArray, sSortX);
            int gaps = (rowArray.length - 1) * margin;
            int totalWidth = (clip.width - gaps - 2 * margin);

            for (int i = 0; i < rowArray.length; i++) {
                int x = margin * i + (i * (totalWidth)) / rowArray.length;
                NlComponent constraintWidget = rowArray[i];
                setScoutAbsoluteDpX(constraintWidget, x + clip.x + margin);
                int xend = margin * i + (totalWidth * (i + 1)) / rowArray.length;

                setScoutAbsoluteDpWidth(constraintWidget, xend - x);
            }
        }
    }

    /**
     * are the two widgets in the same horizontal area
     * @param a
     * @param b
     * @return true if aligned
     */
    static boolean isSameRow(NlComponent a, NlComponent b) {
        return Math.max(getDpY(a), getDpY(b)) <
                Math.min(getDpY(a) + getDpHeight(a), getDpY(b) + getDpHeight(b));
    }
    /**
     * are the two widgets in the same vertical area
     * @param a
     * @param b
     * @return true if aligned
     */
    static boolean isSameColumn(NlComponent a, NlComponent b) {
        return Math.max(getDpX(a), getDpX(b)) <
                Math.min(getDpX(a) + getDpWidth(a), getDpX(b) + getDpWidth(b));
    }

    /**
     * get rectangle for widget
     * @param widget
     * @return
     */
    static Rectangle getRectangle(NlComponent widget) {
        Rectangle rectangle = new Rectangle();
        rectangle.x = getDpX(widget);
        rectangle.y = getDpY(widget);
        rectangle.width = getDpWidth(widget);
        rectangle.height = getDpHeight(widget);
        return rectangle;
    }

    /**
     * Calculate the nearest widget
     *
     * @param direction the direction to check
     * @param list      list of other widgets (root == list[0])
     * @return the distance on that side
     */
    public static NlComponent gapWidget(Direction direction, Rectangle region,
            NlComponent[] list) {
        int rootWidth = getDpWidth(list[0].getParent());
        int rootHeight = getDpHeight(list[0].getParent());
        Rectangle rect = new Rectangle();

        switch (direction) {
            case TOP: {
                rect.y = 0;
                rect.x = region.x + 1;
                rect.width = region.width - 2;
                rect.height = region.y;
            }
            break;
            case BOTTOM: {
                rect.y = region.y + region.height + 1;
                rect.x = region.x + 1;
                rect.width = region.width - 2;
                rect.height = rootHeight - rect.y;
            }
            break;
            case LEFT: {
                rect.y = region.y + 1;
                rect.x = 0;
                rect.width = region.x;
                rect.height = region.height - 2;

            }
            break;
            case RIGHT: {
                rect.y = region.y + 1;
                rect.x = region.x + region.width + 1;
                rect.width = rootWidth - rect.x;
                rect.height = region.height - 2;
            }
            break;

        }
        int min = Integer.MAX_VALUE;
        NlComponent minWidget = null;
        for (NlComponent widget : list) {
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
    public static int gap(Direction direction, Rectangle region, NlComponent[] list) {
        int rootWidth = getDpWidth(list[0].getParent());
        int rootHeight = getDpHeight(list[0].getParent());
        Rectangle rect = new Rectangle();

        switch (direction) {
            case TOP: {
                rect.y = 0;
                rect.x = region.x + 1;
                rect.width = region.width - 2;
                rect.height = region.y;
            }
            break;
            case BOTTOM: {
                rect.y = region.y + region.height + 1;
                rect.x = region.x + 1;
                rect.width = region.width - 2;
                rect.height = rootHeight - rect.y;
            }
            break;
            case LEFT: {
                rect.y = region.y + 1;
                rect.x = 0;
                rect.width = region.x;
                rect.height = region.height - 2;

            }
            break;
            case RIGHT: {
                rect.y = region.y + 1;
                rect.x = region.x + region.width + 1;
                rect.width = rootWidth - rect.x;
                rect.height = region.height - 2;
            }
            break;

        }
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < list.length; i++) {
            NlComponent widget = list[i];

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
                case TOP:
                    return region.y;
                case BOTTOM:
                    return rootHeight - (region.y + region.height);
                case LEFT:
                    return region.x;
                case RIGHT:
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
    static int rootDistanceX(NlComponent widget) {
        int rootWidth = getDpWidth(widget.getParent());
        int aX = getDpX(widget);
        int aWidth = getDpWidth(widget);
        return Math.min(aX, rootWidth - (aX + aWidth));
    }

    /**
     * get the distance to widget's parent in Y
     *
     * @param widget
     * @return
     */
    static int rootDistanceY(NlComponent widget) {
        int rootHeight = getDpHeight(widget.getParent());
        int aY = getDpY(widget);
        int aHeight = getDpHeight(widget);
        return Math.min(aY, rootHeight - (aY + aHeight));
    }

    /**
     * Get the bounding box around a list of widgets
     *
     * @param widgets
     * @return
     */
    static Rectangle getBoundingBox(List<NlComponent> widgets) {
        Rectangle all = null;
        Rectangle tmp = new Rectangle();
        for (NlComponent widget : widgets) {
            if (isLine(widget)) {
                continue;
            }
            tmp.x = getDpX(widget);
            tmp.y = getDpY(widget);
            tmp.width = getDpWidth(widget);
            tmp.height = getDpHeight(widget);
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
    private static void reverse(NlComponent[] widgets) {
        for (int i = 0; i < widgets.length / 2; i++) {
            NlComponent widget = widgets[i];
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
    private static int rootDistance(NlComponent widget) {
        int rootHeight = getDpHeight(widget.getParent());
        int rootWidth = getDpWidth(widget.getParent());
        int aX = getDpX(widget);
        int aY = getDpY(widget);
        int aWidth = getDpHeight(widget);
        int aHeight = getDpWidth(widget);
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
    private static boolean isVerticallyConstrained(NlComponent widget) {
        if (ScoutWidget.isConnected(widget,Direction.BOTTOM)) {
            return true;
        }
        if (ScoutWidget.isConnected(widget,Direction.TOP)) {
            return true;
        }
        if (ScoutWidget.isConnected(widget,Direction.BASELINE)) {
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
    private static NlComponent nearestHorizontal(NlComponent nextTo,
            ArrayList<NlComponent> list) {
        int min = Integer.MAX_VALUE;
        NlComponent ret = null;
        int nextToLeft = getDpX(nextTo);
        int nextToRight = nextToLeft + getDpWidth(nextTo);
        for (NlComponent widget : list) {
            if (widget == nextTo) {
                continue;
            }

            int left = getDpX(widget);
            int right = left + getDpWidth(widget);
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

    /**
     * Wrapper for concept of anchor the "connector" on sides of a widget
     */
    class  Anchor {
        Direction myDirection;
        NlComponent mNlComponent;

        Anchor(NlComponent component, Direction dir){
            mNlComponent = component;
            myDirection = dir;
        }

        public boolean isConnected() {
            String[] attrs = ScoutWidget.ATTR_CONNECTIONS[myDirection.ordinal()];
            for (int i = 0; i < attrs.length; i++) {
                String attr = attrs[i];
                String id = mNlComponent.getAttribute(SdkConstants.SHERPA_URI, attr);
                if (id != null) {
                    return true;
                }
            }
            return false;
        }

        NlComponent getOwner() {
            return mNlComponent;
        }

        public boolean isConnectionAllowed(ScoutWidget component) {
            return false;
        }

        public int getMargin() {
            return ConstraintComponentUtilities.getMargin(mNlComponent,myDirection.getMarginString());
        }

        public  Direction getType() {
            return myDirection;
        }
    }

    public Anchor getAnchor(NlComponent component, Direction direction) {
        return new Anchor(component, direction);
    }
}
