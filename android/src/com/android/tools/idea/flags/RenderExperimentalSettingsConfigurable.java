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
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.Dimension;
import java.awt.Insets;
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
    setupUI();
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
    return myQualitySlider.getValue() != (int)(mySettings.getQuality() * 100);
  }

  @Override
  public void apply() {
    mySettings.setQuality(myQualitySlider.getValue() / 100f);
  }

  @Override
  public void reset() {
    myQualitySlider.setValue((int)(mySettings.getQuality() * 100));
  }

  private void setupUI() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(2, 3, new Insets(0, 0, 0, 0), -1, -1));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setText("Quality Setting");
    myPanel.add(jBLabel1,
                new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    myPanel.add(spacer1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                             GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    final Spacer spacer2 = new Spacer();
    myPanel.add(spacer2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    myQualitySlider = new JSlider();
    myPanel.add(myQualitySlider, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                     new Dimension(150, -1), new Dimension(90, -1), null, 0, false));
  }
}
