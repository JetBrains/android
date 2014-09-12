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
package com.android.tools.idea.welcome;

import com.android.tools.idea.wizard.ScopedStateStore;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Set;

/**
 * Wizard step for selecting existing SDK location.
 */
public class SdkLocationStep extends FirstRunWizardStep {
  private final ScopedStateStore.Key<Boolean> myShouldDownload;
  private final ScopedStateStore.Key<String> myExistingSdkLocation;

  private JRadioButton myDownloadTheLatestVersionRadioButton;
  private JRadioButton myUseAnExistingAndroidRadioButton;
  private JLabel myError;
  private TextFieldWithBrowseButton myExistingSdkPath;
  private JPanel myContentsPane;

  public SdkLocationStep(ScopedStateStore.Key<Boolean> shouldDownload, ScopedStateStore.Key<String> existingSdkLocation) {
    super("SDK Settings");
    myShouldDownload = shouldDownload;
    myExistingSdkLocation = existingSdkLocation;
    myError.setText("");
    myError.setForeground(JBColor.red);

    myExistingSdkPath.addBrowseFolderListener("Android SDK", "Provide Android SDK location", null,
                                              FileChooserDescriptorFactory.createSingleFolderDescriptor());
    setComponent(myContentsPane);
  }

  @Override
  public boolean validate() {
    setErrorHtml(null);
    return isDownloading() || validateSdkLocation(myState.get(myExistingSdkLocation));
  }

  private boolean validateSdkLocation(@Nullable String location) {
    String error = null;
    if (StringUtil.isEmpty(location)) {
      error = "SDK location is required";
    }
    else {
      File sdkLocation = new File(location);
      if (!sdkLocation.isDirectory()) {
        error = "Directory does not exist";
      }
      else {
        Pair<Boolean, String> result = AndroidSdkType.validateAndroidSdk(location);
        if (!result.first) {
          error = result.second;
        }
      }
    }

    setErrorHtml(error);
    return error == null;
  }

  @Override
  public void init() {
    register(myExistingSdkLocation, myExistingSdkPath);
    register(myShouldDownload, myContentsPane,
             new TwoRadiosToBooleanBinding(myDownloadTheLatestVersionRadioButton, myUseAnExistingAndroidRadioButton));

    deriveValues(ImmutableSet.<ScopedStateStore.Key>of(myShouldDownload));
  }

  @Override
  public void deriveValues(Set<ScopedStateStore.Key> modified) {
    if (modified.contains(myShouldDownload)) {
      myExistingSdkPath.setEnabled(!isDownloading());
    }
  }

  private boolean isDownloading() {
    return Objects.equal(myState.get(myShouldDownload), Boolean.TRUE);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myDownloadTheLatestVersionRadioButton;
  }

  @NotNull
  @Override
  public JLabel getMessageLabel() {
    return myError;
  }
}
