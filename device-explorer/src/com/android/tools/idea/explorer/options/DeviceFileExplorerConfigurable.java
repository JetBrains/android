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
import com.android.tools.idea.explorer.DeviceExplorerBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsContexts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DeviceFileExplorerConfigurable implements SearchableConfigurable {
  private JPanel myContentPanel;
  private TextFieldWithBrowseButton myDownloadLocation;

  DeviceFileExplorerConfigurable() {
    myDownloadLocation.addBrowseFolderListener(DeviceExplorerBundle.message("dialog.title.device.file.explorer.download.location"), null, null,
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
      throw new ConfigurationException(DeviceExplorerBundle.message("dialog.message.path.must.be.existing.directory"),
                                       DeviceExplorerBundle.message("dialog.title.invalid.path"));
    }
  }

  @Override
  public void reset() {
    myDownloadLocation.setText(DeviceFileExplorerSettings.getInstance().getDownloadLocation());
  }

  @Override
  public void disposeUIResources() {
    SearchableConfigurable.super.disposeUIResources();
  }

  @NlsContexts.ConfigurableName
  @Override
  public String getDisplayName() {
    if (IdeInfo.getInstance().isAndroidStudio()) {
      return DeviceExplorerBundle.message("configurable.name.device.file.explorer");
    } else {
      return DeviceExplorerBundle.message("configurable.name.android.device.file.explorer");
    }
  }

  @NotNull
  private String getDownloadLocation() {
    return myDownloadLocation.getText();
  }
}
