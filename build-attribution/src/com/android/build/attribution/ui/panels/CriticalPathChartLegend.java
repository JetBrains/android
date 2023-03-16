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
package com.android.build.attribution.ui.panels;

import com.android.build.attribution.ui.data.CriticalPathPluginUiData;
import com.android.build.attribution.ui.data.TaskUiData;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("UseJBColor")
public interface CriticalPathChartLegend {
  ChartColor OTHER_TASKS_COLOR = new ChartColor(new Color(0xA2DFFE));
  Color OTHER_TASKS_TEXT_COLOR = Color.BLACK;

  ChartColor androidPluginColor = new ChartColor(new Color(0xA2DFFE));
  ChartColor externalPluginColor = new ChartColor(new Color(0x097F5));
  ChartColor buildscriptPluginColor = new ChartColor(new Color(0xA78BD9));

  ChartColor[] categoricalGooglePalette = new ChartColor[]{
    new ChartColor(new Color(0x97B1C0)),
    new ChartColor(new Color(0xA2DFFE)),
    new ChartColor(new Color(0xF79C6E)),
    new ChartColor(new Color(0x74E288)),
    new ChartColor(new Color(0xA78BD9)),
    new ChartColor(new Color(0xE66F9A)),
    new ChartColor(new Color(0x52E5CF)),
    new ChartColor(new Color(0xDFCC9F)),
    new ChartColor(new Color(0x0093D4)),
    new ChartColor(new Color(0x158F7F)),
    new ChartColor(new Color(0x824BDF)),
    new ChartColor(new Color(0xC1571A)),
    new ChartColor(new Color(0x335A99)),
    new ChartColor(new Color(0xADAC38)),
    new ChartColor(new Color(0xB8388E)),
    new ChartColor(new Color(0x1A7AFF))
  };

  class ChartColor {
    public final Color baseColor;
    public final Color selectionColor;

    public ChartColor(Color baseColor) {
      this.baseColor = baseColor;
      this.selectionColor = new Color(baseColor.getRed() / 2, baseColor.getGreen() / 2, baseColor.getBlue() / 2);
    }
  }

  static ChartColor resolveTaskColor(TaskUiData taskData) {
    switch (taskData.getSourceType()) {
      case BUILD_SCRIPT:
        return buildscriptPluginColor;
      case ANDROID_PLUGIN:
        return androidPluginColor;
      case THIRD_PARTY:
        return externalPluginColor;
      default:
        throw new IllegalArgumentException("Unknown type: " + taskData.getSourceType());
    }
  }

  PluginColorPalette pluginColorPalette = new PluginColorPalette();

  class PluginColorPalette {
    private int paletteCursor = 0;
    private Map<String, ChartColor> pluginToColorMapping = new HashMap<>();

    public void reset() {
      paletteCursor = 0;
      pluginToColorMapping.clear();
    }

    @NotNull
    public ChartColor getColor(@NotNull String name) {
      return pluginToColorMapping
        .computeIfAbsent(name, key -> categoricalGooglePalette[Math.min(paletteCursor++, categoricalGooglePalette.length - 1)]);
    }

    @NotNull
    public ChartColor getOneColorForAll(@NotNull ArrayList<CriticalPathPluginUiData> aggregatedPlugins) {
      ChartColor otherPluginsGroupColor = getColor("Other");
      aggregatedPlugins.forEach(plugin -> pluginToColorMapping.put(plugin.getName(), otherPluginsGroupColor));
      return otherPluginsGroupColor;
    }
  }
}
