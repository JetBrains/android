/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.theme;

import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ThemeSelectionDialog;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.rendering.ResourceNameValidator;
import com.google.common.base.Strings;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class NewStyleDialog extends DialogWrapper {
  private final ResourceNameValidator myResourceNameValidator;
  private JPanel contentPane;
  private JTextField myStyleNameTextField;
  private TextFieldWithBrowseButton myParentStyleTextField;
  private JLabel myMessageLabel;

  public NewStyleDialog(@NotNull final Configuration configuration, @Nullable String defaultParentStyle, @Nullable String message) {
    super(true);

    if (!Strings.isNullOrEmpty(message)) {
      myMessageLabel.setText(message);
      myMessageLabel.setVisible(true);
    } else {
      myMessageLabel.setVisible(false);
    }

    myResourceNameValidator =
      ResourceNameValidator.create(false, AppResourceRepository.getAppResources(configuration.getModule(), true), ResourceType.STYLE);

    setTitle("New theme");

    myParentStyleTextField.setText(defaultParentStyle);
    myParentStyleTextField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ThemeSelectionDialog themeSelectionDialog = new ThemeSelectionDialog(configuration);
        if (themeSelectionDialog.showAndGet()) {
          myParentStyleTextField.setText(themeSelectionDialog.getTheme());
        }
      }
    });

    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myStyleNameTextField;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    String newStyleName = myStyleNameTextField.getText();
    if (Strings.isNullOrEmpty(newStyleName)) {
      return new ValidationInfo("You must specify a style name", myStyleNameTextField);
    }

    if (!myResourceNameValidator.checkInput(newStyleName)) {
      return new ValidationInfo(myResourceNameValidator.getErrorText(newStyleName), myStyleNameTextField);
    }

    return super.doValidate();
  }

  public String getStyleName() {
    return myStyleNameTextField.getText();
  }

  public String getStyleParentName() {
    return myParentStyleTextField.getText();
  }
}
