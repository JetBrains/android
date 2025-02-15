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

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.ui.properties.ModelPropertyEditor;
import com.android.tools.idea.gradle.structure.model.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.UIUtil;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Insets;
import kotlin.Unit;
import org.jdesktop.swingx.JXLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class SingleDeclaredLibraryDependencyDetails implements ConfigurationDependencyDetails {
  private JPanel myMainPanel;

  private JXLabel myGroupIdLabel;
  private JXLabel myArtifactNameLabel;
  private JPanel myRequestedVersion;
  private JPanel myConfigurationPanel;

  @NotNull private final PsContext myContext;
  @Nullable private PsDeclaredLibraryDependency myDependency;
  @Nullable private ModelPropertyEditor<?> myVersionPropertyEditor;
  @Nullable private JComponent myEditorComponent;

  public SingleDeclaredLibraryDependencyDetails(@NotNull PsContext context) {
    setupUI();
    myContext = context;
  }

  @Override
  @NotNull
  public JPanel getPanel() {
    return myMainPanel;
  }

  @Override
  public void display(@NotNull PsBaseDependency dependency) {
    PsDeclaredLibraryDependency d = (PsDeclaredLibraryDependency)dependency;

    displayVersion(d);
    displayConfiguration(d, PsModule.ImportantFor.LIBRARY);
    if (myDependency != dependency) {
      PsArtifactDependencySpec spec = d.getSpec();
      myGroupIdLabel.setText(spec.getGroup());
      myArtifactNameLabel.setText(spec.getName());
    }

    myDependency = d;
  }

  private void displayVersion(@NotNull PsDeclaredLibraryDependency dependency) {
    if (myVersionPropertyEditor != null) {
      if (dependency == myDependency) {
        myVersionPropertyEditor.reloadIfNotChanged();
      } else {
        if (myEditorComponent != null) {
          myRequestedVersion.remove(myEditorComponent);
        }
        Disposer.dispose(myVersionPropertyEditor);
        myVersionPropertyEditor = null; // remake the editor below
      }
    }
    if (myVersionPropertyEditor == null) {
      myVersionPropertyEditor =
        DeclaredLibraryDependencyUiProperties.INSTANCE.makeVersionUiProperty(dependency)
          .createEditor(myContext, dependency.getParent().getParent(), dependency.getParent(), Unit.INSTANCE, null, null);
      myEditorComponent = myVersionPropertyEditor.getComponent();
      myEditorComponent.setName("version");
      myRequestedVersion.add(myEditorComponent);
    }
  }

  @Override
  @NotNull
  public Class<PsDeclaredLibraryDependency> getSupportedModelType() {
    return PsDeclaredLibraryDependency.class;
  }

  @Override
  @Nullable
  public PsDeclaredLibraryDependency getModel() {
    return myDependency;
  }

  @Override
  public PsContext getContext() {
    return myContext;
  }

  @Override
  public JPanel getConfigurationUI() {
    return myConfigurationPanel;
  }

  private void setupUI() {
    myMainPanel = new JPanel();
    myMainPanel.setLayout(new GridLayoutManager(4, 3, new Insets(0, 0, 0, 0), -1, -1));
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
    jBLabel2.setHorizontalAlignment(10);
    jBLabel2.setText("Artifact Name:");
    myMainPanel.add(jBLabel2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                  GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                  false));
    myGroupIdLabel = new JXLabel();
    Font myGroupIdLabelFont = UIManager.getFont("Tree.font");
    if (myGroupIdLabelFont != null) myGroupIdLabel.setFont(myGroupIdLabelFont);
    myMainPanel.add(myGroupIdLabel, new GridConstraints(0, 1, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, false));
    myArtifactNameLabel = new JXLabel();
    Font myArtifactNameLabelFont = UIManager.getFont("Tree.font");
    if (myArtifactNameLabelFont != null) myArtifactNameLabel.setFont(myArtifactNameLabelFont);
    myMainPanel.add(myArtifactNameLabel, new GridConstraints(1, 1, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             null, null, null, 0, false));
    final JBLabel jBLabel3 = new JBLabel();
    jBLabel3.setComponentStyle(UIUtil.ComponentStyle.SMALL);
    jBLabel3.setFontColor(UIUtil.FontColor.NORMAL);
    jBLabel3.setText("Configuration:");
    myMainPanel.add(jBLabel3, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                  GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                  false));
    final JBLabel jBLabel4 = new JBLabel();
    jBLabel4.setComponentStyle(UIUtil.ComponentStyle.SMALL);
    jBLabel4.setFontColor(UIUtil.FontColor.NORMAL);
    jBLabel4.setText("Requested Version:");
    myMainPanel.add(jBLabel4, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                  GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                  false));
    myRequestedVersion = new JPanel();
    myRequestedVersion.setLayout(new BorderLayout(0, 0));
    myMainPanel.add(myRequestedVersion, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            null, null, null, 0, false));
    myConfigurationPanel = new JPanel();
    myConfigurationPanel.setLayout(new BorderLayout(0, 0));
    myMainPanel.add(myConfigurationPanel, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                              null, null, null, 0, false));
  }
}
