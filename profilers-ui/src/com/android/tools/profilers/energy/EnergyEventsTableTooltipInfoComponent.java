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
import com.android.tools.adtui.instructions.InstructionsRenderer;
import com.android.tools.adtui.instructions.NewRowInstruction;
import com.android.tools.adtui.instructions.RenderInstruction;
import com.android.tools.adtui.instructions.TextInstruction;
import com.android.tools.profiler.proto.EnergyProfiler;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class EnergyEventsTableTooltipInfoComponent extends AnimatedComponent {
  private final int VERTICAL_MARGIN_PX = JBUI.scale(8);
  private final int VERTICAL_PADDING_PX = VERTICAL_MARGIN_PX;
  private final int BOTTOM_RIGHT_EXTRA_MARGIN_PX = JBUI.scale(4);
  private final int HORIZONTAL_PADDING_PX = JBUI.scale(10);
  private final int MIN_TOOLTIP_WIDTH = JBUI.scale(112);

  private final EnergyEventsTableTooltipInfoModel myModel;

  @NotNull
  private final List<RenderInstruction> myInstructions = new ArrayList<>();

  public EnergyEventsTableTooltipInfoComponent(@NotNull EnergyEventsTableTooltipInfoModel model) {
    myModel = model;
    myModel.addDependency(myAspectObserver).onChange(EnergyEventsTableTooltipInfoModel.Aspect.EVENT, this::eventChanged);
  }

  private void eventChanged() {
    myInstructions.clear();
    if (myModel.getDuration() == null) {
      return;
    }
    switch (myModel.getDuration().getKind()) {
      case WAKE_LOCK:
      case JOB:
        renderJobAndWakeLockEvent();
        break;
      case ALARM:
        renderAlarmEvent();
        break;
      case LOCATION:
        renderLocationEvent();
        break;
      default:
        break;
    }
    // Display error message when no tooltip data is available.
    if (myInstructions.isEmpty()) {
      myInstructions.add(new TextInstruction(getFont(), "No data."));
    }
    // Remove the extra last line.
    if (myInstructions.get(myInstructions.size() - 1) instanceof NewRowInstruction) {
      myInstructions.remove(myInstructions.size() - 1);
    }
  }

  private void renderText(@Nullable String string) {
    if (string != null) {
      myInstructions.add(new TextInstruction(getFont(), string));
      myInstructions.add(new NewRowInstruction(VERTICAL_MARGIN_PX));
    }
  }

  private void renderNameValuePair(@NotNull String name, @NotNull String value) {
    myInstructions.add(new TextInstruction(getFont().deriveFont(Font.BOLD), name + ": "));
    myInstructions.add(new TextInstruction(getFont(), value));
    myInstructions.add(new NewRowInstruction(VERTICAL_MARGIN_PX));
  }

  private void renderJobAndWakeLockEvent() {
    renderText(myModel.getStatusString());
    renderText(myModel.getRangeString());
    renderText(myModel.getDurationString());
  }

  private void renderAlarmEvent() {
    if (myModel.getDuration() == null) {
      return;
    }
    EnergyProfiler.EnergyEvent firstEvent = myModel.getDuration().getEventList().get(0);
    if (firstEvent.hasAlarmSet()) {
      if (firstEvent.getAlarmSet().hasOperation()) {
        myInstructions.add(new TextInstruction(getFont().deriveFont(Font.BOLD), "Intent: "));
        String packageString = firstEvent.getAlarmSet().getOperation().getCreatorPackage();
        packageString = packageString.substring(packageString.lastIndexOf('.') + 1);
        int uid = firstEvent.getAlarmSet().getOperation().getCreatorUid();
        myInstructions.add(new TextInstruction(getFont(), packageString));
        myInstructions.add(new TextInstruction(getFont().deriveFont(Font.ITALIC), " (" + uid + ")"));
        myInstructions.add(new NewRowInstruction(VERTICAL_MARGIN_PX));
      }

      long triggerTimeUs = TimeUnit.NANOSECONDS.toMicros(firstEvent.getTimestamp());
      long triggerTimeMs = TimeUnit.NANOSECONDS.toMillis(firstEvent.getTimestamp());
      myInstructions.add(new TextInstruction(getFont().deriveFont(Font.BOLD), "Created: "));
      myInstructions.add(new TextInstruction(getFont(), myModel.getDateFormattedString(triggerTimeMs)));
      myInstructions.add(new TextInstruction(getFont().deriveFont(Font.ITALIC), " (" + myModel.getFormattedString(triggerTimeUs) + ")"));
      myInstructions.add(new NewRowInstruction(VERTICAL_MARGIN_PX));

      long frequency = TimeUnit.MILLISECONDS.toMicros(firstEvent.getAlarmSet().getIntervalMs());
      if (frequency != 0) {
        String frequencyString = myModel.getFormattedDuration(frequency);
        renderNameValuePair("Repeats", "Every " + frequencyString);
      }
    }
  }

  private void renderLocationEvent() {
    if (myModel.getDuration() == null) {
      return;
    }
    EnergyProfiler.EnergyEvent firstEvent = myModel.getDuration().getEventList().get(0);
    if (firstEvent.hasLocationUpdateRequested() && firstEvent.getLocationUpdateRequested().hasRequest()) {
      renderNameValuePair("Priority", firstEvent.getLocationUpdateRequested().getRequest().getPriority().toString());
      long frequency = TimeUnit.MILLISECONDS.toMicros(firstEvent.getLocationUpdateRequested().getRequest().getIntervalMs());
      if (frequency != 0) {
        String frequencyString = myModel.getFormattedDuration(frequency);
        renderNameValuePair("Frequency", "Every " + frequencyString);
      }
    }
  }

  @Override
  public Dimension getPreferredSize() {
    InstructionsRenderer state = new InstructionsRenderer(myInstructions, InstructionsRenderer.HorizontalAlignment.LEFT);
    Dimension renderSize = state.getRenderSize();
    return new Dimension(Math.max(MIN_TOOLTIP_WIDTH, renderSize.width + 2 * HORIZONTAL_PADDING_PX + BOTTOM_RIGHT_EXTRA_MARGIN_PX),
                         renderSize.height + 2 * VERTICAL_MARGIN_PX + BOTTOM_RIGHT_EXTRA_MARGIN_PX);
  }

  @Override
  public Dimension getMinimumSize() {
    return this.getPreferredSize();
  }

  @Override
  protected void draw(Graphics2D g2d, Dimension dim) {
    g2d.setColor(getBackground());
    g2d.fillRect(0, 0, (int)dim.getWidth(), (int)dim.getHeight());
    g2d.setColor(getForeground());
    g2d.translate(HORIZONTAL_PADDING_PX, VERTICAL_PADDING_PX);
    InstructionsRenderer state = new InstructionsRenderer(myInstructions, InstructionsRenderer.HorizontalAlignment.LEFT);
    state.draw(this, g2d);
    g2d.translate(-HORIZONTAL_PADDING_PX, -VERTICAL_PADDING_PX);
  }
}
