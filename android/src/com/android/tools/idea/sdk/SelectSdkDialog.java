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

package com.android.tools.idea.sdk;

import com.android.tools.idea.io.FilePaths;
import com.android.tools.sdk.SdkPaths.ValidationResult;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.tools.sdk.SdkPaths.validateAndroidSdk;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;

public class SelectSdkDialog extends DialogWrapper {
  private JPanel myPanel;
  private TextFieldWithBrowseButton myJdkTextFieldWithButton;
  private TextFieldWithBrowseButton mySdkTextFieldWithButton;
  private JBLabel mySelectSdkDescriptionLabel;
  private HyperlinkLabel mySdkHyperlinkLabel;
  private JBLabel mySelectSdkLabel;
  private JBLabel mySelectJdkDescriptionLabel;
  private HyperlinkLabel myJdkHyperlinkLabel;
  private JBLabel mySelectJdkLabel;
  private JBLabel mySpacer;

  private String myJdkHome = "";
  private String mySdkHome = "";

  /**
   * Displays SDK selection dialog.
   *
   * @param jdkPath path to JDK if known, null otherwise
   * @param sdkPath path to Android SDK if known, null otherwise
   */
  public SelectSdkDialog(@Nullable String jdkPath, @Nullable String sdkPath) {
    super(false);

    init();

    setTitle("Select SDKs");

    if (jdkPath != null) {
      String err = validateJdkPath(jdkPath);
      if (err != null) {
        jdkPath = null;
      }
    }

    if (sdkPath != null) {
      String err = validateAndroidSdkPath(sdkPath);
      if (err != null) {
        sdkPath = null;
      }
    }

    mySelectJdkLabel.setLabelFor(myJdkTextFieldWithButton.getTextField());

    mySelectSdkDescriptionLabel.setText("Please provide the path to the Android SDK.");
    mySdkHyperlinkLabel.setHyperlinkTarget("http://d.android.com/sdk");
    mySdkHyperlinkLabel.setHyperlinkText("If you do not have the Android SDK, you can obtain it from ", "d.android.com/sdk", ".");

    mySelectJdkDescriptionLabel.setText("Please provide the path to a Java Development Kit (JDK) installation.");
    myJdkHyperlinkLabel.setHyperlinkTarget("http://www.oracle.com/technetwork/java/javase/downloads/index.html");
    myJdkHyperlinkLabel.setHyperlinkText("If you do not have a JDK installed, you can obtain one ", "here", ".");

    if (jdkPath == null && sdkPath == null) {
      mySpacer.setVisible(true);
    }
    else if (jdkPath == null) {
      mySpacer.setVisible(false);
      mySelectSdkDescriptionLabel.setVisible(false);
      mySdkHyperlinkLabel.setVisible(false);
      mySelectSdkLabel.setVisible(false);
      mySdkTextFieldWithButton.setVisible(false);
    }
    else {
      mySpacer.setVisible(false);
      mySelectJdkDescriptionLabel.setVisible(false);
      myJdkHyperlinkLabel.setVisible(false);
      mySelectJdkLabel.setVisible(false);
      myJdkTextFieldWithButton.setVisible(false);
    }

    myJdkTextFieldWithButton.setTextFieldPreferredWidth(50);
    mySdkTextFieldWithButton.setTextFieldPreferredWidth(50);

    if (jdkPath != null) {
      myJdkTextFieldWithButton.setText(jdkPath);
    }

    if (sdkPath != null) {
      mySdkTextFieldWithButton.setText(sdkPath);
    }

    FileChooserDescriptor descriptor = JavaSdk.getInstance().getHomeChooserDescriptor();
    BrowseFolderListener listener = new BrowseFolderListener("Select JDK Home", myJdkTextFieldWithButton, descriptor, jdkPath);
    myJdkTextFieldWithButton.addActionListener(listener);

    descriptor = AndroidSdkType.getInstance().getHomeChooserDescriptor();
    listener = new BrowseFolderListener("Select Android SDK Home", mySdkTextFieldWithButton, descriptor, sdkPath);
    mySdkTextFieldWithButton.addActionListener(listener);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected boolean postponeValidation() {
    return false;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    String jdkHome = myJdkTextFieldWithButton.getText().trim();
    String jdkError = validateJdkPath(jdkHome);
    if (jdkError != null) {
      return new ValidationInfo(jdkError, myJdkTextFieldWithButton.getTextField());
    }

    String androidHome = mySdkTextFieldWithButton.getText().trim();
    String sdkError = validateAndroidSdkPath(androidHome);
    if (sdkError != null) {
      return new ValidationInfo(sdkError, mySdkTextFieldWithButton.getTextField());
    }
    return null;
  }

  @Nullable
  private static String validateJdkPath(@Nullable String path) {
    if (isEmpty(path) || !JavaSdk.getInstance().isValidSdkHome(path)) {
      return "Invalid JDK path.";
    }
    return null;
  }

  @Nullable
  private static String validateAndroidSdkPath(@Nullable String path) {
    if (isEmpty(path)) {
      return "Android SDK path not specified.";
    }

    ValidationResult validationResult = validateAndroidSdk(FilePaths.stringToFile(path), false);
    if (!validationResult.success) {
      // Show error message in new line. Long lines trigger incorrect layout rendering.
      // See https://code.google.com/p/android/issues/detail?id=78291
      return String.format("Invalid Android SDK path:<br>%1$s", validationResult.message);
    } else {
      return null;
    }
  }

  @Override
  protected void doOKAction() {
    myJdkHome = myJdkTextFieldWithButton.getText();
    mySdkHome = mySdkTextFieldWithButton.getText();
    super.doOKAction();
  }

  @NotNull
  public String getJdkHome() {
    return myJdkHome;
  }

  @NotNull
  public String getAndroidHome() {
    return mySdkHome;
  }

  private static class BrowseFolderListener extends ComponentWithBrowseButton.BrowseFolderActionListener<JTextField> {
    private final String myDefaultPath;

    public BrowseFolderListener(@Nullable String title,
                                ComponentWithBrowseButton<JTextField> textField,
                                FileChooserDescriptor fileChooserDescriptor,
                                @Nullable String defaultPath) {
      super(title, null, textField, null, fileChooserDescriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
      myDefaultPath = defaultPath;
    }

    @Nullable
    @Override
    protected VirtualFile getInitialFile() {
      String dir = super.getComponentText();
      if (!dir.isEmpty()) {
        return super.getInitialFile();
      }

      LocalFileSystem fileSystem = LocalFileSystem.getInstance();
      return fileSystem.findFileByPath(toSystemIndependentName(myDefaultPath == null ? PathManager.getHomePath() : myDefaultPath));
    }
  }
}
