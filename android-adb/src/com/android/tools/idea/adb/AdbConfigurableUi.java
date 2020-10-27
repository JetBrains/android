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
package com.android.tools.idea.adb;

import com.android.tools.idea.flags.StudioFlags;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import org.jetbrains.annotations.NotNull;

public class AdbConfigurableUi implements ConfigurableUi<AdbOptionsService> {
  private JPanel myPanel;
  private JBLabel myExistingAdbServerPortLabel;
  private JBCheckBox myUseLibusbBackendCheckbox;
  private JBIntSpinner myExistingAdbServerPortSpinner;
  private JRadioButton myAutomaticallyStartAndManageServerRadioButton;
  private JRadioButton myUseExistingManuallyManagedServerRadioButton;

  @Override
  public boolean isModified(@NotNull AdbOptionsService settings) {
    return myUseLibusbBackendCheckbox.isSelected() != settings.shouldUseLibusb()
           || myUseExistingManuallyManagedServerRadioButton.isSelected() != settings.shouldUseUserManagedAdb()
           || getUserManagedAdbPortNumber() != settings.getUserManagedAdbPort();
  }

  @Override
  public void reset(@NotNull AdbOptionsService settings) {
    myUseLibusbBackendCheckbox.setSelected(settings.shouldUseLibusb());
    if (settings.shouldUseUserManagedAdb()) {
      myUseExistingManuallyManagedServerRadioButton.setSelected(true);
    }
    else {
      myAutomaticallyStartAndManageServerRadioButton.setSelected(true);
    }
    myExistingAdbServerPortSpinner.setValue(settings.getUserManagedAdbPort());
    setPortNumberUiEnabled(settings.shouldUseUserManagedAdb());
  }

  @Override
  public void apply(@NotNull AdbOptionsService settings) throws ConfigurationException {
    settings.setAdbConfigs(myUseLibusbBackendCheckbox.isSelected(), myUseExistingManuallyManagedServerRadioButton.isSelected(),
                           getUserManagedAdbPortNumber());
  }

  public static boolean hasComponents() {
    return hasUseLibusbBackendCheckbox() || hasAdbServerLifecycleManagementComponents();
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    assert hasComponents();

    if (!hasUseLibusbBackendCheckbox()) {
      myPanel.remove(myUseLibusbBackendCheckbox);
    }
    if (!hasAdbServerLifecycleManagementComponents()) {
      myPanel.remove(myExistingAdbServerPortSpinner);
      myPanel.remove(myAutomaticallyStartAndManageServerRadioButton);
      myPanel.remove(myUseExistingManuallyManagedServerRadioButton);
    }
    return myPanel;
  }

  private void createUIComponents() {
    myExistingAdbServerPortSpinner = new JBIntSpinner(AdbOptionsService.USER_MANAGED_ADB_PORT_DEFAULT,
                                                      AdbOptionsService.USER_MANAGED_ADB_PORT_MIN_VALUE,
                                                      AdbOptionsService.USER_MANAGED_ADB_PORT_MAX_VALUE);
    myExistingAdbServerPortLabel = new JBLabel();
    myAutomaticallyStartAndManageServerRadioButton = new JRadioButton();
    myAutomaticallyStartAndManageServerRadioButton.addActionListener(event -> setPortNumberUiEnabled(false));
    myUseExistingManuallyManagedServerRadioButton = new JRadioButton();
    myUseExistingManuallyManagedServerRadioButton.addActionListener(event -> setPortNumberUiEnabled(true));
  }

  private void setPortNumberUiEnabled(boolean enabled) {
    myExistingAdbServerPortSpinner.setEnabled(enabled);
    myExistingAdbServerPortLabel.setEnabled(enabled);
  }

  private int getUserManagedAdbPortNumber() {
    return myExistingAdbServerPortSpinner.getNumber();
  }

  private static boolean hasUseLibusbBackendCheckbox() {
    // Currently, the libusb backend is only supported on Mac & Linux.
    return SystemInfo.isMac || SystemInfo.isLinux;
  }

  private static boolean hasAdbServerLifecycleManagementComponents() {
    return StudioFlags.ADB_SERVER_MANAGEMENT_MODE_SETTINGS_VISIBLE.get();
  }
}
