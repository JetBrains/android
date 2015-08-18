/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.ddms.hprof;

import com.android.SdkConstants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.SaveFileListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

public class ConvertHprofDialog extends DialogWrapper {
  private static final String TITLE = AndroidBundle.message("android.profiler.hprof.actions.conv.savedialog.title");
  private static String ourLastUsedPath = null;

  private final Project myProject;

  private JPanel myContentPane;
  private TextFieldWithBrowseButton myPathTextFieldWithButton;

  public ConvertHprofDialog(@NotNull Project project) {
    super(project);
    myProject = project;
    setTitle(TITLE);

    if (ourLastUsedPath != null) {
      myPathTextFieldWithButton.getTextField().setText(ourLastUsedPath);
    }

    myPathTextFieldWithButton.addActionListener(
      new SaveFileListener(myContentPane, myPathTextFieldWithButton, TITLE, SdkConstants.EXT_HPROF) {
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
    return "AndroidSaveHprofDialogDimensions";
  }

  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  @Override
  protected void doOKAction() {
    ourLastUsedPath = myPathTextFieldWithButton.getText().trim();
    super.doOKAction();
  }

  @NotNull
  public File getHprofFile() {
    return new File(myPathTextFieldWithButton.getText().trim());
  }
}
