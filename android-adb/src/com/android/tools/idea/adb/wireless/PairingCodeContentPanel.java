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

import com.android.annotations.concurrency.UiThread;
import com.android.utils.TraceUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.JBUI;
import java.awt.Component;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * Form that displays the list of devices available to pair via pairing code.
 * If there are no devices available, a custom icon and text are displayed.
 */
@UiThread
public class PairingCodeContentPanel {
  @NotNull private JPanel myRootComponent;
  @NotNull private JPanel myEmptyPanel;
  @NotNull private JPanel myDevicesPanel;
  @NotNull private JPanel myDeviceList;
  @NotNull private JBScrollPane myDeviceListScrollPane;
  private JBLabel myDeviceLineupLabel;
  @NotNull List<PairingCodeDevicePanel> myPanels = new ArrayList<>();

  public PairingCodeContentPanel() {
    setupUI();
    myDeviceList.setLayout(new VerticalFlowLayout());

    myEmptyPanel.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
    myDeviceListScrollPane.setBorder(JBUI.Borders.empty());
    myDeviceList.setBackground(UIColors.PAIRING_CONTENT_BACKGROUND);
  }

  @NotNull
  public JComponent getComponent() {
    return myRootComponent;
  }

  public void showDevices(@NotNull List<MdnsService> services, @NotNull Consumer<MdnsService> pairingCodePairInvoked) {
    if (services.isEmpty()) {
      myEmptyPanel.setVisible(true);
      myDevicesPanel.setVisible(false);
      myDeviceList.removeAll();
      myDeviceList.revalidate();
      myDeviceList.repaint();
      myPanels.clear();
    } else {
      myEmptyPanel.setVisible(false);
      myDevicesPanel.setVisible(true);
      boolean needRepaint = false;

      // Keep existing panels & add add new panels for new devices
      for (MdnsService service : services) {
        if (isPanelPresent(myPanels, service)) {
          continue;
        }
        PairingCodeDevicePanel devicePanel = new PairingCodeDevicePanel(service, () -> pairingCodePairInvoked.accept(service));
        myDeviceList.add(devicePanel.getComponent());
        myPanels.add(devicePanel);
        needRepaint = true;
      }

      // Remove panels for devices that are gone
      List<Integer> indicesToRemove = new ArrayList<>();
      int index = 0;
      for (PairingCodeDevicePanel panel : myPanels) {
        if (isPanelDeleted(services, panel)) {
          indicesToRemove.add(index);
        }
        index++;
      }
      for (int i = indicesToRemove.size() - 1; i >= 0; i--) {
        int indexToRemove = indicesToRemove.get(i);
        myPanels.remove(indexToRemove);
        myDeviceList.remove(indexToRemove);
        needRepaint = true;
      }

      if (needRepaint) {
        myDeviceList.revalidate();
        myDeviceList.repaint();
      }

      if (!checkConsistency(services)) {
        Logger.getInstance(PairingCodeContentPanel.class).error("Detected internal inconsistency:\n" + getDataStructuresDump(services));
      }
    }
  }

  /** Checks consistency between services, myPanels and myDeviceList. */
  private boolean checkConsistency(@NotNull List<MdnsService> services) {
    if (myPanels.size() != myDeviceList.getComponentCount() || services.size() != myDeviceList.getComponentCount()) {
      return false;
    }
    for (int i = 0; i < myPanels.size(); i++) {
      // Reference equality (since components are always updated together)
      if (myPanels.get(i).getComponent() != myDeviceList.getComponent(i)) {
        return false;
      }
      // Value equality (since service instances are coming from external source)
      if (!myPanels.get(i).getMdnsService().equals(services.get(i))) {
        return false;
      }
    }
    return true;
  }

  /** Dumps contents of services, myPanels and subcomponents of myDeviceList to a string. */
  private String getDataStructuresDump(@NotNull List<MdnsService> services) {
    StringBuilder buf = new StringBuilder();
    buf.append("services:");
    String separator = " ";
    for (MdnsService service : services) {
      buf.append(separator);
      buf.append(service);
      separator = ", ";
    }
    buf.append("\npanels:");
    separator = " ";
    for (PairingCodeDevicePanel panel : myPanels) {
      buf.append(separator);
      buf.append("(");
      buf.append(panel.getMdnsService());
      buf.append(" ");
      buf.append(TraceUtils.getSimpleId(panel.getComponent()));
      buf.append(")");
      separator = ", ";
    }
    buf.append("\ndevice components:");
    separator = " ";
    for (Component component : myDeviceList.getComponents()) {
      buf.append(separator);
      buf.append(TraceUtils.getSimpleId(component));
      separator = ", ";
    }

    return buf.toString();
  }

  private void setupUI() {
    createUIComponents();
    myRootComponent = new JPanel();
    myRootComponent.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), 0, 0));
    myDevicesPanel = new JPanel();
    myDevicesPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), 0, 0));
    myRootComponent.add(myDevicesPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            null, null, null, 0, false));
    myDevicesPanel.add(myDeviceListScrollPane, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                   GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                   GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    myDeviceList = new JPanel();
    myDeviceList.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    myDeviceListScrollPane.setViewportView(myDeviceList);
    myEmptyPanel = new JPanel();
    myEmptyPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), 0, 0));
    myRootComponent.add(myEmptyPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                          null, null, 0, false));
    myDeviceLineupLabel = new JBLabel();
    myDeviceLineupLabel.setHorizontalTextPosition(0);
    myDeviceLineupLabel.setIconTextGap(0);
    myDeviceLineupLabel.setText("Searching for devices...");
    myDeviceLineupLabel.setVerticalTextPosition(3);
    myEmptyPanel.add(myDeviceLineupLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                                              GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                              null, null, 0, false));
  }

  private static boolean isPanelDeleted(@NotNull List<MdnsService> services, @NotNull PairingCodeDevicePanel panel) {
    //TODO: Add test that MdnsService implements value equality
    return services.stream().noneMatch(service -> service.equals(panel.getMdnsService()));
  }

  private static boolean isPanelPresent(@NotNull List<PairingCodeDevicePanel> panels,
                                        @NotNull MdnsService device) {
    //TODO: Add test that MdnsService implements value equality
    return panels.stream().anyMatch(panel -> panel.getMdnsService().equals(device));
  }

  private void createUIComponents() {
    myDeviceListScrollPane = new JBScrollPane(0);
  }
}
