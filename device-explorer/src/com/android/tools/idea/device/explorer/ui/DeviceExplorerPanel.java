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
package com.android.tools.idea.device.explorer.ui;

import com.android.sdklib.deviceprovisioner.DeviceHandle;
import com.android.tools.adtui.common.AdtUiUtils;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.border.TitledBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeviceExplorerPanel {
  private JComboBox myDeviceCombo;
  private JPanel myComponent;
  private JPanel errorPanel;
  private JLabel errorText;
  private JTabbedPane tabPane;

  public DeviceExplorerPanel() {
    setupUI();
    errorPanel.setBackground(UIUtil.getTreeBackground());
    errorText.setFont(AdtUiUtils.EMPTY_TOOL_WINDOW_FONT);
    errorText.setForeground(NamedColorUtil.getInactiveTextColor());
    errorText.setIconTextGap(16);
  }

  @NotNull
  public JPanel getComponent() {
    return myComponent;
  }

  @NotNull
  public JComboBox<DeviceHandle> getDeviceCombo() {
    //noinspection unchecked
    return myDeviceCombo;
  }

  @NotNull
  public JTabbedPane getTabPane() {
    //noinspection unchecked
    return tabPane;
  }

  public void showTabs() {
    myDeviceCombo.setVisible(true);
    tabPane.setVisible(true);
    errorPanel.setVisible(false);
    errorText.setText("");
  }

  public void showMessageLayer(@NotNull String message, @Nullable Icon icon, boolean showDeviceList) {
    showMessageLayerWorker(message, NamedColorUtil.getInactiveTextColor(), icon, showDeviceList);
  }

  private void showMessageLayerWorker(@NotNull String message, @NotNull Color color, @Nullable Icon icon, boolean showDeviceList) {
    errorText.setForeground(color);
    errorText.setIcon(icon);
    myDeviceCombo.setVisible(showDeviceList);
    // Note: In addition to having the label centered in the panel, we want the text
    // to wrap ("html") and the wrapped lines to be centered as well ("text-align").
    String htmlText = String.format("<html><div style='text-align: center;'>%s</div></html>",
                                    StringUtil.escapeXml(message));
    errorText.setText(htmlText);
    errorPanel.setVisible(true);
    tabPane.setVisible(false);
  }

  private void setupUI() {
    myComponent = new JPanel();
    myComponent.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), 1, 1));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    myComponent.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myDeviceCombo = new JComboBox();
    panel1.add(myDeviceCombo, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                  GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JPanel panel2 = new JPanel();
    panel2.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
    myComponent.add(panel2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null,
                                                null, 0, false));
    errorPanel = new JPanel();
    errorPanel.setLayout(new BorderLayout(0, 0));
    errorPanel.setVisible(false);
    panel2.add(errorPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                               null, 0, false));
    errorPanel.setBorder(
      BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(120, 10, 0, 10), null, TitledBorder.DEFAULT_JUSTIFICATION,
                                       TitledBorder.DEFAULT_POSITION, null, null));
    errorText = new JLabel();
    errorText.setHorizontalAlignment(0);
    errorText.setHorizontalTextPosition(0);
    errorText.setMaximumSize(new Dimension(0, 0));
    errorText.setMinimumSize(new Dimension(0, 0));
    errorText.setPreferredSize(new Dimension(0, 0));
    errorText.setText("");
    errorText.setVerticalAlignment(1);
    errorText.setVerticalTextPosition(3);
    errorPanel.add(errorText, BorderLayout.CENTER);
    tabPane = new JTabbedPane();
    panel2.add(tabPane, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                            new Dimension(200, 200), null, 0, false));
  }
}
