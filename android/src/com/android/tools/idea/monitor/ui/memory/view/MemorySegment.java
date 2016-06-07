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
package com.android.tools.idea.monitor.ui.memory.view;

import com.android.tools.adtui.Range;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.common.formatter.BaseAxisFormatter;
import com.android.tools.adtui.common.formatter.MemoryAxisFormatter;
import com.android.tools.adtui.common.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.LegendRenderData;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.datastore.SeriesDataType;
import com.android.tools.idea.monitor.ui.BaseLineChartSegment;
import com.android.tools.idea.monitor.ui.ProfilerEventListener;
import com.intellij.ui.JBColor;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class MemorySegment extends BaseLineChartSegment {

  private static final String SEGMENT_NAME = "Memory";

  private static final BaseAxisFormatter MEMORY_AXIS_FORMATTER = MemoryAxisFormatter.DEFAULT;

  private static final BaseAxisFormatter COUNT_AXIS_FORMATTER = new SingleUnitAxisFormatter(1, 10, 1, "");

  private static final Color MEMORY_TOTAL_COLOR = new JBColor(new Color(123, 170, 214), new Color(123, 170, 214));

  private static final Color MEMORY_NATIVE_COLOR = new JBColor(new Color(132, 209, 199), new Color(132, 209, 199));

  private static final Color MEMORY_GRAPHICS_COLOR = new JBColor(new Color(219, 191, 141), new Color(219, 191, 141));

  private static final Color MEMORY_CODE_COLOR = new JBColor(new Color(125, 206, 132), new Color(125, 206, 132));

  private static final Color MEMORY_OTHER_COLOR = new JBColor(new Color(78, 147, 187), new Color(78, 147, 187));

  private static final Color MEMORY_COUNT_COLOR = new JBColor(new Color(70, 120, 31), new Color(70, 120, 31));

  public MemorySegment(@NotNull Range timeRange,
                       @NotNull SeriesDataStore dataStore,
                       @NotNull EventDispatcher<ProfilerEventListener> dispatcher) {
    super(SEGMENT_NAME, timeRange, dataStore, MEMORY_AXIS_FORMATTER, COUNT_AXIS_FORMATTER, dispatcher);
  }

  @Override
  public SegmentType getSegmentType() {
    return SegmentType.MEMORY;
  }

  /**
   * Toggle between L1/L2 view. The previous data will be removed from the LineChart and LegendComponent and be replaced
   * by data series associated with the correct view.
   */
  @Override
  public void toggleView(boolean isExpanded) {
    super.toggleView(isExpanded);

    if (isExpanded) {
      addMemoryLevelLine(SeriesDataType.MEMORY_JAVA, MEMORY_TOTAL_COLOR);
      addMemoryLevelLine(SeriesDataType.MEMORY_NATIVE, MEMORY_NATIVE_COLOR);
      addMemoryLevelLine(SeriesDataType.MEMORY_GRAPHICS, MEMORY_GRAPHICS_COLOR);
      addMemoryLevelLine(SeriesDataType.MEMORY_CODE, MEMORY_CODE_COLOR);
      addMemoryLevelLine(SeriesDataType.MEMORY_OTHERS, MEMORY_OTHER_COLOR);

      addLine(SeriesDataType.MEMORY_OBJECT_COUNT, SeriesDataType.MEMORY_OBJECT_COUNT.toString(), new LineConfig(MEMORY_COUNT_COLOR),
              mRightAxisRange);
    } else {
      addMemoryLevelLine(SeriesDataType.MEMORY_TOTAL, MEMORY_TOTAL_COLOR);
    }

    updateLegend();
  }

  private void addMemoryLevelLine(SeriesDataType type, Color color) {
    addLine(type, type.toString(), new LineConfig(color).setFilled(true).setStacked(true), mLeftAxisRange);
  }

  /**
   * Updates the LegendComponent based on the data series currently being rendered in the LineChart.
   */
  private void updateLegend() {
    List<LegendRenderData> legendRenderDataList = new ArrayList<>();
    for (RangedContinuousSeries series : mLineChart.getRangedContinuousSeries()) {
      Color color = mLineChart.getLineConfig(series).getColor();
      LegendRenderData renderData = new LegendRenderData(LegendRenderData.IconType.LINE, color, series);
      legendRenderDataList.add(renderData);
    }
    mLegendComponent.setLegendData(legendRenderDataList);
  }
}