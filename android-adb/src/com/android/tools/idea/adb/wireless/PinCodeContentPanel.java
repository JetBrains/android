/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.adb.wireless;

import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class PinCodeContentPanel {
  @NotNull private JPanel myRootComponent;
  @NotNull private JPanel myEmptyPanel;
  @NotNull private JPanel myDevicesPanel;
  @NotNull private JPanel myDevicesHeaderPanel;
  @NotNull private JPanel myDeviceList;
  @NotNull private JBScrollPane myDeviceListScrollPane;

  public PinCodeContentPanel() {
    myDeviceList.setLayout(new VerticalFlowLayout());

    myEmptyPanel.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myDevicesHeaderPanel.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myDeviceListScrollPane.setBorder(JBUI.Borders.empty());
    myDeviceList.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    EditorPaneUtils.setTitlePanelBorder(myDevicesHeaderPanel, 0);
  }

  @NotNull
  public JComponent getComponent() {
    return myRootComponent;
  }

  public void showDevices(@NotNull List<@NotNull MdnsService> devices) {
    if (devices.isEmpty()) {
      myEmptyPanel.setVisible(true);
      myDevicesPanel.setVisible(false);
      myDeviceList.removeAll();
    } else {
      myEmptyPanel.setVisible(false);
      myDevicesPanel.setVisible(true);
      //TODO: Implement "diff'ing" with previous list, so that UI order is preserved
      //      when devices appear/disappear
      myDeviceList.removeAll();
      for (MdnsService device : devices) {
        PinCodeDevicePanel devicePanel = new PinCodeDevicePanel();
        devicePanel.setDevice(device);
        myDeviceList.add(devicePanel.getComponent());
      }
    }
  }

  private void createUIComponents() {
    myDeviceListScrollPane = new JBScrollPane(0);
  }
}
