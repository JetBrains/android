/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.energy;

import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.event.EventAction;
import com.android.tools.adtui.model.event.EventModel;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEvent;
import com.android.tools.profilers.ProfilerColors;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

public class EnergyEventComponent extends AnimatedComponent {
  private static final int HIGHLIGHT_WIDTH = 2;
  /**
   * Cache the result of filtering events from a single duration that match the range.
   */
  @NotNull private final List<EventAction<EnergyEvent>> myActionToDrawList;
  @NotNull private final EventModel<EnergyEvent> myModel;
  @NotNull private final Color myHighlightColor;
  @NotNull private final Color myDurationColor;
  private boolean myRender;

  EnergyEventComponent(@NotNull EventModel<EnergyEvent> model, @NotNull Color highlightColor) {
    myModel = model;
    myHighlightColor = highlightColor;
    // Use ProfilerColors.DEFAULT_BACKGROUND as the component's background is null.
    myDurationColor = AdtUiUtils.overlayColor(ProfilerColors.DEFAULT_BACKGROUND.getRGB(), myHighlightColor.getRGB(), 0.25f);
    myActionToDrawList = new ArrayList<>();
    myRender = true;
    myModel.addDependency(myAspectObserver).onChange(EventModel.Aspect.EVENT, this::modelChanged);
  }

  private void modelChanged() {
    myRender = true;
    opaqueRepaint();
  }

  /**
   * Filters the events in data series to match the range.
   */
  private void renderToAction() {
    myActionToDrawList.clear();
    double minUs = myModel.getRangedSeries().getXRange().getMin();
    double maxUs = myModel.getRangedSeries().getXRange().getMax();
    for (SeriesData<EventAction<EnergyEvent>> data : myModel.getRangedSeries().getSeries()) {
      // If the condition is false, that means either start time is larger than the range's max and the event is not shown;
      // or the end time is smaller than the range's min and the event is not shown.
      // The conditions two parts will not happen at the same time ever, because startTimeUs is always earlier than endTimeUs.
      if (data.value.getStartUs() <= maxUs && data.value.getEndUs() >= minUs) {
        myActionToDrawList.add(data.value);
      }
    }
  }

  @Override
  protected void draw(Graphics2D g, Dimension dim) {
    if (myRender) {
      renderToAction();
      myRender = false;
    }

    Color currentColor = g.getColor();
    Stroke currentStroke = g.getStroke();
    double minUs = myModel.getRangedSeries().getXRange().getMin();
    double maxUs = myModel.getRangedSeries().getXRange().getMax();
    double scaleFactor = dim.getWidth();
    double height = dim.getHeight() - 2;

    for (EventAction<EnergyEvent> action : myActionToDrawList) {
      // Starts from the maximum of the range's min and the start time.
      double normalizedPositionStart = ((Math.max(minUs, action.getStartUs()) - minUs) / (maxUs - minUs));
      AffineTransform transform = AffineTransform
        .getTranslateInstance(normalizedPositionStart * scaleFactor, 0);
      double xPosition = transform.getTranslateX() - 1;
      double yPosition = transform.getTranslateY() + 1;

      // Draw the event highlight only when the event timestamp is after the range's min.
      if (action.getStartUs() >= minUs) {
        g.setColor(myHighlightColor);
        g.fill(new Rectangle2D.Double(xPosition, yPosition, HIGHLIGHT_WIDTH, height));
        xPosition += HIGHLIGHT_WIDTH;
      }

      // Draw the event bar only when the event is not terminal.
      if (!action.getType().getIsTerminal()) {
        // Ends at the minimum of the range's max and the end time.
        double normalizedPositionEnd = ((Math.min(maxUs, action.getEndUs()) - minUs) / (maxUs - minUs));
        double length = (normalizedPositionEnd - normalizedPositionStart) * scaleFactor;
        g.setColor(myDurationColor);
        g.fill(new Rectangle2D.Double(xPosition, yPosition, length, height));
      }
    }
    g.setColor(currentColor);
    g.setStroke(currentStroke);
  }
}
