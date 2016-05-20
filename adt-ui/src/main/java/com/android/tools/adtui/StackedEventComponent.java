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

package com.android.tools.adtui;

import com.android.tools.adtui.common.AdtUIUtils;
import com.android.tools.adtui.model.EventAction;
import com.android.tools.adtui.model.EventRenderData;
import com.android.tools.adtui.model.RangedSimpleSeries;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.PriorityQueue;

/**
 * A chart component that renders lines with a title that have the ability to stack.
 */
public class StackedEventComponent extends AnimatedComponent {

  /**
   * Enum that defines an activity state. Each activity started action, should have an associated
   * activity completed action.
   */
  public enum Action {
    ACTIVITY_STARTED,
    ACTIVITY_COMPLETED,
  }

  private static final Color DISABLED_ACTION = new Color(137, 157, 179);
  private static final Color ENABLED_ACTION = new Color(93, 185, 98);
  private static final String ELLIPSIS = "...";
  private static final int ELLIPSIS_LENGTH = ELLIPSIS.length();
  private static final int CHARACTERS_TO_SHRINK_BY = 1;
  private static final int TAIL_HEIGHT = 4;
  private static final int SEGMENT_SPACING = 4;
  private static final int NORMALIZED_END = 1;
  private static final float EPSILON = .99f;

  @NotNull
  private final RangedSimpleSeries<EventAction<Action, String>> mData;

  @NotNull
  private final ArrayList<Shape> mPaths;

  private final int myMaxHeight;

  /**
   * This map is used to pair actions, to their draw location. This is used primarily to store the
   * location where to draw the name of the incoming event.
   */
  private HashMap<EventAction<Action, String>, EventRenderData> myActionToDrawLocationMap =
    new HashMap<EventAction<Action, String>, EventRenderData>();

  /**
   * @param data The state chart data.
   */
  public StackedEventComponent(int maxHeight,
                               @NotNull RangedSimpleSeries<EventAction<Action, String>> data) {
    mData = data;
    myMaxHeight = maxHeight;
    mPaths = new ArrayList<Shape>();
  }

  @Override
  protected void updateData() {
    double min = mData.getRange().getMin();
    double max = mData.getRange().getMax();

    // A map of EventAction started events to their start time, so we can correlate these to
    // EventAction competed events with the EventAction start events. This is done this way as
    // a event started and completed events may come in in any order at any time.
    // TODO: Combine start/stop events, that means pulling this logic out into the supportlib.
    HashMap<Long, EventAction<Action, String>> downEvents
      = new HashMap<Long, EventAction<Action, String>>();

    // This map is needed because the index to draw our path can change from the time we get a
    // start event to the time we get a completed event. The key in this map and the key in the
    // downEvents map should be the same. The value in this map is the index that the start
    // event is drawn at. We use this when we get an end event to draw the completed line at the
    // same index.
    HashMap<Long, Integer> drawOrderIndex = new HashMap<Long, Integer>();

    // A queue of open index values, this allows us to pack our events without leaving gaps.
    PriorityQueue<Integer> offsetValues = new PriorityQueue<Integer>();
    mPaths.clear();
    myActionToDrawLocationMap.clear();
    int lastIndex = 0;
    EventAction<Action, String> lastStart = null;
    int lastStartIndex = 0;

    // Loop through the data series looking at all of the start events, and stop events.
    // For each start event we store off its EventAction until we find an associated stop event.
    // Once we find a stop event we determine the draw order, name, start and stop locations and
    // cache off a path to draw.
    for (int i = 0; i < mData.getSeries().size(); i++) {
      EventAction<Action, String> data = mData.getSeries().get(i);
      if (!drawOrderIndex.containsKey(data.getStart())) {

        // The index is used to determine what height we want to draw the activity line at.
        // This condition enables us to pack activity lines, so if there is a gap between
        // line 0 and line 2 the first index in the queue will be 1. The final behavior of
        // this is undefined so this will do for now.
        int index = lastIndex;
        if (offsetValues.size() != 0) {
          index = offsetValues.remove();
        }
        else {
          lastIndex++;
        }
        drawOrderIndex.put(data.getStart(), index);

        // Here we pair the activity_started event with an activity_completed event
        if (lastStart == null) {
          lastStart = data;
          lastStartIndex = index;
        }
        else if (index >= lastStartIndex) {
          myActionToDrawLocationMap.put(lastStart, new EventRenderData(lastStartIndex,
                                                                       data.getStart() - lastStart.getStart()));
          lastStart = data;
          lastStartIndex = index;
        }
      }
      if (data.getValue() == Action.ACTIVITY_STARTED) {
        downEvents.put(data.getStart(), data);
      }
      else if (data.getValue() == Action.ACTIVITY_COMPLETED) {
        assert downEvents.containsKey(data.getStart());
        EventAction<Action, String> event = downEvents.get(data.getStart());
        int index = drawOrderIndex.get(event.getStart());
        drawOrderIndex.remove(event.getStart());
        offsetValues.add(index);
        Path2D.Float path = new Path2D.Float();
        // Here we normalize the position to a value between 0 and 1. This allows us to scale the width of the line based on the
        // width of our chart.
        double normalizedEndPosition = ((data.getEnd() - min) / (max - min));
        double normalizedstartPosition = ((event.getStart() - min) / (max - min));
        double baseHeight = myMaxHeight - (index * SEGMENT_SPACING);
        double tailHeight = myMaxHeight - (index * SEGMENT_SPACING - TAIL_HEIGHT);
        path.moveTo(normalizedEndPosition, baseHeight);
        path.lineTo(normalizedEndPosition, tailHeight);
        path.lineTo(normalizedstartPosition, tailHeight);
        path.lineTo(normalizedstartPosition, baseHeight);
        mPaths.add(path); //TODO CULL if end is off the screen.
        if (lastStart != null && index == lastStartIndex) {
          myActionToDrawLocationMap.put(lastStart,
                                        new EventRenderData(lastStartIndex,
                                                            data.getEnd() - lastStart.getStart()));
          lastStart = null;
          lastStartIndex = 0;
        }
        downEvents.remove(data.getStart());
      }
    }
    if (lastStart != null) {
      myActionToDrawLocationMap.put(lastStart,
                                    new EventRenderData(lastStartIndex, (long)max - lastStart.getStart()));
      lastStart = null;
      lastStartIndex = 0;
    }
    for (Long key : downEvents.keySet()) {
      EventAction<Action, String> event = downEvents.get(key);
      int offset = drawOrderIndex.get(key);
      Path2D.Float path = new Path2D.Float();
      double normalizedEndPosition = NORMALIZED_END;
      double normalizedstartPosition = ((event.getStart() - min) / (max - min));
      double baseHeight = myMaxHeight - (offset * SEGMENT_SPACING);
      double tailHeight = myMaxHeight - (offset * SEGMENT_SPACING - TAIL_HEIGHT);
      path.moveTo(normalizedEndPosition, baseHeight);
      path.lineTo(normalizedEndPosition, tailHeight);
      path.lineTo(normalizedstartPosition, tailHeight);
      path.lineTo(normalizedstartPosition, baseHeight);
      mPaths.add(path);
    }

  }

  @Override
  protected void draw(Graphics2D g2d) {
    Dimension dim = getSize();
    double scaleFactor = dim.getWidth();
    double min = mData.getRange().getMin();
    double max = mData.getRange().getMax();
    Stroke current = g2d.getStroke();
    BasicStroke str = new BasicStroke(2.0f);
    g2d.setStroke(str);
    AffineTransform scale = AffineTransform.getScaleInstance(scaleFactor, 1);
    for (int i = 0; i < mPaths.size(); i++) {
      double maxX = mPaths.get(i).getBounds2D().getMaxX() * scaleFactor;
      //Small fudge factor
      if (maxX <= scaleFactor - EPSILON) {
        g2d.setColor(DISABLED_ACTION);
      }
      else {
        g2d.setColor(ENABLED_ACTION);
      }
      Shape shape = scale.createTransformedShape(mPaths.get(i));
      g2d.draw(shape);
    }
    g2d.setStroke(current);
    g2d.setFont(AdtUIUtils.DEFAULT_FONT);
    FontMetrics metrics = g2d.getFontMetrics();
    for (EventAction<Action, String> event : myActionToDrawLocationMap.keySet()) {
      EventRenderData positionData = myActionToDrawLocationMap.get(event);
      int offset = positionData.getIndex();
      String text = event.getValueData();
      int width = metrics.stringWidth(text);
      double normalizedStartPosition = (event.getStart() - min) / (max - min);
      double normalizedEndPosition = ((event.getStart() + positionData.getTimestamp()) - min)
                                     / (max - min);
      double startPosition = normalizedStartPosition * scaleFactor;
      double endPosition = normalizedEndPosition * scaleFactor;
      boolean ellipsis = true;
      //TODO: If text was previously ellipsed and it is getting pushed off the screen,
      //we need to ellipse it before doing the sliding animation.
      if (startPosition < 0 && endPosition > 0) {
        startPosition = width < endPosition ? 0 : endPosition - width;
        ellipsis = false;
      }
      // This loop test the length of the word we are trying to draw, if it is to big to fit between the start of this event and the
      // start of the next event we add an ellipsis and remove a character. We do this until the word fits in the space available to draw.
      while (width > endPosition - startPosition && text.length() > ELLIPSIS_LENGTH
             && ellipsis) {
        text = text
          .substring(0, text.length() - (ELLIPSIS_LENGTH + CHARACTERS_TO_SHRINK_BY));
        text += ELLIPSIS;
        width = metrics.stringWidth(text);
      }
      //Small Fudge factor
      if (endPosition <= scaleFactor - EPSILON) {
        g2d.setColor(DISABLED_ACTION);
      }
      else {
        g2d.setColor(ENABLED_ACTION);
      }
      g2d.drawString(text, (float)startPosition, myMaxHeight - (offset * SEGMENT_SPACING));
    }
  }

  @Override
  protected void debugDraw(Graphics2D g) {
    super.debugDraw(g);
  }
}

