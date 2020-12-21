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
package com.android.tools.idea.gradle.actions;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_CPP_EXTERNAL_PROJECT_LINKED;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.jetbrains.android.facet.AndroidRootUtil.getPathRelativeToModuleDir;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.google.common.base.Strings;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jdesktop.swingx.JXLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LinkExternalCppProjectDialog extends DialogWrapper {
  private static final String CMAKE_PATH_DESCRIPTION = "Select the main CMakeLists.txt file of a CMake project";
  private static final String NDK_BUILD_PATH_DESCRIPTION = "Select the main .mk file of the NDK project (e.g. Android.mk)";

  private enum BuildSystem {
    CMAKE("CMake"),
    NDK_BUILD("ndk-build");

    private String myText;

    BuildSystem(String text) {
      myText = text;
    }

    public String toString() {
      return myText;
    }
  }

  @NotNull private final Module myModule;

  private JPanel myPanel;
  private JComboBox<BuildSystem> myBuildSystemCombo;
  private JXLabel myProjectPathDescriptionLabel;
  private TextFieldWithBrowseButton myProjectPathTextField;
  private JXLabel myProjectPathResultLabel;

  public LinkExternalCppProjectDialog(@NotNull Module module) {
    super(false);

    myModule = module;

    init();

    setTitle("Link C++ Project with Gradle");

    myBuildSystemCombo.addItem(BuildSystem.CMAKE);
    myBuildSystemCombo.addItem(BuildSystem.NDK_BUILD);

    myBuildSystemCombo.setSelectedItem(BuildSystem.CMAKE);
    myProjectPathDescriptionLabel.setText(CMAKE_PATH_DESCRIPTION);

    myBuildSystemCombo.addItemListener(e -> {
      if (myBuildSystemCombo.getSelectedItem() == BuildSystem.CMAKE) {
        myProjectPathDescriptionLabel.setText(CMAKE_PATH_DESCRIPTION);
      }
      else {
        myProjectPathDescriptionLabel.setText(NDK_BUILD_PATH_DESCRIPTION);
      }
    });

    getOKAction().setEnabled(false);

    myProjectPathTextField.setTextFieldPreferredWidth(50);
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public void validateSelectedFiles(@NotNull VirtualFile[] files) throws Exception {
        for (VirtualFile virtualFile : files) {
          String errorMessage = validateProjectFilePath(virtualToIoFile(virtualFile));
          if (errorMessage != null) {
            throw new IllegalArgumentException(errorMessage);
          }
        }
      }
    };

    descriptor.setTitle("Choose C++ Project Location");

    myProjectPathTextField.addActionListener(new ComponentWithBrowseButton.BrowseFolderActionListener<>(
      "Select C++ Project Location", null, myProjectPathTextField, null, descriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT));

    myProjectPathResultLabel.setVisible(false);
  }

  @Override
  protected boolean postponeValidation() {
    return false;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    String projectPath = toSystemIndependentName(myProjectPathTextField.getText().trim());
    if (Strings.isNullOrEmpty(projectPath)) {
      getOKAction().setEnabled(false);
      setProjectPathResultLabelVisible(false);
      return null;
    }

    String errorMessage = validateProjectFilePath(new File(projectPath));
    if (errorMessage != null) {
      setProjectPathResultLabelVisible(false);
      return new ValidationInfo(errorMessage, myProjectPathTextField.getTextField());
    }
    String relativePath = getPathRelativeToModuleDir(myModule, projectPath);
    assert relativePath != null;

    myProjectPathResultLabel.setText("<html>Path to be saved into the build.gradle file:<br><b>\"" + relativePath + "\"</b></html>");

    getOKAction().setEnabled(true);
    setProjectPathResultLabelVisible(true);

    return null;
  }

  private void setProjectPathResultLabelVisible(boolean newVisible) {
    boolean oldVisible = myProjectPathResultLabel.isVisible();
    if (oldVisible != newVisible) {
      myProjectPathResultLabel.setVisible(newVisible);

      myPanel.doLayout();
      myPanel.revalidate();
      myPanel.repaint();
    }
  }

  @Nullable
  private String validateProjectFilePath(@NotNull File file) {
    if (!file.exists()) {
      return "The selected file does not exist";
    }
    if (myBuildSystemCombo.getSelectedItem() == BuildSystem.CMAKE && !file.getName().equals("CMakeLists.txt")) {
      return "Invalid file name. Expected: CMakeLists.txt";
    }
    else if (myBuildSystemCombo.getSelectedItem() == BuildSystem.NDK_BUILD && !FileUtilRt.extensionEquals(file.getPath(), "mk")) {
      return "Invalid file extension. Expected: .mk";
    }
    return null;
  }

  @Override
  protected void doOKAction() {
    String projectPath = toSystemIndependentName(myProjectPathTextField.getText().trim());
    String relativePath = getPathRelativeToModuleDir(myModule, projectPath);
    assert relativePath != null;

    GradleBuildModel buildModel = GradleBuildModel.get(myModule);
    assert buildModel != null;

    AndroidModel android = buildModel.android();

    if (myBuildSystemCombo.getSelectedItem() == BuildSystem.CMAKE) {
      android.externalNativeBuild().cmake().path().setValue(relativePath);
    }
    else {
      android.externalNativeBuild().ndkBuild().path().setValue(relativePath);
    }

    Project project = myModule.getProject();
    WriteCommandAction.writeCommandAction(project).withName("Link C++ Project with Gradle").run(() -> {
      buildModel.applyChanges();
    });

    GradleSyncInvoker.getInstance().requestProjectSync(project, TRIGGER_CPP_EXTERNAL_PROJECT_LINKED);
    super.doOKAction();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
