/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.editor.dependencies;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.structure.configurables.model.ArtifactDependencyMergedModel;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jdesktop.swingx.JXLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import java.awt.*;

import static com.intellij.util.ui.UIUtil.getLabelBackground;

class ArtifactDependencyEditor {
  private JPanel myPanel;
  private JTextField myGroupIdTextField;
  private JTextField myArtifactNameTextField;
  private JBLabel myScopeLabel;
  private TextFieldWithBrowseButton myScopeField;
  private JTextField myVersionTextField;
  private JButton myCheckForUpdatesButton;

  ArtifactDependencyEditor() {
    myScopeLabel.setLabelFor(myScopeField.getTextField());
    Color background = getLabelBackground();
    myGroupIdTextField.setBackground(background);
    myArtifactNameTextField.setBackground(background);
  }

  @NotNull
  public JPanel getPanel() {
    return myPanel;
  }

  void update(@NotNull ArtifactDependencyMergedModel dependency) {
    GradleCoordinate coordinate = dependency.getCoordinate();
    myGroupIdTextField.setText(coordinate.getGroupId());
    myArtifactNameTextField.setText(coordinate.getArtifactId());
    myVersionTextField.setText(coordinate.getRevision());
    myScopeField.setText(dependency.getConfigurationName());
  }
}
