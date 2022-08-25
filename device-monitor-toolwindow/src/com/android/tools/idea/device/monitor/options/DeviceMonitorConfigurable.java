/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.monitor.options;

import com.android.tools.idea.IdeInfo;
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

final class DeviceMonitorConfigurable implements SearchableConfigurable {
  private JPanel myContentPanel;
  private TextFieldWithBrowseButton myDownloadLocation;

  DeviceMonitorConfigurable() {
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
    return !DeviceMonitorSettings.getInstance().getDownloadLocation().equals(myDownloadLocation.getText());
  }

  @Override
  public void apply() throws ConfigurationException {
    // Validate the path for download location
    Path path = Paths.get(getDownloadLocation());
    if (Files.isDirectory(path)) {
      DeviceMonitorSettings.getInstance().setDownloadLocation(path.toString());
    } else {
      throw new ConfigurationException("Foo",
                                       "Bar");
    }
  }

  @Override
  public void reset() {
    myDownloadLocation.setText(DeviceMonitorSettings.getInstance().getDownloadLocation());
  }

  @Override
  public void disposeUIResources() {
  }

  @NlsContexts.ConfigurableName
  @Override
  public String getDisplayName() {
    if (IdeInfo.getInstance().isAndroidStudio()) {
      //TODO: Use bundle if still needed
      return "Device Monitor";
    } else {
      return "Android Device Monitor";
    }
  }

  @NotNull
  private String getDownloadLocation() {
    return myDownloadLocation.getText();
  }
}
