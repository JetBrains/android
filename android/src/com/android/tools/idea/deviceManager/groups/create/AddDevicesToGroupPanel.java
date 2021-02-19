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
package com.android.tools.idea.deviceManager.groups.create;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.deviceManager.groups.GroupableDevice;
import com.intellij.ui.table.JBTable;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("NotNullFieldNotInitialized")
public class AddDevicesToGroupPanel {
  private @NotNull JPanel myRootComponent;
  private @NotNull JBTable myAvailableTable;
  private @NotNull JBTable myGroupTable;
  private @NotNull JButton myAddButton;
  private @NotNull JButton myRemoveButton;

  private @NotNull GroupableDeviceTableModel myAvailableTableModel; // TODO: need to support physical devices too
  private @NotNull GroupableDeviceTableModel myGroupTableModel; // TODO: need to support physical devices too

  AddDevicesToGroupPanel() {
    myAddButton.addActionListener(event -> onAddButtonClicked());
    myRemoveButton.addActionListener(event -> onRemoveButtonClicked());
  }

  public @NotNull JPanel getComponent() {
    return myRootComponent;
  }

  private void createUIComponents() {
    createAvailableTable();
    createGroupTable();
  }

  private void createAvailableTable() {
    // TODO(b/174518417): call this on a background thread
    List<AvdInfo> avds = AvdManagerConnection.getDefaultAvdManagerConnection().getAvds(true);
    myAvailableTableModel = new GroupableDeviceTableModel(avds.stream().map(GroupableDevice::new).collect(Collectors.toList()));
    myAvailableTable = new JBTable(myAvailableTableModel);
    myAvailableTable.setDefaultRenderer(GroupableDevice.class, new GroupableDeviceTableCellRenderer());
    myAvailableTable.getColumnModel().getColumn(0).setPreferredWidth(220);
    myAvailableTable.getColumnModel().getColumn(1).setPreferredWidth(80);
  }

  private void createGroupTable() {
    myGroupTableModel = new GroupableDeviceTableModel();
    myGroupTable = new JBTable(myGroupTableModel);
    myGroupTable.setDefaultRenderer(GroupableDevice.class, new GroupableDeviceTableCellRenderer());
    myGroupTable.getColumnModel().getColumn(0).setPreferredWidth(220);
    myGroupTable.getColumnModel().getColumn(1).setPreferredWidth(80);
  }

  /**
   * When the "Add >" button is clicked, move the left table's selected item to the right table
   */
  private void onAddButtonClicked() {
    int viewRowIndex = myAvailableTable.getSelectedRow();
    if (viewRowIndex == -1) {
      return;
    }
    int modelRowIndex = myAvailableTable.convertRowIndexToModel(viewRowIndex);
    GroupableDevice device = myAvailableTableModel.getDeviceAt(modelRowIndex);
    myAvailableTableModel.removeRow(modelRowIndex);
    myGroupTableModel.addDevice(device);
  }

  /**
   * When the "< Remove" button is clicked, move the right table's selected item to the left table
   */
  private void onRemoveButtonClicked() {
    int viewRowIndex = myGroupTable.getSelectedRow();
    if (viewRowIndex == -1) {
      return;
    }
    int modelRowIndex = myGroupTable.convertRowIndexToModel(viewRowIndex);
    GroupableDevice device = myGroupTableModel.getDeviceAt(modelRowIndex);
    myGroupTableModel.removeRow(modelRowIndex);
    myAvailableTableModel.addDevice(device);
  }

  @NotNull List<@NotNull GroupableDevice> getGroupableDevices() {
    return myGroupTableModel.getGroupableDevices();
  }
}
