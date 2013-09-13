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

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class SelectSdkDialog extends DialogWrapper {
  private JPanel myPanel;
  private TextFieldWithBrowseButton myJdkTextFieldWithButton;
  private TextFieldWithBrowseButton mySdkTextFieldWithButton;
  private JBLabel myDescriptionLabel;
  private JBLabel mySelectJdkLabel;
  private JBLabel mySelectSdkLabel;

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
      String err = validateJdk(jdkPath);
      if (err != null) {
        jdkPath = null;
      }
    }

    if (sdkPath != null) {
      String err = validateAndroidSdk(sdkPath);
      if (err != null) {
        sdkPath = null;
      }
    }

    if (jdkPath == null && sdkPath == null) {
      myDescriptionLabel.setText(AndroidBundle.message("android.startup.missing.both"));
    }
    else if (jdkPath == null) {
      myDescriptionLabel.setText(AndroidBundle.message("android.startup.missing.jdk"));
      mySdkTextFieldWithButton.setVisible(false);
      mySelectSdkLabel.setVisible(false);
    }
    else {
      myDescriptionLabel.setText(AndroidBundle.message("android.startup.missing.sdk"));
      myJdkTextFieldWithButton.setVisible(false);
      mySelectJdkLabel.setVisible(false);
    }

    myJdkTextFieldWithButton.setTextFieldPreferredWidth(50);
    mySdkTextFieldWithButton.setTextFieldPreferredWidth(50);

    if (jdkPath != null) {
      myJdkTextFieldWithButton.setText(jdkPath);
    }

    if (sdkPath != null) {
      mySdkTextFieldWithButton.setText(sdkPath);
    }

    BrowseFolderListener listener =
      new BrowseFolderListener("Select JDK Home", myJdkTextFieldWithButton, JavaSdk.getInstance().getHomeChooserDescriptor(), jdkPath);
    myJdkTextFieldWithButton.addBrowseFolderListener(null, listener);

    listener =
      new BrowseFolderListener("Select Android SDK Home", mySdkTextFieldWithButton, AndroidSdkType.getInstance().getHomeChooserDescriptor(),
                               sdkPath);
    mySdkTextFieldWithButton.addBrowseFolderListener(null, listener);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    String jdkHome = myJdkTextFieldWithButton.getText().trim();
    String jdkError = validateJdk(jdkHome);
    if (jdkError != null) {
      return new ValidationInfo(jdkError, myJdkTextFieldWithButton.getTextField());
    }

    String androidHome = mySdkTextFieldWithButton.getText().trim();
    String sdkError = validateAndroidSdk(androidHome);
    if (sdkError != null) {
      return new ValidationInfo(sdkError, mySdkTextFieldWithButton.getTextField());
    }
    VersionCheck.VersionCheckResult result = VersionCheck.checkVersion(androidHome);
    if (!result.isCompatibleVersion()) {
      String msg = AndroidBundle.message("android.version.check.too.old", VersionCheck.MIN_TOOLS_REV, result.getRevision());
      return new ValidationInfo(msg, mySdkTextFieldWithButton.getTextField());
    }
    return null;
  }

  @Nullable
  private static String validateJdk(String path) {
    if (StringUtil.isEmpty(path) || !JavaSdk.getInstance().isValidSdkHome(path)) {
      return "Invalid JDK path.";
    }

    return null;
  }

  @Nullable
  private static String validateAndroidSdk(String path) {
    if (StringUtil.isEmpty(path)) {
      return "Android SDK path not specified.";
    }

    Pair<Boolean, String> validationResult = AndroidSdkType.validateAndroidSdk(path);
    String error = validationResult.getSecond();
    if (!validationResult.getFirst()) {
      return String.format("Invalid Android SDK (%1$s): %2$s", path, error);
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

      return myDefaultPath == null
             ? LocalFileSystem.getInstance().findFileByPath(PathManager.getHomePath())
             : LocalFileSystem.getInstance().findFileByPath(myDefaultPath);
    }
  }
}
