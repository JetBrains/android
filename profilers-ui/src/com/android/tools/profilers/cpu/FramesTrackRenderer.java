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
import com.android.tools.adtui.common.DataVisualizationColors;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.adtui.model.trackgroup.TrackModel;
import com.android.tools.adtui.trackgroup.TrackRenderer;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerTrackRendererType;
import com.android.tools.profilers.cpu.systemtrace.CpuFramesModel;
import com.android.tools.profilers.cpu.systemtrace.SystemTraceFrame;
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
    StateChart<SystemTraceFrame> stateChart = new StateChart<>(
      trackModel.getDataModel().getModel(), new FrameColorProvider(), new FrameTextConverter());
    stateChart.setRenderMode(StateChart.RenderMode.TEXT);
    return stateChart;
  }

  private static class FrameColorProvider extends StateChartColorProvider<SystemTraceFrame> {
    @NotNull
    @Override
    public Color getColor(boolean isMouseOver, @NotNull SystemTraceFrame value) {
      switch (value.getTotalPerfClass()) {
        case BAD:
          return isMouseOver ? ProfilerColors.SLOW_FRAME_COLOR_HIGHLIGHTED : ProfilerColors.SLOW_FRAME_COLOR;
        case GOOD:
          return DataVisualizationColors.INSTANCE.getColor(DataVisualizationColors.BACKGROUND_DATA_COLOR, isMouseOver);
        default:
          return ProfilerColors.CPU_STATECHART_DEFAULT_STATE;
      }
    }
  }

  private static class FrameTextConverter implements StateChartTextConverter<SystemTraceFrame> {
    @NotNull
    @Override
    public String convertToString(@NotNull SystemTraceFrame value) {
      // Show timing on bad frames.
      if (value.getTotalPerfClass() == SystemTraceFrame.PerfClass.BAD) {
        return TimeFormatter.getSingleUnitDurationString(value.getDurationUs());
      }
      return "";
    }
  }
}
