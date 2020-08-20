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
import com.android.tools.adtui.common.DataVisualizationColors;
import com.android.tools.adtui.model.trackgroup.TrackModel;
import com.android.tools.adtui.trackgroup.TrackRenderer;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerTrackRendererType;
import com.android.tools.profilers.cpu.systemtrace.SurfaceflingerEvent;
import com.android.tools.profilers.cpu.systemtrace.SurfaceflingerTrackModel;
import java.awt.Color;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Track renderer for SysTrace Surfaceflinger events.
 */
public class SurfaceflingerTrackRenderer implements TrackRenderer<SurfaceflingerTrackModel, ProfilerTrackRendererType> {
  @NotNull
  @Override
  public JComponent render(@NotNull TrackModel<SurfaceflingerTrackModel, ProfilerTrackRendererType> trackModel) {
    return new StateChart<>(trackModel.getDataModel(), new SurfaceflingerColorProvider());
  }

  private static class SurfaceflingerColorProvider extends StateChartColorProvider<SurfaceflingerEvent> {
    @NotNull
    @Override
    public Color getColor(boolean isMouseOver, @NotNull SurfaceflingerEvent value) {
      switch (value.getType()) {
        case PROCESSING:
          return DataVisualizationColors.INSTANCE.getColor(DataVisualizationColors.BACKGROUND_DATA_COLOR, isMouseOver);
        case IDLE: // fallthrough
        default:
          return ProfilerColors.CPU_STATECHART_DEFAULT_STATE;
      }
    }
  }
}
