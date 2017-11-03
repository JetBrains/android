/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.instantapp.provision;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ProvisionEditTaskDialog extends DialogWrapper {
  private JBCheckBox myClearCacheCheckBox;
  private JBCheckBox myClearProvisionedDevicesCheckBox;

  protected ProvisionEditTaskDialog(@Nullable Project project, boolean clearCache, boolean clearProvisionedDevices) {
    super(project);
    setTitle("Provision Specifications");
    myClearCacheCheckBox = new JBCheckBox("Clear cache", clearCache);
    myClearProvisionedDevicesCheckBox = new JBCheckBox("Clear provisioned devices cache", clearProvisionedDevices);
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel();
    panel.add(myClearCacheCheckBox);
    panel.add(myClearProvisionedDevicesCheckBox);
    return panel;
  }

  public boolean isClearCache() {
    return myClearCacheCheckBox.isSelected();
  }

  public boolean isClearProvisionedDevices() {
    return myClearProvisionedDevicesCheckBox.isSelected();
  }
}
