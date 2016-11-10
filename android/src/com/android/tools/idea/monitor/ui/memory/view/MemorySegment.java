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

import com.android.tools.adtui.chart.linechart.EventConfig;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.common.formatter.BaseAxisFormatter;
import com.android.tools.adtui.common.formatter.MemoryAxisFormatter;
import com.android.tools.adtui.common.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.LegendRenderData;
import com.android.tools.adtui.model.Range;
import com.android.tools.datastore.SeriesDataStore;
import com.android.tools.datastore.SeriesDataType;
import com.android.tools.idea.monitor.ui.BaseLineChartSegment;
import com.android.tools.idea.monitor.ui.BaseProfilerUiManager;
import com.android.tools.idea.monitor.tool.ProfilerEventListener;
import com.intellij.ui.JBColor;
import com.intellij.util.EventDispatcher;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class MemorySegment extends BaseLineChartSegment {

  private static final String SEGMENT_NAME = "Memory";

  private static final BaseAxisFormatter MEMORY_AXIS_FORMATTER_L1 = new MemoryAxisFormatter(1, 5, 5); // Do not show minor ticks in L1.

  private static final BaseAxisFormatter COUNT_AXIS_FORMATTER = new SingleUnitAxisFormatter(1, 10, 1, "");

  private static final Color MEMORY_TOTAL_COLOR = new JBColor(new Color(123, 170, 214), new Color(123, 170, 214));

  private static final Color MEMORY_NATIVE_COLOR = new JBColor(new Color(132, 209, 199), new Color(132, 209, 199));

  private static final Color MEMORY_GRAPHICS_COLOR = new JBColor(new Color(219, 191, 141), new Color(219, 191, 141));

  private static final Color MEMORY_CODE_COLOR = new JBColor(new Color(125, 206, 132), new Color(125, 206, 132));

  private static final Color MEMORY_OTHER_COLOR = new JBColor(new Color(78, 147, 187), new Color(78, 147, 187));

  private static final Color MEMORY_OBJECT_COUNT_COLOR = new JBColor(new Color(160, 160, 31), new Color(160, 160, 31));

  private static final Color MEMORY_HEAP_DUMP_COLOR = JBColor.DARK_GRAY;

  private static final String JAVA_OBJECTS_COUNT = "Objects";

  private static final String TOTAL_MEM_USAGE = "Total";

  private static final String UNCLASSIFIED_MEM = "Other";

  private static final String CODE_MEM = "Code";

  private static final String GRAPHICS_MEM = "Graphics";

  private static final String NATIVE_MEM = "Native";

  private static final String JAVA_MEM = "Java";

  public MemorySegment(@NotNull Range timeCurrentRangeUs,
                       @NotNull SeriesDataStore dataStore,
                       @NotNull EventDispatcher<ProfilerEventListener> dispatcher) {
    super(SEGMENT_NAME, timeCurrentRangeUs, dataStore, MEMORY_AXIS_FORMATTER_L1, MemoryAxisFormatter.DEFAULT, COUNT_AXIS_FORMATTER, dispatcher);
  }

  @Override
  public BaseProfilerUiManager.ProfilerType getProfilerType() {
    return BaseProfilerUiManager.ProfilerType.MEMORY;
  }

  @Override
  protected void updateChartLines(boolean isExpanded) {
    if (isExpanded) {
      // Left axis series
      // TODO Computing the max value for the stacked memory levels as we need to add up all the categories at each sample point to find
      // the overall max. MEMORY_TOTAL already encapsulates that information and is readily available, so simply include that in the
      // Level 2/3 views as well.
      addLeftAxisLine(
        SeriesDataType.MEMORY_TOTAL, TOTAL_MEM_USAGE, new LineConfig(MEMORY_TOTAL_COLOR).setLegendIconType(LegendRenderData.IconType.NONE));
      addMemoryLevelLine(SeriesDataType.MEMORY_JAVA, JAVA_MEM, MEMORY_TOTAL_COLOR);
      addMemoryLevelLine(SeriesDataType.MEMORY_NATIVE, NATIVE_MEM, MEMORY_NATIVE_COLOR);
      addMemoryLevelLine(SeriesDataType.MEMORY_GRAPHICS, GRAPHICS_MEM, MEMORY_GRAPHICS_COLOR);
      addMemoryLevelLine(SeriesDataType.MEMORY_CODE, CODE_MEM, MEMORY_CODE_COLOR);
      addMemoryLevelLine(SeriesDataType.MEMORY_OTHERS, UNCLASSIFIED_MEM, MEMORY_OTHER_COLOR);

      // Right axis series
      addRightAxisLine(SeriesDataType.MEMORY_OBJECT_COUNT,
                       JAVA_OBJECTS_COUNT,
                       new LineConfig(MEMORY_OBJECT_COUNT_COLOR));

      // Add the heap dump event series
      addEvent(SeriesDataType.MEMORY_HEAPDUMP_EVENT,
               new EventConfig(MEMORY_HEAP_DUMP_COLOR).setIcon(AndroidIcons.Ddms.DumpHprof).setBlocking(true));
    }
    else {
      addMemoryLevelLine(SeriesDataType.MEMORY_TOTAL, TOTAL_MEM_USAGE, MEMORY_TOTAL_COLOR);
    }
  }

  private void addMemoryLevelLine(SeriesDataType type, String title, Color color) {
    addLeftAxisLine(type, title, new LineConfig(color).setFilled(true).setStacked(true));
  }
}
