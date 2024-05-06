/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.flags;

import com.android.tools.idea.rendering.RenderSettings;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import java.util.Hashtable;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import org.jetbrains.annotations.NotNull;

public class RenderExperimentalSettingsConfigurable implements ExperimentalConfigurable {
  private JPanel myPanel;
  private JSlider myQualitySlider;

  private final RenderSettings mySettings;

  public RenderExperimentalSettingsConfigurable(@NotNull Project project) {
    mySettings = RenderSettings.getProjectSettings(project);

    Hashtable<Integer, JComponent> qualityLabels = new Hashtable<>();
    qualityLabels.put(20, new JLabel("Fastest"));
    qualityLabels.put(100, new JLabel("Slowest"));
    myQualitySlider.setMinimum(20);
    myQualitySlider.setLabelTable(qualityLabels);
    myQualitySlider.setPaintLabels(true);
    myQualitySlider.setPaintTicks(true);
    myQualitySlider.setMajorTickSpacing(20);
  }

  @Override
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public boolean isModified() {
    return myQualitySlider.getValue() != (int)(mySettings.getQuality()*100);
  }

  @Override
  public void apply() {
    mySettings.setQuality(myQualitySlider.getValue()/100f);
  }

  @Override
  public void reset() {
    myQualitySlider.setValue((int)(mySettings.getQuality()*100));
  }
}
