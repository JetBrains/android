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

import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.*;
import com.google.common.primitives.Ints;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.*;
import java.util.List;

import static com.android.tools.adtui.common.AdtUiUtils.getFittedString;

/**
 * A chart component that renders lines with a title that have the ability to stack.
 */
public class StackedEventComponent extends AnimatedComponent {

  /**
   * Enum that defines an activity state. Each activity started action, should have an associated
   * activity completed action.
   */
  public enum Action {
    NONE,
    ACTIVITY_STARTED,
    ACTIVITY_COMPLETED,
  }

  private static final Color DISABLED_ACTION = new Color(221, 222, 224);
  private static final Color ENABLED_ACTION = new Color(106, 189, 180);
  private static final int CHARACTERS_TO_SHRINK_BY = 1;
  private static final int SEGMENT_SPACING = 5;
  private static final int NORMALIZED_END = 1;

  @NotNull
  private final RangedSeries<EventAction<Action, String>> mData;

  private final int myMaxHeight;

  private float myLineThickness = 6.0f;

  /**
   * This map is used to pair actions, to their draw location. This is used primarily to store the
   * location where to draw the name of the incoming event.
   */
  private HashMap<EventAction<Action, String>, EventRenderData> myActionToDrawLocationMap = new HashMap<>();
  private List<EventRenderData> myActivities = new ArrayList<>();

  /**
   * @param data The state chart data.
   */
  public StackedEventComponent(@NotNull RangedSeries<EventAction<Action, String>> data, int maxHeight) {
    mData = data;
    myMaxHeight = maxHeight;
    setFont(AdtUiUtils.DEFAULT_FONT);
  }

  @Override
  protected void updateData() {
    double min = mData.getXRange().getMin();
    double max = mData.getXRange().getMax();

    // A map of EventAction started events to their start time, so we can correlate these to
    // EventAction competed events with the EventAction start events. This is done this way as
    // a event started and completed events may come in in any order at any time.
    // TODO: Combine start/stop events, that means pulling this logic out into the supportlib.
    HashMap<Long, EventAction<Action, String>> downEvents = new HashMap<>();

    // A queue of open index values, this allows us to pack our events without leaving gaps.

    myActivities.clear();
    myActionToDrawLocationMap.clear();
    int lastIndex = 0;
    EventAction<Action, String> lastStart = null;
    int lastStartIndex = 0;
    ImmutableList<SeriesData<EventAction<Action, String>>> series = mData.getSeries();
    int size = series.size();

    // Loop through the data series looking at all of the start events, and stop events.
    // For each start event we store off its EventAction until we find an associated stop event.
    // Once we find a stop event we determine the draw order, name, start and stop locations and
    // cache off a path to draw.
    for (int i = 0; i < size; i++) {
      SeriesData<EventAction<Action, String>> seriesData = series.get(i);
      EventAction<Action, String> data = seriesData.value;
      if (data.getValue() == Action.ACTIVITY_STARTED) {
        //TODO: This should be managed by perfa not the profilers.
        downEvents.put(data.getStartUs(), data);
      }
      else if (data.getValue() == Action.ACTIVITY_COMPLETED) {
        // TODO: check/assert that ACTIVITY_COMPLETED event time is greater than or equal to ACTIVITY_STARTED time
        Path2D.Float path = new Path2D.Float();
        // Here we normalize the position to a value between 0 and 1. This allows us to scale the width of the line based on the
        // width of our chart.
        double normalizedEndPosition = ((data.getEndUs() - min) / (max - min));
        double normalizedstartPosition = ((data.getStartUs() - min) / (max - min));
        double baseHeight = myMaxHeight - (SEGMENT_SPACING);
        path.moveTo(normalizedEndPosition, baseHeight);
        path.lineTo(normalizedstartPosition, baseHeight);
        myActivities.add(new EventRenderData(data, path));
        downEvents.remove(data.getStartUs());
      }
    }

    for (Long key : downEvents.keySet()) {
      EventAction<Action, String> event = downEvents.get(key);
      Path2D.Float path = new Path2D.Float();
      double normalizedEndPosition = NORMALIZED_END;
      double normalizedstartPosition = ((event.getStartUs() - min) / (max - min));
      double baseHeight = myMaxHeight - (SEGMENT_SPACING);
      path.moveTo(normalizedEndPosition, baseHeight);
      path.lineTo(normalizedstartPosition, baseHeight);
      myActivities.add(new EventRenderData(event, path));
    }
    myActivities.sort((erd1, erd2) -> {
      if (erd1.getAction().getEndUs() == 0 && erd2.getAction().getEndUs() != 0) {
        return -1;
      }
      else if (erd1.getAction().getEndUs() != 0 && erd2.getAction().getEndUs() == 0) {
        return 1;
      }
      else if (erd1.getAction().getEndUs() != 0 && erd2.getAction().getEndUs() != 0) {
        return Ints.checkedCast(erd1.getAction().getEndUs() - erd2.getAction().getEndUs());
      }
      return Ints.checkedCast(erd1.getAction().getStartUs() - erd2.getAction().getStartUs());
    });
  }

  @Override
  protected void draw(Graphics2D g2d) {
    Dimension dim = getSize();
    int scaleFactor = dim.width;
    double min = mData.getXRange().getMin();
    double max = mData.getXRange().getMax();
    FontMetrics metrics = g2d.getFontMetrics();
    Stroke current = g2d.getStroke();
    BasicStroke str = new BasicStroke(myLineThickness);
    AffineTransform scale = AffineTransform.getScaleInstance(scaleFactor, 1);
    Iterator<EventRenderData> itor = myActivities.iterator();
    g2d.setFont(getFont());

    while (itor.hasNext()) {
      g2d.setStroke(str);
      EventRenderData renderData = itor.next();
      EventAction<Action, String> event = renderData.getAction();
      if (event.getEndUs() != 0) {
        g2d.setColor(DISABLED_ACTION);
      }
      else {
        g2d.setColor(ENABLED_ACTION);
      }
      Shape shape = scale.createTransformedShape(renderData.getPath());
      g2d.draw(shape);

      g2d.setStroke(current);
      String text = event.getValueData();
      int width = metrics.stringWidth(text);
      double normalizedStartPosition = (event.getStartUs() - min) / (max - min);
      double lifetime = event.getEndUs() - event.getStartUs();
      if (event.getEndUs() == 0) {
        lifetime = max - event.getStartUs();
      }
      double normalizedEndPosition = ((event.getStartUs() + (lifetime)) - min)
                                     / (max - min);
      float startPosition = (float)normalizedStartPosition * scaleFactor;
      float endPosition = (float)normalizedEndPosition * scaleFactor;
      boolean ellipsis = true;
      //TODO: If text was previously ellipsed and it is getting pushed off the screen,
      //we need to ellipse it before doing the sliding animation.
      if (startPosition < 0 && endPosition > 0) {
        startPosition = width < endPosition ? 0 : endPosition - width;
        ellipsis = false;
      }

      if (ellipsis) {
        text = getFittedString(metrics, text, endPosition - startPosition, CHARACTERS_TO_SHRINK_BY);
        if (text.isEmpty()) {
          continue;
        }
      }
      if (event.getEndUs() != 0) {
        g2d.setColor(DISABLED_ACTION);
      }
      else {
        g2d.setColor(ENABLED_ACTION);
      }
      g2d.drawString(text, startPosition, myMaxHeight - (myLineThickness + SEGMENT_SPACING));
    }
  }

  @Override
  protected void debugDraw(Graphics2D g) {
    super.debugDraw(g);
  }

  public void setLineThickness(float lineThickness) {
    myLineThickness = lineThickness;
  }

  private static class EventRenderData {

    private final EventAction<Action, String> mAction;
    private final Path2D mPath;

    public EventAction<Action, String> getAction() {
      return mAction;
    }

    public Path2D getPath() {
      return mPath;
    }

    public EventRenderData(EventAction<Action, String> action, Path2D path) {
      mAction = action;
      mPath = path;
    }
  }
}

