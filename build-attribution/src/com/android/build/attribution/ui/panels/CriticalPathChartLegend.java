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

import com.android.build.attribution.ui.data.TaskUiData;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.ui.ColorIcon;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

public interface CriticalPathChartLegend {
  JBColor MISC_COLOR = new JBColor(0xBDBDBD, 0xBDBDBD);
  JBColor OTHER_TASKS_COLOR = new JBColor(0xA2DFFE, 0xA2DFFE);
  JBColor androidPluginColor = new JBColor(0xE66F9A, 0xE66F9A);
  JBColor externalPluginColor = new JBColor(0x1A7AFF, 0x1A7AFF);
  JBColor buildsrcPluginColor = new JBColor(0xA78BD9, 0xA78BD9);

  JBColor[] categoricalGooglePalette = new JBColor[]{
    new JBColor(0x97B1C0, 0x97B1C0),
    new JBColor(0xA2DFFE, 0xA2DFFE),
    new JBColor(0xF79C6E, 0xF79C6E),
    new JBColor(0x74E288, 0x74E288),
    new JBColor(0xA78BD9, 0xA78BD9),
    new JBColor(0xE66F9A, 0xE66F9A),
    new JBColor(0x52E5CF, 0x52E5CF),
    new JBColor(0xDFCC9F, 0xDFCC9F),
    new JBColor(0x0093D4, 0x0093D4),
    new JBColor(0x158F7F, 0x158F7F),
    new JBColor(0x824BDF, 0x824BDF),
    new JBColor(0xC1571A, 0xC1571A),
    new JBColor(0x335A99, 0x335A99),
    new JBColor(0xADAC38, 0xADAC38),
    new JBColor(0xB8388E, 0xB8388E),
    new JBColor(0x1A7AFF, 0x1A7AFF),
  };

  static JPanel createTasksLegendPanel() {
    JPanel panel = new JPanel(new HorizontalLayout(10));
    panel.add(new JBLabel("Android/Java/Kotlin plugin", new ColorIcon(10, androidPluginColor), SwingConstants.RIGHT));
    panel.add(new JBLabel("external plugin", new ColorIcon(10, externalPluginColor), SwingConstants.RIGHT));
    panel.add(new JBLabel("buildSrc plugin", new ColorIcon(10, buildsrcPluginColor), SwingConstants.RIGHT));
    return panel;
  }

  static JBColor resolveTaskColor(TaskUiData taskData) {
    switch (taskData.getSourceType()) {
      case BUILD_SRC:
        return buildsrcPluginColor;
      case ANDROID_PLUGIN:
        return androidPluginColor;
      case THIRD_PARTY:
        return externalPluginColor;
      default:
        throw new IllegalArgumentException("Unknown type: " + taskData.getSourceType());
    }
  }

  class PluginColorPalette {
    private int paletteCursor = 0;

    public JBColor getNewColor() {
      return categoricalGooglePalette[Math.min(paletteCursor++, categoricalGooglePalette.length)];
    }
  }
}
