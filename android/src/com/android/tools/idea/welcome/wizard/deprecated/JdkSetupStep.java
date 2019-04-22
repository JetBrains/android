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
package com.android.tools.idea.welcome.wizard.deprecated;

import static com.android.tools.idea.io.FilePaths.toSystemDependentPath;
import static com.android.tools.idea.sdk.IdeSdks.MAC_JDK_CONTENT_PATH;
import static com.android.tools.idea.sdk.IdeSdks.getJdkFromJavaHome;
import static com.android.tools.idea.wizard.WizardConstants.KEY_JDK_LOCATION;
import static com.google.common.base.Strings.nullToEmpty;
import static com.intellij.openapi.fileChooser.FileChooser.chooseFile;
import static com.intellij.openapi.projectRoots.JdkUtil.checkForJdk;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import java.io.File;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JdkSetupStep extends FirstRunWizardStep {
  @NotNull private String myUserSelectedJdkPath = "";
  @NotNull private final ChangeListener myListener;
  private JPanel myRootPanel;
  private JRadioButton myUseEmbeddedJdkRadioButton;
  private JRadioButton myOtherRadioButton;
  private TextFieldWithBrowseButton myJdkLocationTextField;
  private JRadioButton myUseJavaHomeEnvironmentVariableRadioButton;

  public JdkSetupStep() {
    super("Select default JDK Location");

    myListener = new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent event) {
        String text = "";
        boolean enableButton = false;
        if (myUseJavaHomeEnvironmentVariableRadioButton.isSelected()) {
          text = nullToEmpty(getJdkFromJavaHome());
        }
        else if (myUseEmbeddedJdkRadioButton.isSelected()) {
          text = EmbeddedDistributionPaths.getInstance().getEmbeddedJdkPath().getPath();
        }
        else if (myOtherRadioButton.isSelected()) {
          text = myUserSelectedJdkPath;
          enableButton = true;
          myJdkLocationTextField.getTextField().requestFocus();
        }
        setJdkFieldText(text, enableButton);
      }
    };

    myUseJavaHomeEnvironmentVariableRadioButton.addChangeListener(myListener);
    myUseEmbeddedJdkRadioButton.addChangeListener(myListener);
    myOtherRadioButton.addChangeListener(myListener);
    myJdkLocationTextField.getButton().addActionListener(e -> chooseJdkLocation());
    setComponent(myRootPanel);
  }

  private void setJdkFieldText(String path, boolean enableButton) {
    myJdkLocationTextField.setText(path);
    myJdkLocationTextField.setEditable(false);
    myJdkLocationTextField.getButton().setEnabled(enableButton);
    myState.put(KEY_JDK_LOCATION, path);
    updateIsValidPath();
  }

  private void chooseJdkLocation() {
    if (!myOtherRadioButton.isSelected()) {
      // Show dialog only when "Other" option is selected
      return;
    }
    myJdkLocationTextField.getTextField().requestFocus();

    VirtualFile suggestedDir = null;
    File jdkLocation = getUserSelectedJdkLocation();
    if (jdkLocation.isDirectory()) {
      suggestedDir = findFileByIoFile(jdkLocation, false);
    }
    VirtualFile chosen = chooseFile(createSingleFolderDescriptor(file -> {
      File validJdkLocation = validateJdkPath(file);
      if (validJdkLocation == null) {
        throw new IllegalArgumentException("Please choose a valid JDK directory.");
      }
      return null;
    }), null, suggestedDir);
    if (chosen != null) {
      File validJdkLocation = validateJdkPath(virtualToIoFile(chosen));
      assert validJdkLocation != null;
      myUserSelectedJdkPath = validJdkLocation.getPath();
      myJdkLocationTextField.setText(myUserSelectedJdkPath);
      updateIsValidPath();
    }
  }

  @NotNull
  private static FileChooserDescriptor createSingleFolderDescriptor(@NotNull Function<? super File, Void> validation) {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
      @Override
      public void validateSelectedFiles(VirtualFile[] files) {
        for (VirtualFile virtualFile : files) {
          File file = virtualToIoFile(virtualFile);
          validation.fun(file);
        }
      }
    };
    if (SystemInfo.isMac) {
      descriptor.withShowHiddenFiles(true);
    }
    descriptor.setTitle("Choose JDK Location");
    return descriptor;
  }


  private void updateIsValidPath() {
    invokeUpdate(null);
  }

  @Nullable
  private File validateJdkPath(@NotNull File file) {
    if (checkForJdk(file)) {
      return file;
    }
    if (SystemInfo.isMac) {
      File potentialPath = new File(file, MAC_JDK_CONTENT_PATH);
      if (potentialPath.isDirectory() && checkForJdk(potentialPath)) {
        myJdkLocationTextField.setText(potentialPath.getPath());
        return potentialPath;
      }
    }
    return null;
  }

  @NotNull
  private File getUserSelectedJdkLocation() {
    return toSystemDependentPath(nullToEmpty(myUserSelectedJdkPath));
  }

  @Override
  public void init() {
    // Apply default selection
    myListener.stateChanged(null);
  }

  @Nullable
  @Override
  public JLabel getMessageLabel() {
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myRootPanel;
  }

  @Override
  public boolean validate() {
    if (!isValidJdkPath()) {
      return false;
    }
    return super.validate();
  }

  private boolean isValidJdkPath() {
    File chosenPath = toSystemDependentPath(myJdkLocationTextField.getText());
    File validatedPath = validateJdkPath(chosenPath);
    return validatedPath != null;
  }

  @Override
  public boolean commitStep() {
    if (!isValidJdkPath()) {
      return false;
    }
    ApplicationManager.getApplication().runWriteAction(() -> IdeSdks.getInstance().setJdkPath(getJdkLocation()));
    return true;
  }

  @NotNull
  private File getJdkLocation() {
    String jdkLocation = myJdkLocationTextField.getText();
    return toSystemDependentPath(jdkLocation);
  }

  @Override
  public boolean isStepVisible(){
    return false;
  }
}
