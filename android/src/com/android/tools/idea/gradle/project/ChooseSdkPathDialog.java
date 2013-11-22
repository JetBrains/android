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
package com.android.tools.idea.gradle.project;

import com.intellij.openapi.ui.DialogWrapper;
import org.jdesktop.swingx.JXLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class ChooseSdkPathDialog extends DialogWrapper {
  public static final int USE_IDE_SDK_PATH = 3;
  public static final int USE_PROJECT_SDK_PATH = 4;

  private JPanel myPanel;
  private JXLabel myDescriptionLabel;

  public ChooseSdkPathDialog(@NotNull String ideSdkPath, @NotNull String localPropertiesSdkPath) {
    super(null);
    init();

    setTitle("Android SDK Manager");
    setCrossClosesWindow(false);
    setResizable(false);

    String description = String.format("The project and Android Studio point to different Android SDKs.\n\n" +
                                       "Android Studio's default SDK is in:\n" +
                                       "%1$s\n\n" +
                                       "The project's SDK (specified in local.properties) is in:\n" +
                                       "%2$s\n\n" +
                                       "To keep results consistent between IDE and command line builds, only one path can be used.\n" +
                                       "Do you want to:\n\n" +
                                       "[1] Use Android Studio's default SDK (modifies the project's local.properties file.)\n\n" +
                                       "[2] Use the project's SDK (modifies Android Studio's default.)\n\n" +
                                       "Note that switching SDKs could cause compile errors if the selected SDK doesn't have the " +
                                       "necessary Android platforms or build tools.",
                                       ideSdkPath, localPropertiesSdkPath);
    myDescriptionLabel.setText(description);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    return new Action[]{new UseIdeSdkPathAction(), new UseProjectSdkPathAction()};
  }

  private class UseIdeSdkPathAction extends DialogWrapperAction {
    protected UseIdeSdkPathAction() {
      super("Use Android Studio's SDK");
    }

    @Override
    protected void doAction(ActionEvent e) {
      close(USE_IDE_SDK_PATH);
    }
  }

  private class UseProjectSdkPathAction extends DialogWrapperAction {
    protected UseProjectSdkPathAction() {
      super("Use Project's SDK");
    }

    @Override
    protected void doAction(ActionEvent e) {
      close(USE_PROJECT_SDK_PATH);
    }
  }
}
