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
import com.android.tools.adtui.model.trackgroup.TrackModel;
import com.android.tools.adtui.trackgroup.TrackRenderer;
import com.android.tools.profilers.DataVisualizationColors;
import com.android.tools.profilers.cpu.systemtrace.SurfaceflingerEvent;
import com.android.tools.profilers.cpu.systemtrace.SurfaceflingerTrackModel;
import com.intellij.util.ui.UIUtil;
import java.awt.Color;
import java.util.function.BooleanSupplier;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Track renderer for SysTrace Surfaceflinger events.
 */
public class SurfaceflingerTrackRenderer implements TrackRenderer<SurfaceflingerTrackModel> {
  private final BooleanSupplier myVsyncEnabler;

  public SurfaceflingerTrackRenderer(BooleanSupplier vsyncEnabler) {
    myVsyncEnabler = vsyncEnabler;
  }

  @NotNull
  @Override
  public JComponent render(@NotNull TrackModel<SurfaceflingerTrackModel, ?> trackModel) {
    return VsyncPanel.of(new StateChart<>(trackModel.getDataModel(), new SurfaceflingerColorProvider()),
                         trackModel.getDataModel().getViewRange(),
                         trackModel.getDataModel().getSystemTraceData().getVsyncCounterValues(),
                         myVsyncEnabler);
  }

  private static class SurfaceflingerColorProvider extends StateChartColorProvider<SurfaceflingerEvent> {
    @NotNull
    @Override
    public Color getColor(boolean isMouseOver, @NotNull SurfaceflingerEvent value) {
      switch (value.getType()) {
        case PROCESSING:
          return DataVisualizationColors.getPaletteManager().getBackgroundColor(
            DataVisualizationColors.BACKGROUND_DATA_COLOR_NAME, isMouseOver);
        case IDLE: // fallthrough
        default:
          return UIUtil.TRANSPARENT_COLOR;
      }
    }
  }
}
