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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.editor;

import com.android.tools.idea.gradle.structure.model.PsdArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.android.PsdLibraryDependencyModel;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.intellij.util.ui.UIUtil.getLabelBackground;

public class LibraryDependencyEditor implements DependencyEditor<PsdLibraryDependencyModel> {
  private JPanel myPanel;
  private JTextField myGroupIdTextField;
  private JTextField myArtifactNameTextField;
  private JBLabel myScopeLabel;
  private TextFieldWithBrowseButton myScopeField;
  private JTextField myDeclaredVersionTextField;
  private JTextField myResolvedVersionTextField;
  private JButton myCheckForUpdatesButton;

  public LibraryDependencyEditor() {
    myScopeLabel.setLabelFor(myScopeField.getTextField());
    Color background = getLabelBackground();
    myGroupIdTextField.setBackground(background);
    myArtifactNameTextField.setBackground(background);
    myResolvedVersionTextField.setBackground(background);
  }

  @Override
  @NotNull
  public JPanel getPanel() {
    return myPanel;
  }

  @Override
  public void display(@NotNull PsdLibraryDependencyModel model) {
    PsdArtifactDependencySpec declaredSpec = model.getDeclaredSpec();
    assert declaredSpec != null;
    myGroupIdTextField.setText(declaredSpec.group);
    myArtifactNameTextField.setText(declaredSpec.name);
    myScopeField.setText(model.getConfigurationName());
    myDeclaredVersionTextField.setText(declaredSpec.version);
    myResolvedVersionTextField.setText(model.getResolvedSpec().version);
  }

  @Override
  @NotNull
  public Class<PsdLibraryDependencyModel> getSupportedModelType() {
    return PsdLibraryDependencyModel.class;
  }
}
