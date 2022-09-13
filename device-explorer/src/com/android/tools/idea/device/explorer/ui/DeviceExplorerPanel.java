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
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import java.awt.Color;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeviceExplorerPanel {
  private JComboBox myDeviceCombo;
  private JPanel myComponent;
  private JPanel errorPanel;
  private JLabel errorText;
  private JTabbedPane tabPane;

  public DeviceExplorerPanel() {
    errorPanel.setBackground(UIUtil.getTreeBackground());
    errorText.setFont(AdtUiUtils.EMPTY_TOOL_WINDOW_FONT);
    errorText.setForeground(NamedColorUtil.getInactiveTextColor());
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
}
