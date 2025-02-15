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
package com.android.tools.idea.gradle.structure.configurables.dependencies.details;

import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsBaseDependency;
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.UIUtil;
import java.awt.Font;
import java.awt.Insets;
import org.jdesktop.swingx.JXLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class MultipleLibraryDependenciesDetails implements DependencyDetails {
  private JPanel myMainPanel;

  private JXLabel myArtifactNameLabel;
  private JXLabel myGroupIdLabel;

  private PsLibraryDependency myDependency;

  public MultipleLibraryDependenciesDetails() {
    setupUI();
  }

  @Override
  @NotNull
  public JPanel getPanel() {
    return myMainPanel;
  }

  @Override
  public void display(@NotNull PsBaseDependency dependency) {
    myDependency = (PsLibraryDependency)dependency;

    PsArtifactDependencySpec resolvedSpec = myDependency.getSpec();
    myArtifactNameLabel.setText(resolvedSpec.getName());
    myGroupIdLabel.setText(resolvedSpec.getGroup());
  }

  @Override
  @NotNull
  public Class<PsLibraryDependency> getSupportedModelType() {
    return PsLibraryDependency.class;
  }

  @Override
  @Nullable
  public PsLibraryDependency getModel() {
    return myDependency;
  }

  private void setupUI() {
    myMainPanel = new JPanel();
    myMainPanel.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setComponentStyle(UIUtil.ComponentStyle.SMALL);
    jBLabel1.setFontColor(UIUtil.FontColor.NORMAL);
    jBLabel1.setText("Group ID:");
    myMainPanel.add(jBLabel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                  GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                  false));
    final JBLabel jBLabel2 = new JBLabel();
    jBLabel2.setComponentStyle(UIUtil.ComponentStyle.SMALL);
    jBLabel2.setFontColor(UIUtil.FontColor.NORMAL);
    jBLabel2.setText("Artifact Name:");
    myMainPanel.add(jBLabel2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                  GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                  false));
    myGroupIdLabel = new JXLabel();
    Font myGroupIdLabelFont = UIManager.getFont("Tree.font");
    if (myGroupIdLabelFont != null) myGroupIdLabel.setFont(myGroupIdLabelFont);
    myMainPanel.add(myGroupIdLabel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, false));
    myArtifactNameLabel = new JXLabel();
    Font myArtifactNameLabelFont = UIManager.getFont("Tree.font");
    if (myArtifactNameLabelFont != null) myArtifactNameLabel.setFont(myArtifactNameLabelFont);
    myMainPanel.add(myArtifactNameLabel, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             null, null, null, 0, false));
  }
}
