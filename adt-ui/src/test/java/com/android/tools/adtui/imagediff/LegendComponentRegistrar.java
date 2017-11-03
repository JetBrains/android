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
package com.android.tools.adtui.imagediff;

import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.LegendConfig;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.model.DefaultDataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.formatter.BaseAxisFormatter;
import com.android.tools.adtui.model.formatter.MemoryAxisFormatter;
import com.android.tools.adtui.model.formatter.NetworkTrafficFormatter;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;

import java.awt.*;

class LegendComponentRegistrar extends ImageDiffEntriesRegistrar {

  private static final BaseAxisFormatter MEMORY_AXIS_FORMATTER = new MemoryAxisFormatter(1, 2, 5);
  private static final BaseAxisFormatter NETWORK_AXIS_FORMATTER = new NetworkTrafficFormatter(1, 2, 5);

  public LegendComponentRegistrar() {
    registerSimpleLegendComponent();
  }
  private void registerSimpleLegendComponent() {
    // As a good portion of the legend component is composed by text, the similarity threshold needs to be increased.
    // 2% is a reasonable value because make the test not to fail because of different text rendering on different JDKs,
    // but also make it fail when something odd happens with the legend (e.g. when one of the icons doesn't render).
    float similarityThreshold = 2;
    register(new LegendComponentImageDiffEntry("simple_legend_component_baseline.png", similarityThreshold) {
      @Override
      protected void generateComponent() {
        // Create a simple legend component with different icon types.
        addLine("", new LineConfig(Color.YELLOW).setLegendIconType(LegendConfig.IconType.NONE), MEMORY_AXIS_FORMATTER);
        addLine("L", new LineConfig(Color.BLUE).setLegendIconType(LegendConfig.IconType.LINE), MEMORY_AXIS_FORMATTER);
        addLine("B", new LineConfig(Color.RED).setLegendIconType(LegendConfig.IconType.BOX), NETWORK_AXIS_FORMATTER);
        addLine("Z", new LineConfig(Color.GREEN).setLegendIconType(LegendConfig.IconType.DASHED_LINE), NETWORK_AXIS_FORMATTER);
      }
    });
  }

  private static abstract class LegendComponentImageDiffEntry extends AnimatedComponentImageDiffEntry {

    private static final int COMPONENT_HEIGHT_PX = 25;

    private static final int COMPONENT_WIDTH_PX = 275;

    private LegendComponent myLegend;

    private LegendComponentModel myLegendModel;

    private LegendComponentImageDiffEntry(String baselineFilename, float similarityThreshold) {
      super(baselineFilename, similarityThreshold);
    }

    @Override
    protected void setUp() {
      myLegendModel = new LegendComponentModel(100);
      myLegend = new LegendComponent(myLegendModel);
      myLegend.setFont(ImageDiffUtil.getDefaultFont());
      myContentPane.add(myLegend, BorderLayout.CENTER);
      myContentPane.setSize(COMPONENT_WIDTH_PX, COMPONENT_HEIGHT_PX);
      myComponents.add(myLegendModel);
    }

    @Override
    protected void generateTestData() {
      // Nothing to do
    }

    protected void addLine(String seriesLabel, LineConfig lineConfig, BaseAxisFormatter formatter) {
      Range yRange = new Range();
      DefaultDataSeries<Long> series = new DefaultDataSeries<>();
      RangedContinuousSeries rangedSeries = new RangedContinuousSeries(seriesLabel, myXRange, yRange, series);
      series.add(0, 999L);

      SeriesLegend legend = new SeriesLegend(rangedSeries, formatter, myXRange);
      myLegend.configure(legend, new LegendConfig(lineConfig));
      myLegendModel.add(legend);
    }
  }
}
