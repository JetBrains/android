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
  public void testCorrectSettingsFromLineConfig() throws Exception {
    LineConfig line = new LineConfig(Color.PINK).setFilled(true);
    LegendConfig legend = new LegendConfig(line);
    assertEquals(Color.PINK, legend.getColor());
    assertEquals(LegendConfig.IconType.BOX, legend.getIcon());

    line.setFilled(false).setColor(Color.ORANGE);
    legend = new LegendConfig(line);
    assertEquals(Color.ORANGE, legend.getColor());
    assertEquals(LegendConfig.IconType.LINE, legend.getIcon());

    line.setLegendIconType(LegendConfig.IconType.DOTTED_LINE);
    legend = new LegendConfig(line);
    assertEquals(LegendConfig.IconType.DOTTED_LINE, legend.getIcon());
  }
}