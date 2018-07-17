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
package com.android.tools.idea.explorer.options;

import com.android.tools.idea.IdeInfo;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

final class DeviceFileExplorerConfigurable implements SearchableConfigurable {
  private JPanel myContentPanel;
  private TextFieldWithBrowseButton myDownloadLocation;

  DeviceFileExplorerConfigurable() {
    myDownloadLocation.addBrowseFolderListener("Device File Explorer Download Location", null, null,
                                               new FileChooserDescriptor(false, true, false,
                                                                         false, false, false));
  }

  @NotNull
  @Override
  public String getId() {
    return "device.file.explorer";
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myContentPanel;
  }

  @Override
  public boolean isModified() {
    return !DeviceFileExplorerSettings.getInstance().getDownloadLocation().equals(myDownloadLocation.getText());
  }

  @Override
  public void apply() throws ConfigurationException {
    // Validate the path for download location
    Path path = Paths.get(getDownloadLocation());
    if (Files.isDirectory(path)) {
      DeviceFileExplorerSettings.getInstance().setDownloadLocation(path.toString());
    } else {
      throw new ConfigurationException("Path must be an existing directory", "Invalid Path");
    }
  }

  @Override
  public void reset() {
    myDownloadLocation.setText(DeviceFileExplorerSettings.getInstance().getDownloadLocation());
  }

  @Override
  public void disposeUIResources() {
  }

  @Nls
  @Override
  public String getDisplayName() {
    if (IdeInfo.getInstance().isAndroidStudio()) {
      return "Device File Explorer";
    } else {
      return "Android Device File Explorer";
    }
  }

  @NotNull
  private String getDownloadLocation() {
    return myDownloadLocation.getText();
  }
}
