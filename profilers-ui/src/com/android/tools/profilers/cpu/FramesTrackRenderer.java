/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.chart.statechart.StateChart;
import com.android.tools.adtui.chart.statechart.StateChartColorProvider;
import com.android.tools.adtui.chart.statechart.StateChartTextConverter;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.adtui.model.trackgroup.TrackModel;
import com.android.tools.adtui.trackgroup.TrackRenderer;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerTrackRendererType;
import com.android.tools.profilers.cpu.atrace.AtraceFrame;
import java.awt.Color;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Track renderer for Atrace frame rendering data.
 */
public class FramesTrackRenderer implements TrackRenderer<CpuFramesModel.FrameState, ProfilerTrackRendererType> {
  @NotNull
  @Override
  public JComponent render(@NotNull TrackModel<CpuFramesModel.FrameState, ProfilerTrackRendererType> trackModel) {
    StateChart<AtraceFrame> stateChart = new StateChart<>(
      trackModel.getDataModel().getModel(), new FrameColorProvider(), new FrameTextConverter());
    stateChart.setRenderMode(StateChart.RenderMode.TEXT);
    return stateChart;
  }

  private static class FrameColorProvider extends StateChartColorProvider<AtraceFrame> {
    @NotNull
    @Override
    public Color getColor(boolean isMouseOver, @NotNull AtraceFrame value) {
      switch (value.getTotalPerfClass()) {
        case BAD:
          return isMouseOver ? ProfilerColors.SLOW_FRAME_COLOR_HIGHLIGHTED : ProfilerColors.SLOW_FRAME_COLOR;
        case GOOD:
          return isMouseOver ? ProfilerColors.NORMAL_FRAME_COLOR_HIGHLIGHTED : ProfilerColors.NORMAL_FRAME_COLOR;
        default:
          return ProfilerColors.DEFAULT_STAGE_BACKGROUND;
      }
    }
  }

  private static class FrameTextConverter implements StateChartTextConverter<AtraceFrame> {
    @NotNull
    @Override
    public String convertToString(@NotNull AtraceFrame value) {
      // Show timing on bad frames.
      if (value.getTotalPerfClass() == AtraceFrame.PerfClass.BAD) {
        return TimeFormatter.getSingleUnitDurationString(value.getDurationUs());
      }
      return "";
    }
  }
}
