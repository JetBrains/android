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
package com.android.tools.idea.monitor.ui.energy.view;

import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.common.formatter.EnergyAxisFormatter;
import com.android.tools.adtui.model.Range;
import com.android.tools.datastore.SeriesDataStore;
import com.android.tools.datastore.SeriesDataType;
import com.android.tools.idea.monitor.tool.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.BaseLineChartSegment;
import com.android.tools.idea.monitor.ui.BaseProfilerUiManager;
import com.intellij.ui.JBColor;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class EnergySegment extends BaseLineChartSegment {
  private static final String SEGMENT_NAME = "Energy";

  private static final Color ENERGY_TOTAL_COLOR = new JBColor(new Color(200, 200, 200), new Color(200, 200, 200));
  private static final Color ENERGY_SCREEN_COLOR = new JBColor(new Color(200, 200, 100), new Color(200, 200, 100));
  private static final Color ENERGY_CPU_SYSTEM_COLOR = new JBColor(new Color(200, 50, 50), new Color(200, 50, 50));
  private static final Color ENERGY_CPU_USER_COLOR = new JBColor(new Color(250, 50, 50), new Color(250, 50, 50));
  private static final Color ENERGY_SENSORS_COLOR = new JBColor(new Color(200, 100, 0), new Color(200, 100, 0));
  private static final Color ENERGY_CELL_NETWORK_COLOR = new JBColor(new Color(100, 200, 150), new Color(100, 200, 150));
  private static final Color ENERGY_WIFI_NETWORK_COLOR = new JBColor(new Color(100, 150, 200), new Color(100, 150, 200));

  public EnergySegment(@NotNull Range timeCurrentRangeUs,
                       @NotNull SeriesDataStore dataStore,
                       @NotNull EventDispatcher<ProfilerEventListener> dispatcher) {
    super(SEGMENT_NAME, timeCurrentRangeUs, dataStore, EnergyAxisFormatter.SIMPLE, EnergyAxisFormatter.DETAILED, null, dispatcher);
  }

  @Override
  public BaseProfilerUiManager.ProfilerType getProfilerType() {
    return BaseProfilerUiManager.ProfilerType.ENERGY;
  }

  @Override
  protected void updateChartLines(boolean isExpanded) {

    if (isExpanded) {
      addEnergyLevelLine(SeriesDataType.ENERGY_SCREEN, ENERGY_SCREEN_COLOR);
      addEnergyLevelLine(SeriesDataType.ENERGY_CPU_SYSTEM, ENERGY_CPU_SYSTEM_COLOR);
      addEnergyLevelLine(SeriesDataType.ENERGY_CPU_USER, ENERGY_CPU_USER_COLOR);
      addEnergyLevelLine(SeriesDataType.ENERGY_CELL_NETWORK, ENERGY_CELL_NETWORK_COLOR);
      addEnergyLevelLine(SeriesDataType.ENERGY_WIFI_NETWORK, ENERGY_WIFI_NETWORK_COLOR);

      // This is on it's own because we don't want to stack it together with the rest.
      addLeftAxisLine(SeriesDataType.ENERGY_TOTAL, SeriesDataType.ENERGY_TOTAL.toString(), new LineConfig(ENERGY_TOTAL_COLOR));
    }
    else {
      addEnergyLevelLine(SeriesDataType.ENERGY_TOTAL, ENERGY_TOTAL_COLOR);
    }
  }

  private void addEnergyLevelLine(SeriesDataType type, Color color) {
    addLeftAxisLine(type, type.toString(), new LineConfig(color).setFilled(true).setStacked(true));
  }
}