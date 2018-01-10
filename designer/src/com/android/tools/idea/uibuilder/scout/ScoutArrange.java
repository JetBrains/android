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
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities;

import java.awt.Rectangle;
import java.util.*;

import static com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities.*;

/**
 * This implements the standard arrange functions
 */
public class ScoutArrange {
  static Comparator<NlComponent> sSortY = (w1, w2) -> ConstraintComponentUtilities.getDpY(w1) - ConstraintComponentUtilities.getDpY(w2);
  static Comparator<NlComponent> sSortX = (w1, w2) -> getDpX(w1) - getDpX(w2);

  static Comparator<ScoutWidget> sSortRecY = (w1, w2) -> w1.getDpY() - w2.getDpY();
  static Comparator<ScoutWidget> sSortRecX = (w1, w2) -> w1.getDpX() - w2.getDpX();

  /**
   * Perform various types of arrangements on a selection of widgets
   *
   * @param type             the type of change
   * @param widgetList       the list of selected widgets
   * @param applyConstraints if true apply related constraints (except expand and pack)
   */
  public static void align(Scout.Arrange type, List<NlComponent> widgetList,
                           boolean applyConstraints) {
    if (widgetList == null || widgetList.isEmpty()) {
      return;
    }
    if (widgetList.get(0).getParent() == null) {
      return;
    }
    ScoutWidget parentScoutWidget = new ScoutWidget(widgetList.get(0).getParent(), null);
    ScoutWidget[] scoutWidgets = ScoutWidget.create(widgetList, parentScoutWidget);

    int margin = Scout.getMargin();
    switch (type) {
      case AlignHorizontallyCenter:
      case AlignHorizontallyLeft:
      case AlignHorizontallyRight:
        Arrays.sort(scoutWidgets, sSortRecY);
        if (rootDistance(scoutWidgets[0].mNlComponent) > rootDistance(scoutWidgets[scoutWidgets.length - 1].mNlComponent)) {
          reverse(scoutWidgets);
        }
        break;
      case DistributeVertically:
        Arrays.sort(scoutWidgets, sSortRecY);
        break;
      case AlignVerticallyTop:
      case AlignVerticallyMiddle:
      case AlignBaseline:
      case AlignVerticallyBottom:
        Arrays.sort(scoutWidgets, sSortRecX);
        if (rootDistance(scoutWidgets[0].mNlComponent) > rootDistance(scoutWidgets[scoutWidgets.length - 1].mNlComponent)) {
          reverse(scoutWidgets);
        }
        break;
      case DistributeHorizontally:
        Arrays.sort(scoutWidgets, sSortRecX);
        break;
        default:
    }

    switch (type) {
      case ConnectTop:
        connect(scoutWidgets, parentScoutWidget, Direction.TOP, (applyConstraints) ? 0 : -1);
        break;
      case ConnectBottom:
        connect(scoutWidgets, parentScoutWidget, Direction.BOTTOM, (applyConstraints) ? 0 : -1);
        break;
      case ConnectStart:
        connect(scoutWidgets, parentScoutWidget, Direction.LEFT, (applyConstraints) ? 0 : -1);
        break;
      case ConnectEnd:
        connect(scoutWidgets, parentScoutWidget, Direction.RIGHT, (applyConstraints) ? 0 : -1);
        break;

      case CreateHorizontalChain: {
        Arrays.sort(scoutWidgets, sSortRecX);
        Rectangle rectangle = new Rectangle();
        NlComponent parent = parentScoutWidget.mNlComponent;
        ScoutWidget[] peers = ScoutWidget.create(parent.getChildren(), parentScoutWidget);
        ScoutWidget leftConnect = null;
        for (int i = 0; i < scoutWidgets.length; i++) {
          ScoutWidget widget = scoutWidgets[i];
          ScoutWidget rightConnect;
          if (i + 1 < scoutWidgets.length) {
            rightConnect = scoutWidgets[i + 1];
          }
          else {
            rectangle.x = widget.getDpX();
            rectangle.y = widget.getDpY();
            rectangle.width = widget.getDpWidth();
            rectangle.height = widget.getDpHeight();
            rightConnect = gapWidget(Direction.RIGHT, rectangle, peers, parentScoutWidget);
          }
          if (leftConnect == null) {
            rectangle.x = widget.getDpX();
            rectangle.y = widget.getDpY();
            rectangle.width = widget.getDpWidth();
            rectangle.height = widget.getDpHeight();
            leftConnect = gapWidget(Direction.LEFT, rectangle, peers, parentScoutWidget);
          }
          else {
            leftConnect = scoutWidgets[i - 1];
          }

          Direction dir = Direction.RIGHT;
          if (leftConnect == parentScoutWidget) {
            dir = Direction.LEFT;
          }
          scoutConnect(widget.mNlComponent, Direction.LEFT, leftConnect.mNlComponent, dir, 0);
          dir = Direction.LEFT;
          if (rightConnect == parentScoutWidget) {
            dir = Direction.RIGHT;
          }
          scoutConnect(widget.mNlComponent, Direction.RIGHT, rightConnect.mNlComponent, dir, 0);
          setScoutHorizontalBiasPercent(widget.mNlComponent, .5f);
        }
      }
      break;
      case CreateVerticalChain: {
        Arrays.sort(scoutWidgets, sSortRecY);
        Rectangle rectangle = new Rectangle();
        NlComponent parent = parentScoutWidget.mNlComponent;
        ScoutWidget[] peers = ScoutWidget.create(parent.getChildren(), parentScoutWidget);
        ScoutWidget topConnect = null;
        for (int i = 0; i < scoutWidgets.length; i++) {
          ScoutWidget widget = scoutWidgets[i];
          ScoutWidget bottomConnect;
          if (i + 1 < scoutWidgets.length) {
            bottomConnect = scoutWidgets[i + 1];
          }
          else {
            rectangle.x = widget.getDpX();
            rectangle.y = widget.getDpY();
            rectangle.width = widget.getDpWidth();
            rectangle.height = widget.getDpHeight();
            bottomConnect = gapWidget(Direction.BOTTOM, rectangle, peers, parentScoutWidget);
          }
          if (topConnect == null) {
            rectangle.x = widget.getDpX();
            rectangle.y = widget.getDpY();
            rectangle.width = widget.getDpWidth();
            rectangle.height = widget.getDpHeight();
            topConnect = gapWidget(Direction.TOP, rectangle, peers, parentScoutWidget);
          }
          else {
            topConnect = scoutWidgets[i - 1];
          }

          Direction dir = Direction.BOTTOM;
          if (topConnect == parentScoutWidget) {
            dir = Direction.TOP;
          }
          scoutConnect(widget.mNlComponent, Direction.TOP, topConnect.mNlComponent, dir, 0);
          dir = Direction.TOP;
          if (bottomConnect == parentScoutWidget) {
            dir = Direction.BOTTOM;
          }
          scoutConnect(widget.mNlComponent, Direction.BOTTOM, bottomConnect.mNlComponent, dir, 0);
          setScoutHorizontalBiasPercent(widget.mNlComponent, .5f);
        }
    }
        break;
      case CenterHorizontally: {
        Rectangle rectangle = new Rectangle();
        NlComponent parent = parentScoutWidget.mNlComponent;
        ScoutWidget[] peers = ScoutWidget.create(parent.getChildren(), parentScoutWidget);

        for (ScoutWidget widget : scoutWidgets) {
          rectangle.x = widget.getDpX();
          rectangle.y = widget.getDpY();
          rectangle.width = widget.getDpWidth();
          rectangle.height = widget.getDpHeight();
          int westDistance = gap(Direction.LEFT, rectangle, peers, parentScoutWidget);
          int eastDistance = gap(Direction.RIGHT, rectangle, peers, parentScoutWidget);

          int x = widget.getDpX();

          if (applyConstraints) {
            ScoutWidget westConnect =
              gapWidget(Direction.LEFT, rectangle, peers, parentScoutWidget);
            ScoutWidget eastConnect =
              gapWidget(Direction.RIGHT, rectangle, peers, parentScoutWidget);

            Direction dir = Direction.RIGHT;
            if (westConnect == parentScoutWidget) {
              dir = Direction.LEFT;
            }
            scoutConnect(widget.mNlComponent, Direction.LEFT, westConnect.mNlComponent, dir, 0);
            dir = Direction.LEFT;
            if (eastConnect == parentScoutWidget) {
              dir = Direction.RIGHT;
            }
            scoutConnect(widget.mNlComponent, Direction.RIGHT, eastConnect.mNlComponent, dir, 0);
            setScoutHorizontalBiasPercent(widget.mNlComponent, .5f);
          }
          else {
            setScoutAbsoluteDpX(widget.mNlComponent, x + (eastDistance - westDistance) / 2, true);
          }
        }
      }
      break;
      case CenterVertically: {
        Rectangle rectangle = new Rectangle();
        NlComponent parent = parentScoutWidget.mNlComponent;
        ScoutWidget[] peers = ScoutWidget.create(parent.getChildren(), parentScoutWidget);

        for (ScoutWidget widget : scoutWidgets) {
          rectangle.x = widget.getDpX();
          rectangle.y = widget.getDpY();
          rectangle.width = widget.getDpWidth();
          rectangle.height = widget.getDpHeight();
          int northDistance = gap(Direction.TOP, rectangle, peers, parentScoutWidget);
          int southDistance = gap(Direction.BOTTOM, rectangle, peers, parentScoutWidget);
          int Y = widget.getDpY();

          if (applyConstraints) {
            ScoutWidget northConnect =
              gapWidget(Direction.TOP, rectangle, peers, parentScoutWidget);
            ScoutWidget southConnect =
              gapWidget(Direction.BOTTOM, rectangle, peers, parentScoutWidget);

            Direction dir = Direction.BOTTOM;
            if (northConnect == parentScoutWidget) {
              dir = Direction.TOP;
            }
            scoutConnect(widget.mNlComponent, Direction.TOP, northConnect.mNlComponent, dir, 0);
            dir = Direction.TOP;
            if (southConnect == parentScoutWidget) {
              dir = Direction.BOTTOM;
            }
            scoutConnect(widget.mNlComponent, Direction.BOTTOM, southConnect.mNlComponent, dir, 0);
            setScoutVerticalBiasPercent(widget.mNlComponent, .5f);
          }
          else {
            setScoutAbsoluteDpY(widget.mNlComponent, Y + (southDistance - northDistance) / 2, true);
          }
        }
      }
      break;
      case CenterHorizontallyInParent: {
        for (ScoutWidget widget : scoutWidgets) {
          int parentWidth = parentScoutWidget.getDpWidth();
          int width = widget.getDpWidth();
          setScoutAbsoluteDpX(widget.mNlComponent, (parentWidth - width) / 2, true);
          if (applyConstraints) {
            scoutConnect(widget.mNlComponent, Direction.LEFT, parentScoutWidget.mNlComponent, Direction.LEFT, 0);
            scoutConnect(widget.mNlComponent, Direction.RIGHT, parentScoutWidget.mNlComponent, Direction.RIGHT, 0);
            setScoutHorizontalBiasPercent(widget.mNlComponent, .5f);
          }
        }
      }
      break;
      case CenterVerticallyInParent: {
        for (ScoutWidget widget : scoutWidgets) {
          int parentHeight = parentScoutWidget.getDpHeight();
          int height = widget.getDpHeight();
          setScoutAbsoluteDpY(widget.mNlComponent, (parentHeight - height) / 2, true);
          if (applyConstraints) {
            scoutConnect(widget.mNlComponent, Direction.TOP, parentScoutWidget.mNlComponent, Direction.TOP, 0);
            scoutConnect(widget.mNlComponent, Direction.BOTTOM, parentScoutWidget.mNlComponent, Direction.BOTTOM, 0);
            setScoutVerticalBiasPercent(widget.mNlComponent, .5f);
          }
        }
      }
      break;
      case AlignHorizontallyCenter: {
        int count = 0;
        float avg = 0;

        for (ScoutWidget widget : scoutWidgets) {
          avg += widget.getDpX() + widget.getDpWidth() / 2.0f;
          count++;
        }
        avg /= count;

        NlComponent previousWidget = null;
        for (ScoutWidget widget : scoutWidgets) {
          float current = widget.getDpWidth() / 2.0f;
          setScoutAbsoluteDpX(widget.mNlComponent, (int)(avg - current), true);
          if (applyConstraints) {
            if (previousWidget != null) {
              scoutConnect(widget.mNlComponent, Direction.LEFT, previousWidget, Direction.LEFT, 0);
              scoutConnect(widget.mNlComponent, Direction.RIGHT, previousWidget, Direction.RIGHT, 0);
            }
          }
          previousWidget = widget.mNlComponent;
        }
      }
      break;
      case AlignHorizontallyLeft: {
        int min = Integer.MAX_VALUE;
        if (applyConstraints) { // test if already connected to flip directions
          flipConnectionsAndReverse(scoutWidgets, Direction.LEFT, ourLeftAttributes, ourStartAttributes);
        }
        for (ScoutWidget widget : scoutWidgets) {
          min = Math.min(min, widget.getDpX());
        }
        NlComponent previousWidget = null;
        for (ScoutWidget widget : scoutWidgets) {
          setScoutAbsoluteDpX(widget.mNlComponent, min, true);
          if (applyConstraints) {
            if (previousWidget != null) {
              scoutClearAttributes(widget.mNlComponent, ourRightAttributes);
              scoutClearAttributes(widget.mNlComponent, ourEndAttributes);

              scoutConnect(widget.mNlComponent, Direction.LEFT, previousWidget,
                           Direction.LEFT, 0);
            }
          }
          previousWidget = widget.mNlComponent;
        }
      }
      break;
      case AlignHorizontallyRight: {
        int max = Integer.MIN_VALUE;
        if (applyConstraints) { // test if already connected to flip directions
          flipConnectionsAndReverse(scoutWidgets, Direction.RIGHT, ourRightAttributes, ourEndAttributes);
        }
        for (ScoutWidget widget : scoutWidgets) {
          max = Math.max(max, widget.getDpX() + widget.getDpWidth());
        }
        NlComponent previousWidget = null;
        for (ScoutWidget widget : scoutWidgets) {
          float current = widget.getDpWidth();
          setScoutAbsoluteDpX(widget.mNlComponent, (int)(max - current), true);
          if (applyConstraints) {
            if (previousWidget != null) {
              scoutClearAttributes(widget.mNlComponent, ourLeftAttributes);
              scoutClearAttributes(widget.mNlComponent, ourStartAttributes);
              scoutConnect(widget.mNlComponent, Direction.RIGHT, previousWidget,
                           Direction.RIGHT, 0);
            }
          }
          previousWidget = widget.mNlComponent;
        }
      }
      break;
      case AlignVerticallyTop: {
        int min = Integer.MAX_VALUE;
        if (applyConstraints) { // test if already connected to flip directions
          flipConnectionsAndReverse(scoutWidgets, Direction.TOP, ourTopAttributes, null);
        }
        for (ScoutWidget widget : scoutWidgets) {
          min = Math.min(min, widget.getDpY());
        }
        NlComponent previousWidget = null;
        for (ScoutWidget widget : scoutWidgets) {
          setScoutAbsoluteDpY(widget.mNlComponent, min, true);
          if (applyConstraints) {
            if (previousWidget != null) {
              scoutClearAttributes(widget.mNlComponent, ourBottomAttributes);
              scoutConnect(widget.mNlComponent, Direction.TOP, previousWidget,
                           Direction.TOP, 0);
            }
          }
          previousWidget = widget.mNlComponent;
        }
      }
      break;
      case AlignVerticallyMiddle: {
        int count = 0;
        float avg = 0;
        for (ScoutWidget widget : scoutWidgets) {
          avg += widget.getDpY() + widget.getDpHeight() / 2.0f;
          count++;
        }
        avg /= count;
        NlComponent previousWidget = null;
        for (ScoutWidget widget : scoutWidgets) {
          float current = widget.getDpHeight() / 2.0f;
          setScoutAbsoluteDpY(widget.mNlComponent, (int)(avg - current), true);
          if (applyConstraints) {
            if (previousWidget != null) {
              scoutConnect(widget.mNlComponent, Direction.TOP, previousWidget, Direction.TOP, 0);
              scoutConnect(widget.mNlComponent, Direction.BOTTOM, previousWidget, Direction.BOTTOM, 0);
            }
          }
          previousWidget = widget.mNlComponent;
        }
      }
      break;
      case AlignBaseline: {
        int count = 0;
        float avg = 0;
        if (applyConstraints) { // test if already connected to flip directions
          flipBaselineAndReverse(scoutWidgets);
        }
        int number_of_constrained = 0;
        NlComponent fixedWidget = null;
        for (ScoutWidget widget : scoutWidgets) {
          if (isVerticallyConstrained(widget.mNlComponent)) {
            number_of_constrained++;
            fixedWidget = widget.mNlComponent;
          }
          avg += widget.getDpY() + widget.getDpBaseline();
          count++;
        }
        avg /= count;
        // if one is already constrained move the rest to it
        if (number_of_constrained == 1) {
          avg = getDpX(fixedWidget) + getDpBaseline(fixedWidget);
        }
        NlComponent previousWidget = null;
        if (!applyConstraints || number_of_constrained == 0) {
          for (ScoutWidget widget : scoutWidgets) {
            float baseline = widget.getDpBaseline();
            setScoutAbsoluteDpY(widget.mNlComponent, (int)(avg - baseline), true);
            if (applyConstraints) {
              if (previousWidget != null) {
                scoutConnect(widget.mNlComponent, Direction.BASELINE, previousWidget,
                             Direction.BASELINE, 0);
              }
            }
            previousWidget = widget.mNlComponent;
          }
        }
        else { // if you are creating constraints and some are already constrained
          // Build a list of constrained and unconstrained widgets
          ArrayList<ScoutWidget> unconstrained = new ArrayList<>();
          ArrayList<ScoutWidget> constrained = new ArrayList<>();
          for (ScoutWidget widget : scoutWidgets) {
            if (isVerticallyConstrained(widget.mNlComponent)) {
              constrained.add(widget);
            }
            else {
              unconstrained.add(widget);
            }
          }
          // one by one constrain widgets by finding the closest between the two list
          while (!unconstrained.isEmpty()) {
            ScoutWidget to = null;
            ScoutWidget from = null;
            int min = Integer.MAX_VALUE;

            for (ScoutWidget fromCandidate : unconstrained) {
              for (ScoutWidget toCandidate : constrained) {
                int fromLeft = fromCandidate.getDpX();
                int fromRight = fromLeft + fromCandidate.getDpWidth();
                int toLeft = toCandidate.getDpX();
                int toRight = toLeft + toCandidate.getDpWidth();
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
            scoutConnect(from.mNlComponent, Direction.BASELINE, to.mNlComponent,
                         Direction.BASELINE, 0);
            constrained.add(from);
            unconstrained.remove(from);
          }
        }
      }
      break;
      case AlignVerticallyBottom: {
        int max = Integer.MIN_VALUE;
        if (applyConstraints) { // test if already connected to flip directions
          flipConnectionsAndReverse(scoutWidgets, Direction.BOTTOM, ourBottomAttributes, null);
        }
        for (ScoutWidget widget : scoutWidgets) {
          max = Math.max(max, widget.getDpY() + widget.getDpHeight());
        }
        NlComponent previousWidget = null;
        for (ScoutWidget widget : scoutWidgets) {
          float current = widget.getDpHeight();
          setScoutAbsoluteDpY(widget.mNlComponent, (int)(max - current), true);
          if (applyConstraints) {
            if (previousWidget != null) {
              scoutClearAttributes(widget.mNlComponent, ourTopAttributes);
              scoutConnect(widget.mNlComponent, Direction.BOTTOM, previousWidget,
                           Direction.BOTTOM, 0);
            }
          }
          previousWidget = widget.mNlComponent;
        }
      }
      break;
      case DistributeVertically: {
        int count = 0;
        int sum = 0;
        int min = getDpY(widgetList.get(0));
        int max = getDpY(widgetList.get(0)) + getDpHeight(widgetList.get(0));
        for (ScoutWidget widget : scoutWidgets) {
          int start = widget.getDpY();
          int size = widget.getDpHeight();
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
          rootDistanceY(scoutWidgets[0].mNlComponent) > rootDistanceY(scoutWidgets[scoutWidgets.length - 1].mNlComponent);
        for (int i = 0; i < count; i++) {
          if (i > 0) {
            int size = scoutWidgets[i - 1].getDpHeight();
            min += size;
            int pos = min + (totalGap * i) / gaps;
            setScoutAbsoluteDpY(scoutWidgets[i].mNlComponent, pos, true);
            if (applyConstraints) {
              if (reverse) {
                scoutConnect(scoutWidgets[i - 1].mNlComponent, Direction.BOTTOM, scoutWidgets[i].mNlComponent,
                             Direction.TOP, pos - lastY - size);
              }
              else {
                scoutConnect(scoutWidgets[i].mNlComponent, Direction.TOP, scoutWidgets[i - 1].mNlComponent,
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
        for (ScoutWidget widget : scoutWidgets) {
          int start = widget.getDpX();
          int size = widget.getDpWidth();
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
          rootDistanceX(scoutWidgets[0].mNlComponent) > rootDistanceX(scoutWidgets[scoutWidgets.length - 1].mNlComponent);
        for (int i = 0; i < count; i++) {

          if (i > 0) {
            int size = scoutWidgets[i - 1].getDpWidth();
            min += size;
            int pos = min + (totalGap * i) / gaps;
            setScoutAbsoluteDpX(scoutWidgets[i].mNlComponent, pos, true);
            if (applyConstraints) {
              if (reverse) {
                scoutConnect(scoutWidgets[i - 1].mNlComponent, Direction.RIGHT, scoutWidgets[i].mNlComponent,
                             Direction.LEFT, pos - lastX - size);
              }
              else {
                scoutConnect(scoutWidgets[i].mNlComponent, Direction.LEFT, scoutWidgets[i - 1].mNlComponent,
                             Direction.RIGHT, pos - lastX - size);
              }
              lastX = pos;
            }
          }
        }
      }
      break;
      case VerticalPack: {
        NlComponent[] wArray = new NlComponent[widgetList.size()];
        wArray = widgetList.toArray(wArray);
        Arrays.sort(wArray, (w1, w2) -> Integer.compare(getDpY(w1), getDpY(w2)));
        ScoutWidget[] list = ScoutWidget.getWidgetArray(widgetList.get(0).getParent());
        Rectangle bounds = null;
        for (int i = 0; i < wArray.length; i++) {
          String id = SdkConstants.NEW_ID_PREFIX + wArray[i].getId();
          ScoutWidget w = list[0].getChild(id);
          if (bounds == null) {
            bounds = new Rectangle(w.getRectangle());
          }
          else {
            bounds = bounds.union(w.getRectangle());
          }
        }

        for (NlComponent cw : wArray) {
          for (ScoutWidget scoutWidget : list) {
            if (scoutWidget.mNlComponent == cw) {
              int gapN = scoutWidget.gap(Direction.TOP, list);
              int newY = margin + scoutWidget.getDpY() - gapN;
              newY = Math.max(newY, bounds.y);
              scoutWidget.setY(newY);
            }
          }
        }
      }
      break;
      case HorizontalPack: {
        NlComponent[] wArray = new NlComponent[widgetList.size()];
        wArray = widgetList.toArray(wArray);
        Arrays.sort(wArray, (w1, w2) -> Integer.compare(getDpX(w1), getDpX(w2)));
        ScoutWidget[] list = ScoutWidget.getWidgetArray(widgetList.get(0).getParent());
        Rectangle bounds = null;
        for (int i = 0; i < wArray.length; i++) {
          String id = SdkConstants.NEW_ID_PREFIX + wArray[i].getId();
          ScoutWidget w = list[0].getChild(id);
          if (bounds == null) {
            bounds = new Rectangle(w.getRectangle());
          }
          else {
            bounds = bounds.union(w.getRectangle());
          }
        }
        for (NlComponent cw : wArray) {
          for (ScoutWidget scoutWidget : list) {
            if (scoutWidget.mNlComponent == cw) {
              int gapW = scoutWidget.gap(Direction.LEFT, list);
              int newX = margin + getDpX(scoutWidget.mNlComponent) - gapW;
              newX = Math.max(newX, bounds.x);
              scoutWidget.setX(newX);
            }
          }
        }
      }
      break;
      case ExpandVertically: {
        expandVertically(scoutWidgets, parentScoutWidget, margin, true);
      }
      break;
      case ExpandHorizontally: {
        expandHorizontally(scoutWidgets, parentScoutWidget, margin, true);
      }
      break;
      case ChainVerticalRemove:
      case ChainHorizontalRemove:
      case ChainVerticalMoveUp:
      case ChainVerticalMoveDown:
      case ChainHorizontalMoveLeft:
      case ChainHorizontalMoveRight:
      case ChainInsertHorizontal:
      case ChainInsertVertical:
        break; // cases covered by scout
    }
  }

  /**
   * Expands widgets vertically in an evenly spaced manner
   *  @param widgetList
   * @param margin
   * @param apply
   */
  public static void expandVertically(ScoutWidget[] list, ScoutWidget parent, int margin, boolean apply) {

    ScoutWidget[] pears = ScoutWidget.create(parent.mNlComponent.getChildren(), parent);

    Rectangle selectBounds = getBoundingBox(list);

    Rectangle clip = new Rectangle();
    int gapNorth = gap(Direction.TOP, selectBounds, pears, parent);
    int gapSouth = gap(Direction.BOTTOM, selectBounds, pears, parent);

    clip.y = selectBounds.y - gapNorth;
    clip.height = selectBounds.height + gapSouth + gapNorth;

    ArrayList<ScoutWidget> selectedList = new ArrayList<ScoutWidget>(Arrays.asList(list));
    while (!selectedList.isEmpty()) {
      ScoutWidget widget = selectedList.remove(0);
      ArrayList<ScoutWidget> col = new ArrayList<>();
      col.add(widget);
      for (Iterator<ScoutWidget> iterator = selectedList.iterator();
           iterator.hasNext(); ) {
        ScoutWidget elem = iterator.next();
        if (isSameColumn(widget, elem)) {
          if (!col.contains(elem)) {
            col.add(elem);
          }
          iterator.remove();
        }
      }
      ScoutWidget[] colArray = new ScoutWidget[col.size()];
      colArray = col.toArray(colArray);
      Arrays.sort(colArray, sSortRecY);
      int gaps = (colArray.length - 1) * margin;
      int totalHeight = (clip.height - gaps - 2 * margin);

      for (int i = 0; i < colArray.length; i++) {
        int y = margin * i + (i * (totalHeight)) / colArray.length;
        ScoutWidget constraintWidget = colArray[i];
        setScoutAbsoluteDpY(constraintWidget.mNlComponent, y + clip.y + margin, apply);
        int yend = margin * i + (totalHeight * (i + 1)) / colArray.length;
        setScoutAbsoluteDpHeight(constraintWidget.mNlComponent, yend - y, apply);
        constraintWidget.setY(y + clip.y + margin);
        constraintWidget.setHeight(yend - y);
      }
    }
  }

  /**
   * Expands widgets horizontally in an evenly spaced manner
   *  @param widgetList
   * @param margin
   * @param apply
   */
  public static void expandHorizontally(ScoutWidget[] list, ScoutWidget parent, int margin, boolean apply) {
    ScoutWidget[] pears = ScoutWidget.create(parent.mNlComponent.getChildren(), parent);
    Rectangle selectBounds = getBoundingBox(list);

    Rectangle clip = new Rectangle();
    int gapWest = gap(Direction.LEFT, selectBounds, pears, parent);
    int gapEast = gap(Direction.RIGHT, selectBounds, pears, parent);
    clip.x = selectBounds.x - gapWest;
    clip.width = selectBounds.width + gapEast + gapWest;
    ArrayList<ScoutWidget> selectedList;
    selectedList = new ArrayList<ScoutWidget>(Arrays.asList(list));
    while (!selectedList.isEmpty()) {
      ScoutWidget widget = selectedList.remove(0);
      ArrayList<ScoutWidget> row = new ArrayList<>();
      row.add(widget);
      for (Iterator<ScoutWidget> iterator = selectedList.iterator();
           iterator.hasNext(); ) {
        ScoutWidget elem = iterator.next();
        if (isSameRow(widget, elem)) {
          if (!row.contains(elem)) {
            row.add(elem);
          }
          iterator.remove();
        }
      }

      ScoutWidget[] rowArray = new ScoutWidget[row.size()];
      rowArray = row.toArray(rowArray);
      Arrays.sort(rowArray, sSortRecX);
      int gaps = (rowArray.length - 1) * margin;
      int totalWidth = (clip.width - gaps - 2 * margin);

      for (int i = 0; i < rowArray.length; i++) {
        int x = margin * i + (i * (totalWidth)) / rowArray.length;
        ScoutWidget constraintWidget = rowArray[i];
        setScoutAbsoluteDpX(constraintWidget.mNlComponent, x + clip.x + margin, apply);
        int xend = margin * i + (totalWidth * (i + 1)) / rowArray.length;

        setScoutAbsoluteDpWidth(constraintWidget.mNlComponent, xend - x, apply);
        constraintWidget.setX(x + clip.x + margin);
        constraintWidget.setWidth(xend - x);
      }
    }
  }

  /**
   * are the two widgets in the same horizontal area
   *
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
   *
   * @param a
   * @param b
   * @return true if aligned
   */
  static boolean isSameColumn(NlComponent a, NlComponent b) {
    return Math.max(getDpX(a), getDpX(b)) <
           Math.min(getDpX(a) + getDpWidth(a), getDpX(b) + getDpWidth(b));
  }

  /**
   * are the two widgets in the same horizontal area
   *
   * @param a
   * @param b
   * @return true if aligned
   */
  static boolean isSameRow(ScoutWidget a, ScoutWidget b) {
    return Math.max(a.getDpY(), b.getDpY()) <
           Math.min(a.getDpY() + a.getDpHeight(), b.getDpY() + b.getDpHeight());
  }

  /**
   * are the two widgets in the same vertical area
   *
   * @param a
   * @param b
   * @return true if aligned
   */
  static boolean isSameColumn(ScoutWidget a, ScoutWidget b) {
    return Math.max(a.getDpX(), b.getDpX()) <
           Math.min(a.getDpX() + a.getDpWidth(), b.getDpX() + b.getDpWidth());
  }

  /**
   * get rectangle for widget
   *
   * @param widget
   * @return
   */
  static Rectangle getRectangle(ScoutWidget widget) {
    Rectangle rectangle = new Rectangle();
    rectangle.x = widget.getDpX();
    rectangle.y = widget.getDpY();
    rectangle.width = widget.getDpWidth();
    rectangle.height = widget.getDpHeight();
    if (ConstraintComponentUtilities.isVerticalLine(widget.mNlComponent)) {
      rectangle.height = widget.getParent().getDpHeight();
      rectangle.y = 0;
    }
    else if (ConstraintComponentUtilities.isHorizontalLine(widget.mNlComponent)) {
      rectangle.width = widget.getParent().getDpWidth();
      rectangle.x = 0;
    }
    return rectangle;
  }

  /**
   * Calculate the nearest widget
   *
   * @param direction the direction to check
   * @param list      list of other widgets (root == list[0])
   * @return the distance on that side
   */
  public static ScoutWidget gapWidget(Direction direction, Rectangle region,
                                      ScoutWidget[] list, ScoutWidget parent) {
    int rootWidth = parent.getDpWidth();
    int rootHeight = parent.getDpHeight();
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
      default:
    }
    int min = Integer.MAX_VALUE;
    ScoutWidget minWidget = null;
    for (ScoutWidget widget : list) {
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

  public static void flipBaselineAndReverse(ScoutWidget[] scoutWidgets) {
    ScoutWidget last = null;
    boolean isAlreadyConnected = true;
    for (ScoutWidget widget : scoutWidgets) {
      if (last != null) {
        if (!(widget.isConnected(Direction.BASELINE))) {
          isAlreadyConnected = false;
        }
      }
      last = widget;
    }
    if (isAlreadyConnected) {
      reverse(scoutWidgets);
    }
    for (ScoutWidget widget : scoutWidgets) {
      scoutClearAttributes(widget.mNlComponent, ourBaselineAttributes);
    }
  }

  public static void flipConnectionsAndReverse(ScoutWidget[] scoutWidgets, Direction direction, ArrayList<String> clear, ArrayList<String> clearRtl) {
    ScoutWidget last = null;
    boolean isAlreadyConnected = true;
    for (ScoutWidget widget : scoutWidgets) {
      if (last != null) {
        if (!(widget.isConnected(direction) && !widget.isConnected(direction.getOpposite()))) {
          isAlreadyConnected = false;
        }
      }
      last = widget;
    }
    if (isAlreadyConnected) {
      reverse(scoutWidgets);
    }
    if (scoutWidgets[0].isConnected(direction)) {
      for (ScoutWidget widget : scoutWidgets) {
        if (scoutWidgets[0].isConnected(direction, widget)) {
          scoutClearAttributes(scoutWidgets[0].mNlComponent, clear);
          if (clearRtl != null) {
            scoutClearAttributes(scoutWidgets[0].mNlComponent, clearRtl);
          }
          break;
        }
      }
    }
  }

  /**
   * Calculate the gap in to the nearest widget
   *
   * @param direction the direction to check
   * @param list      list of other widgets (root == list[0])
   * @return the distance on that side
   */
  public static int gap(Direction direction, Rectangle region, ScoutWidget[] list, ScoutWidget root) {
    int rootX = root.getDpWidth();
    int rootY = root.getDpHeight();
    int rootWidth = root.getDpWidth();
    int rootHeight = root.getDpHeight();
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
      default:
    }
    int min = Integer.MAX_VALUE;
    for (int i = 0; i < list.length; i++) {
      ScoutWidget widget = list[i];

      Rectangle r = getRectangle(widget);
      if (r.intersects(rect)) {
        int dist = (int)distance(r, region);
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
          default:
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
    float xReturn = (yOverlap) ? xmin : (float)Math.hypot(xmin, ymin);
    float yReturn = (xOverlap) ? ymin : (float)Math.hypot(xmin, ymin);
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
  static Rectangle getBoundingBox(ScoutWidget[] widgets) {
    Rectangle all = null;
    Rectangle tmp = new Rectangle();
    for (ScoutWidget widget : widgets) {
      if (isLine(widget.mNlComponent)) {
        continue;
      }
      tmp.x = widget.getDpX();
      tmp.y = widget.getDpY();
      tmp.width = widget.getDpWidth();
      tmp.height = widget.getDpHeight();
      if (all == null) {
        all = new Rectangle(tmp);
      }
      else {
        all = all.union(tmp);
      }
    }
    return all;
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
      }
      else {
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
   * in place Reverses the order of the widgets
   *
   * @param widgets to reverse
   */
  private static void reverse(ScoutWidget[] widgets) {
    for (int i = 0; i < widgets.length / 2; i++) {
      ScoutWidget widget = widgets[i];
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
    if (ScoutWidget.isConnected(widget, Direction.BOTTOM)) {
      return true;
    }
    if (ScoutWidget.isConnected(widget, Direction.TOP)) {
      return true;
    }
    if (ScoutWidget.isConnected(widget, Direction.BASELINE)) {
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
  class Anchor {
    Direction myDirection;
    NlComponent mNlComponent;

    Anchor(NlComponent component, Direction dir) {
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

    public Direction getType() {
      return myDirection;
    }
  }

  public Anchor getAnchor(NlComponent component, Direction direction) {
    return new Anchor(component, direction);
  }

  /**
   * Connect a widget to its neighbour
   *
   * @param scoutWidgets      The widget
   * @param parentScoutWidget it parent
   * @param dir               the direction to connect LEFT, RIGHT, TOP BOTTOM
   * @param margin            the margin to set or -1 to keep the current distance
   */
  public static void connect(ScoutWidget[] scoutWidgets, ScoutWidget parentScoutWidget, Direction dir, int margin) {
    Rectangle rectangle = new Rectangle();
    NlComponent parent = parentScoutWidget.mNlComponent;
    ScoutWidget[] peers = ScoutWidget.create(parent.getChildren(), parentScoutWidget);
    for (ScoutWidget widget : scoutWidgets) {
      rectangle.x = widget.getDpX();
      rectangle.y = widget.getDpY();
      rectangle.width = widget.getDpWidth();
      rectangle.height = widget.getDpHeight();
      int dist = gap(dir, rectangle, peers, parentScoutWidget);
      ScoutWidget connect = gapWidget(dir, rectangle, peers, parentScoutWidget);
      Direction connectDir = (connect != parentScoutWidget) ? dir.getOpposite() : dir;
      if (connect != null) {
        scoutConnect(widget.mNlComponent, dir, connect.mNlComponent, connectDir, (margin == -1) ? dist : margin);
      }
    }
  }
}
