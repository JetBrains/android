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
import com.android.tools.idea.deviceManager.displayList.columns.AvdDeviceColumnInfo;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.table.TableCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("NotNullFieldNotInitialized")
public class AddDevicesToGroupPanel {
  private @NotNull JPanel myRootComponent;
  private @NotNull JBTable myAvailableTable;
  private @NotNull JBTable myGroupTable;
  private @NotNull JButton myAddButton;
  private @NotNull JButton myRemoveButton;

  // TODO(b/174518066): create custom table model instead of using ListTableModel
  private @NotNull ListTableModel<@NotNull AvdInfo> myAvailableTableModel; // TODO: need to support physical devices too
  private @NotNull ListTableModel<@NotNull AvdInfo> myGroupTableModel; // TODO: need to support physical devices too
  private @NotNull TableCellRenderer myTableCellRenderer;

  AddDevicesToGroupPanel() {
    myAddButton.addActionListener(event -> onAddButtonClicked());
    myRemoveButton.addActionListener(event -> onRemoveButtonClicked());
  }

  public @NotNull JPanel getComponent() {
    return myRootComponent;
  }

  private void createUIComponents() {
    myTableCellRenderer = AvdDeviceColumnInfo.Companion.getStaticRenderer();

    createAvailableTable();
    createGroupTable();
  }

  private void createAvailableTable() {
    myAvailableTableModel = new ListTableModel<>();
    // TODO(b/174518417): call this on a background thread
    myAvailableTableModel.addRows(AvdManagerConnection.getDefaultAvdManagerConnection().getAvds(true));
    myAvailableTable = new JBTable(myAvailableTableModel);
    myAvailableTableModel.setColumnInfos(createColumns().toArray(new ColumnInfo[0]));
    myAvailableTable.setDefaultRenderer(AvdInfo.class, myTableCellRenderer);
  }

  private void createGroupTable() {
    myGroupTableModel = new ListTableModel<>();
    myGroupTable = new JBTable(myGroupTableModel);
    myGroupTableModel.setColumnInfos(createColumns().toArray(new ColumnInfo[0]));
    myGroupTable.setDefaultRenderer(AvdInfo.class, myTableCellRenderer);
  }

  private @NotNull List<@NotNull ColumnInfo<@Nullable AvdInfo, @Nullable AvdInfo>> createColumns() {
    List<ColumnInfo<AvdInfo, AvdInfo>> devices = new ArrayList<>();

    devices.add(new AvdDeviceColumnInfo("Device", 70));
    // TODO: add "Type" column

    return devices;
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
    AvdInfo avdInfo = myAvailableTableModel.getRowValue(modelRowIndex);
    myAvailableTableModel.removeRow(modelRowIndex);
    myGroupTableModel.addRow(avdInfo);
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
    AvdInfo avdInfo = myGroupTableModel.getRowValue(modelRowIndex);
    myGroupTableModel.removeRow(modelRowIndex);
    myAvailableTableModel.addRow(avdInfo);
  }
}
