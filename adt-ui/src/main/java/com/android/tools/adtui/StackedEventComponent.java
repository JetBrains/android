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
import com.android.tools.adtui.model.EventAction;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.SeriesData;
import com.google.common.primitives.Ints;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import static com.android.tools.adtui.common.AdtUiUtils.getFittedString;

/**
 * A chart component that renders lines with a title that have the ability to stack.
 */
public class StackedEventComponent extends AnimatedComponent {

  private static final Color DISABLED_ACTION = new Color(221, 222, 224);
  private static final Color ENABLED_ACTION = new Color(106, 189, 180);
  private static final int CHARACTERS_TO_SHRINK_BY = 1;
  private static final int SEGMENT_SPACING = 5;

  @NotNull
  private final RangedSeries<EventAction<EventAction.ActivityAction, String>> mData;

  private float myLineThickness = 6.0f;

  /**
   * This map is used to pair actions, to their draw location. This is used primarily to store the
   * location where to draw the name of the incoming event.
   */
  private HashMap<EventAction<EventAction.ActivityAction, String>, EventRenderData> myActionToDrawLocationMap = new HashMap<>();
  private List<EventRenderData> myActivities = new ArrayList<>();

  /**
   * @param data The state chart data.
   */
  public StackedEventComponent(@NotNull RangedSeries<EventAction<EventAction.ActivityAction, String>> data) {
    mData = data;
    setFont(AdtUiUtils.DEFAULT_FONT);
  }

  @Override
  protected void updateData() {
    double min = mData.getXRange().getMin();
    double max = mData.getXRange().getMax();

    // A map of EventAction started events to their start time, so we can correlate these to
    // EventAction competed events with the EventAction start events. This is done this way as
    // a event started and completed events may come in in any order at any time.

    // A queue of open index values, this allows us to pack our events without leaving gaps.

    myActivities.clear();
    myActionToDrawLocationMap.clear();
    ImmutableList<SeriesData<EventAction<EventAction.ActivityAction, String>>> series = mData.getSeries();
    int size = series.size();

    // Loop through the data series looking at all of the start events, and stop events.
    // For each start event we store off its EventAction until we find an associated stop event.
    // Once we find a stop event we determine the draw order, name, start and stop locations and
    // cache off a path to draw.
    for (int i = 0; i < size; i++) {
      SeriesData<EventAction<EventAction.ActivityAction, String>> seriesData = series.get(i);
      EventAction<EventAction.ActivityAction, String> data = seriesData.value;
      Path2D.Float path = new Path2D.Float();
      // Here we normalize the position to a value between 0 and 1. This allows us to scale the width of the line based on the
      // width of our chart.
      double endTime = data.getEndUs() == 0 ? max : data.getEndUs();
      double normalizedEndPosition = ((endTime - min) / (max - min));
      double normalizedstartPosition = ((data.getStartUs() - min) / (max - min));
      path.moveTo(normalizedEndPosition, 1);
      path.lineTo(normalizedstartPosition, 1);
      myActivities.add(new EventRenderData(data, path));
    }

    myActivities.sort((erd1, erd2) -> {
      if (erd1.getAction().getEndUs() == 0 && erd2.getAction().getEndUs() != 0) {
        return -1;
      }
      else if (erd1.getAction().getEndUs() != 0 && erd2.getAction().getEndUs() == 0) {
        return 1;
      }
      else if (erd1.getAction().getEndUs() != 0 && erd2.getAction().getEndUs() != 0) {
        return erd1.getAction().getEndUs() - erd2.getAction().getEndUs() >= 0 ? 1 : -1;
      }
      return erd1.getAction().getStartUs() - erd2.getAction().getStartUs() >= 0 ? 1 : -1;
    });
  }

  @Override
  protected void draw(Graphics2D g2d, Dimension dim) {
    int scaleFactor = dim.width;
    double min = mData.getXRange().getMin();
    double max = mData.getXRange().getMax();
    FontMetrics metrics = g2d.getFontMetrics();
    Stroke current = g2d.getStroke();
    BasicStroke str = new BasicStroke(myLineThickness);
    AffineTransform scale = AffineTransform.getScaleInstance(scaleFactor, dim.height - SEGMENT_SPACING);
    Iterator<EventRenderData> itor = myActivities.iterator();
    g2d.setFont(getFont());

    while (itor.hasNext()) {
      g2d.setStroke(str);
      EventRenderData renderData = itor.next();
      EventAction<EventAction.ActivityAction, String> event = renderData.getAction();
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
      int height = metrics.getHeight();
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
      g2d.drawString(text, startPosition, (myLineThickness + SEGMENT_SPACING));
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

    private final EventAction<EventAction.ActivityAction, String> mAction;
    private final Path2D mPath;

    public EventAction<EventAction.ActivityAction, String> getAction() {
      return mAction;
    }

    public Path2D getPath() {
      return mPath;
    }

    public EventRenderData(EventAction<EventAction.ActivityAction, String> action, Path2D path) {
      mAction = action;
      mPath = path;
    }
  }
}

