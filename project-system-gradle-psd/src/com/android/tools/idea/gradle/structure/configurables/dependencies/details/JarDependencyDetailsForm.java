/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.dependencies.details;

import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.UIUtil;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.UIManager;
import org.jdesktop.swingx.JXLabel;

public abstract class JarDependencyDetailsForm implements ConfigurationDependencyDetails {
  protected JPanel myMainPanel;
  protected JXLabel myNameText;
  protected JXLabel myIncludesText;
  protected JBLabel myExcludesLabel;
  protected JXLabel myExcludesText;
  protected JBLabel myNameLabel;
  protected JBLabel myIncludesLabel;
  protected JBLabel myConfigurationLabel;
  protected JPanel myConfigurationPanel;

  public JarDependencyDetailsForm() {
    setupUI();
  }

  @Override
  public JPanel getConfigurationUI() {
    return myConfigurationPanel;
  }

  private void setupUI() {
    myMainPanel = new JPanel();
    myMainPanel.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
    myNameLabel = new JBLabel();
    myNameLabel.setComponentStyle(UIUtil.ComponentStyle.SMALL);
    myNameLabel.setFontColor(UIUtil.FontColor.BRIGHTER);
    myNameLabel.setText("Name:");
    myMainPanel.add(myNameLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                     0, false));
    myNameText = new JXLabel();
    Font myNameTextFont = UIManager.getFont("Tree.font");
    if (myNameTextFont != null) myNameText.setFont(myNameTextFont);
    myMainPanel.add(myNameText, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                    null, 0, false));
    myIncludesLabel = new JBLabel();
    myIncludesLabel.setComponentStyle(UIUtil.ComponentStyle.SMALL);
    myIncludesLabel.setFontColor(UIUtil.FontColor.BRIGHTER);
    myIncludesLabel.setText("Includes:");
    myMainPanel.add(myIncludesLabel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                         GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                         null, 0, false));
    myIncludesText = new JXLabel();
    Font myIncludesTextFont = UIManager.getFont("Tree.font");
    if (myIncludesTextFont != null) myIncludesText.setFont(myIncludesTextFont);
    myIncludesText.setText("");
    myMainPanel.add(myIncludesText, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, false));
    myExcludesLabel = new JBLabel();
    myExcludesLabel.setComponentStyle(UIUtil.ComponentStyle.SMALL);
    myExcludesLabel.setFontColor(UIUtil.FontColor.BRIGHTER);
    myExcludesLabel.setText("Excludes:");
    myMainPanel.add(myExcludesLabel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                         GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                         null, 0, false));
    myExcludesText = new JXLabel();
    Font myExcludesTextFont = UIManager.getFont("Tree.font");
    if (myExcludesTextFont != null) myExcludesText.setFont(myExcludesTextFont);
    myMainPanel.add(myExcludesText, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, false));
    myConfigurationLabel = new JBLabel();
    myConfigurationLabel.setComponentStyle(UIUtil.ComponentStyle.SMALL);
    myConfigurationLabel.setFontColor(UIUtil.FontColor.BRIGHTER);
    myConfigurationLabel.setText("Configuration:");
    myMainPanel.add(myConfigurationLabel, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                              GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                              null, null, 0, false));
    myConfigurationPanel = new JPanel();
    myConfigurationPanel.setLayout(new BorderLayout(0, 0));
    myMainPanel.add(myConfigurationPanel, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                              null, null, null, 0, false));
  }
}
