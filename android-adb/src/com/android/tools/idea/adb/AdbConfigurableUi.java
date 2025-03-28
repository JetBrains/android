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
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.JBUI;
import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.border.TitledBorder;
import org.jetbrains.annotations.NotNull;

public class AdbConfigurableUi implements ConfigurableUi<AdbOptionsService> {
  private JPanel myPanel;
  private JBLabel myExistingAdbServerPortLabel;
  private JBIntSpinner myExistingAdbServerPortSpinner;
  private JRadioButton myAutomaticallyStartAndManageServerRadioButton;
  private JRadioButton myUseExistingManuallyManagedServerRadioButton;
  private JComboBox<String> myAdbServerUsbBackend;
  private JComboBox myAdbServerMdnsBackend;
  private JComboBox myAdbServerBurstMode;
  private JCheckBox myEnableADBServerLogs;

  public AdbConfigurableUi() {
    setupUI();
  }

  @Override
  public boolean isModified(@NotNull AdbOptionsService settings) {
    return getAdbServerUsbBackend() != settings.getAdbServerUsbBackend()
           || getAdbServerMdnsBackend() != settings.getAdbServerMdnsBackend()
           || myUseExistingManuallyManagedServerRadioButton.isSelected() != settings.shouldUseUserManagedAdb()
           || getUserManagedAdbPortNumber() != settings.getUserManagedAdbPort()
           || getAdbServerBurstMode() != settings.getAdbServerBurstMode()
           || getAdbServerLogsEnabled() != settings.getAdbServerLogsEnabled();
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
    setAdbServerBurstMode(settings.getAdbServerBurstMode());
    setAdbServerLogsEnabled(settings.getAdbServerLogsEnabled());
  }

  @Override
  public void apply(@NotNull AdbOptionsService settings) throws ConfigurationException {
    settings.getOptionsUpdater()
      .setAdbServerUsbBackend(getAdbServerUsbBackend())
      .setUseUserManagedAdb(myUseExistingManuallyManagedServerRadioButton.isSelected())
      .setAdbServerMdnsBackend(getAdbServerMdnsBackend())
      .setUserManagedAdbPort(getUserManagedAdbPortNumber())
      .setBurstMode(getAdbServerBurstMode())
      .setAdbServerLogsEnabled(getAdbServerLogsEnabled())
      .commit();
  }

  @NotNull
  @Override
  public JComponent getComponent() {
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
    ButtonGroup buttonGroup;
    buttonGroup = new ButtonGroup();
    buttonGroup.add(myAutomaticallyStartAndManageServerRadioButton);
    buttonGroup.add(myUseExistingManuallyManagedServerRadioButton);
    myAdbServerUsbBackend = new com.intellij.openapi.ui.ComboBox<>();
    myAdbServerMdnsBackend = new com.intellij.openapi.ui.ComboBox<>();
    myAdbServerBurstMode = new com.intellij.openapi.ui.ComboBox<>();
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

  void setAdbServerBurstMode(AdbServerBurstMode burstMode) {
    myAdbServerBurstMode.setSelectedItem(burstMode);
  }

  AdbServerBurstMode getAdbServerBurstMode() {
    return AdbServerBurstMode.fromDisplayText(myAdbServerBurstMode.getSelectedItem().toString());
  }

  private void setupUI() {
    createUIComponents();
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(6, 6, new Insets(0, 0, 0, 0), -1, -1));

    myPanel.add(new Spacer(), new GridConstraints(0, 5, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                             GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));


    HyperlinkLabel adbServerUsbBackendLabel = new HyperlinkLabel();
    adbServerUsbBackendLabel.setToolTipText("");
    adbServerUsbBackendLabel.setHyperlinkText("ADB server USB backend (", "Support list", ")");
    adbServerUsbBackendLabel.setHyperlinkTarget("https://developer.android.com/tools/adb#backends");
    adbServerUsbBackendLabel.setIcon(null);
    myPanel.add(adbServerUsbBackendLabel,
                new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));

    myAdbServerUsbBackend.setModel(new DefaultComboBoxModel(AdbServerUsbBackend.values()));
    myPanel.add(myAdbServerUsbBackend, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                           GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                           null, 0, false));


    HyperlinkLabel adbServerMdnsBackendLabel = new HyperlinkLabel();
    adbServerMdnsBackendLabel.setHyperlinkText("ADB server mDNS backend (", "Support list", ")");
    adbServerMdnsBackendLabel.setHyperlinkTarget("https://developer.android.com/tools/adb#mdnsBackends");
    adbServerMdnsBackendLabel.setIcon(null);
    myPanel.add(adbServerMdnsBackendLabel,
                new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(4, 38), null, 0, false));

    myAdbServerMdnsBackend.setModel(new DefaultComboBoxModel(AdbServerMdnsBackend.values()));
    myPanel.add(myAdbServerMdnsBackend, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                            GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                            new Dimension(83, 38), null, 0, false));


    HyperlinkLabel adbServerBurstModeLabel = new HyperlinkLabel();
    adbServerBurstModeLabel.setHyperlinkText("ADB server Burst Mode (", "Support list", ")");
    adbServerBurstModeLabel.setHyperlinkTarget("https://developer.android.com/tools/adb#burstMode");
    adbServerBurstModeLabel.setIcon(null);
    myPanel.add(adbServerBurstModeLabel,
                new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(4, 38), null, 0, false));

    myAdbServerBurstMode.setModel(new DefaultComboBoxModel(AdbServerBurstMode.values()));
    myPanel.add(myAdbServerBurstMode, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                          GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                          new Dimension(83, 38), null, 0, false));






    myEnableADBServerLogs = new JCheckBox();
    myEnableADBServerLogs.setText("Enable ADB server logs");
    myPanel.add(myEnableADBServerLogs,
                new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(4, 38), null, 0, false));


    final JPanel lifeCyclePanel = new JPanel();
    lifeCyclePanel.setBorder(IdeBorderFactory.PlainSmallWithIndent.createTitledBorder(BorderFactory.createEtchedBorder(), "Adb Server Lifecycle Management",
                                                                              TitledBorder.DEFAULT_JUSTIFICATION,
                                                                              TitledBorder.DEFAULT_POSITION, null, null));

    myPanel.add(lifeCyclePanel, new GridConstraints(4, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
                                            0, false));

    lifeCyclePanel.setLayout(new GridLayoutManager(4, 5, JBUI.emptyInsets(), -1, -1));

    myAutomaticallyStartAndManageServerRadioButton.setText("Automatically start and manage server");
    lifeCyclePanel.add(myAutomaticallyStartAndManageServerRadioButton,
               new GridConstraints(0, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myUseExistingManuallyManagedServerRadioButton.setEnabled(true);
    myUseExistingManuallyManagedServerRadioButton.setText("Use existing manually managed server");
    lifeCyclePanel.add(myUseExistingManuallyManagedServerRadioButton,
               new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myExistingAdbServerPortLabel.setText("Existing ADB server port:");
    lifeCyclePanel.add(myExistingAdbServerPortLabel,
               new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 2, false));

    myExistingAdbServerPortSpinner.setMax(65535);
    myExistingAdbServerPortSpinner.setMin(5038);
    myExistingAdbServerPortSpinner.setNumber(5038);
    myExistingAdbServerPortSpinner.setOpaque(false);
    lifeCyclePanel.add(myExistingAdbServerPortSpinner, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                                   GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                   null, null, null, 0, false));
  }

  void setAdbServerLogsEnabled(boolean enabled) {
    myEnableADBServerLogs.setSelected(enabled);
  }

  boolean getAdbServerLogsEnabled() {
    return myEnableADBServerLogs.isSelected();
  }
}
