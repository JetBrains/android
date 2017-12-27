/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.profilers;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.android.util.SaveFileListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

public class ExportDialog extends DialogWrapper {
  @NotNull private final Project myProject;

  private JPanel myContentPane;
  private TextFieldWithBrowseButton myPathTextFieldWithButton;

  /**
   * @param dialogTitle   Title to be displayed on the "save as" dialog.
   * @param fileExtension The file extension for the destination file, without the dot.
   */
  public ExportDialog(@NotNull Project project, @NotNull String dialogTitle, @NotNull String fileExtension) {
    super(project);
    myProject = project;
    setTitle(dialogTitle);

    myPathTextFieldWithButton.addActionListener(
      new SaveFileListener(myContentPane, myPathTextFieldWithButton, dialogTitle, fileExtension) {
        @Nullable
        @Override
        protected String getDefaultLocation() {
          return myProject.getBasePath();
        }
      });
    myPathTextFieldWithButton.setTextFieldPreferredWidth(60);

    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myContentPane;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    String path = myPathTextFieldWithButton.getText().trim();
    JTextField textField = myPathTextFieldWithButton.getTextField();

    if (path.isEmpty()) {
      return new ValidationInfo("Destination should not be empty", textField);
    }

    File f = new File(path);
    if (!f.isAbsolute()) {
      return new ValidationInfo("Destination path must be absolute.", textField);
    }

    if (f.getParentFile() == null || !f.getParentFile().isDirectory()) {
      return new ValidationInfo("Invalid path", textField);
    }

    return null;
  }

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return "ExportDialogDimensions";
  }

  @NotNull
  File getFile() {
    return new File(myPathTextFieldWithButton.getText().trim());
  }
}

