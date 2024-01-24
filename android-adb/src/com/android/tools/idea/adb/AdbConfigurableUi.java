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

import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.components.JBLabel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import org.jetbrains.annotations.NotNull;

public class AdbConfigurableUi implements ConfigurableUi<AdbOptionsService> {
  private JPanel myPanel;
  private JBLabel myExistingAdbServerPortLabel;
  private JBIntSpinner myExistingAdbServerPortSpinner;
  private JRadioButton myAutomaticallyStartAndManageServerRadioButton;
  private JRadioButton myUseExistingManuallyManagedServerRadioButton;
  private JComboBox<String> myAdbServerUsbBackend;
  private HyperlinkLabel myAdbServerUsbBackendLabel;
  private JComboBox myAdbServerMdnsBackend;
  private HyperlinkLabel myAdbServerMdnsBackendLabel;
  private HyperlinkLabel myAdbServerLifecycleLabel;

  @Override
  public boolean isModified(@NotNull AdbOptionsService settings) {
    return getAdbServerUsbBackend() != settings.getAdbServerUsbBackend()
           || getAdbServerMdnsBackend() != settings.getAdbServerMdnsBackend()
           || myUseExistingManuallyManagedServerRadioButton.isSelected() != settings.shouldUseUserManagedAdb()
           || getUserManagedAdbPortNumber() != settings.getUserManagedAdbPort();
  }

  @Override
  public void reset(@NotNull AdbOptionsService settings) {
    setAdbServerUsbBackend(settings.getAdbServerUsbBackend());
    if (settings.shouldUseUserManagedAdb()) {
      myUseExistingManuallyManagedServerRadioButton.setSelected(true);
    }
    else {
      myAutomaticallyStartAndManageServerRadioButton.setSelected(true);
    }
    setAdbServerMdnsBackend(settings.getAdbServerMdnsBackend());
    myExistingAdbServerPortSpinner.setValue(settings.getUserManagedAdbPort());
    setPortNumberUiEnabled(settings.shouldUseUserManagedAdb());
  }

  @Override
  public void apply(@NotNull AdbOptionsService settings) throws ConfigurationException {
    settings.getOptionsUpdater()
      .setAdbServerUsbBackend(getAdbServerUsbBackend())
      .setUseUserManagedAdb(myUseExistingManuallyManagedServerRadioButton.isSelected())
      .setAdbServerMdnsBackend(getAdbServerMdnsBackend())
      .setUserManagedAdbPort(getUserManagedAdbPortNumber())
      .commit();
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    myAdbServerUsbBackend.setModel(new DefaultComboBoxModel(AdbServerUsbBackend.values()));
    myAdbServerUsbBackendLabel.setHyperlinkText("ADB server USB backend (", "See support list", ")");
    myAdbServerUsbBackendLabel.setHyperlinkTarget("https://developer.android.com/tools/adb#backends");
    myAdbServerUsbBackendLabel.setIcon(null);

    myAdbServerMdnsBackend.setModel(new DefaultComboBoxModel(AdbServerMdnsBackend.values()));
    myAdbServerMdnsBackendLabel.setHyperlinkText("ADB server mDNS backend (", "See support list", ")");
    myAdbServerMdnsBackendLabel.setHyperlinkTarget("https://developer.android.com/tools/adb#mdnsBackends");
    myAdbServerMdnsBackendLabel.setIcon(null);

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
    myAdbServerUsbBackend = new com.intellij.openapi.ui.ComboBox<>();
    myAdbServerMdnsBackend = new com.intellij.openapi.ui.ComboBox<>();
  }

  private void setPortNumberUiEnabled(boolean enabled) {
    myExistingAdbServerPortSpinner.setEnabled(enabled);
    myExistingAdbServerPortLabel.setEnabled(enabled);
  }

  private int getUserManagedAdbPortNumber() {
    return myExistingAdbServerPortSpinner.getNumber();
  }


  void setAdbServerMdnsBackend(AdbServerMdnsBackend backend) {
    myAdbServerMdnsBackend.setSelectedItem(backend);
  }

  AdbServerMdnsBackend getAdbServerMdnsBackend() {
    return AdbServerMdnsBackend.fromDisplayText(myAdbServerMdnsBackend.getSelectedItem().toString());
  }

  void setAdbServerUsbBackend(AdbServerUsbBackend backend) {
    myAdbServerUsbBackend.setSelectedItem(backend);
  }

  AdbServerUsbBackend getAdbServerUsbBackend() {
    return AdbServerUsbBackend.fromDisplayText(myAdbServerUsbBackend.getSelectedItem().toString());
  }
}
