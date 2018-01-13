/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.adtui;

import com.android.tools.adtui.chart.linechart.LineConfig;
import org.junit.Test;

import java.awt.*;

import static org.junit.Assert.assertEquals;

public class LegendConfigTest {

  @Test
  public void correctSettingsFromLineConfigConstructor() {
    LineConfig line = new LineConfig(Color.PINK).setFilled(true).setLegendIconType(LegendConfig.IconType.BOX);
    LegendConfig legend = new LegendConfig(line);
    assertEquals(Color.PINK, legend.getColor());
    assertEquals(LegendConfig.IconType.BOX, legend.getIconType());

    line.setColor(Color.ORANGE).setLegendIconType(LegendConfig.IconType.LINE);
    legend = new LegendConfig(line);
    assertEquals(Color.ORANGE, legend.getColor());
    assertEquals(LegendConfig.IconType.LINE, legend.getIconType());

    line.setLegendIconType(LegendConfig.IconType.DASHED_LINE);
    legend = new LegendConfig(line);
    assertEquals(Color.ORANGE, legend.getColor());
    assertEquals(LegendConfig.IconType.DASHED_LINE, legend.getIconType());

    legend = new LegendConfig(new LineConfig(Color.PINK));
    // If legendIconType is not set, LegendConfig.IconType.NONE should be used
    assertEquals(LegendConfig.IconType.NONE, legend.getIconType());
  }

  @Test
  public void correctSettingsFromDefaultConstructor() {
    LegendConfig legend = new LegendConfig(LegendConfig.IconType.DASHED_LINE, Color.BLACK);
    assertEquals(LegendConfig.IconType.DASHED_LINE, legend.getIconType());
    assertEquals(Color.BLACK, legend.getColor());
  }
}