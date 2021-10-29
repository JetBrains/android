/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdActionPanel.AvdRefreshProvider;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.avdmanager.AvdUiAction.AvdInfoProvider;
import com.android.tools.idea.avdmanager.CreateAvdAction;
import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.DeviceManagerUsageTracker;
import com.android.tools.idea.devicemanager.Table;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.table.JBTable;
import java.awt.Point;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.ListSelectionModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class VirtualDeviceTable extends JBTable implements Table, AvdRefreshProvider, AvdInfoProvider {
  VirtualDeviceTable() {
    super(new VirtualDeviceTableModel());

    setDefaultRenderer(Device.class, new VirtualDeviceTableCellRenderer());
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setShowGrid(false);

    // noinspection DialogTitleCapitalization
    getEmptyText()
      .appendLine("No virtual devices added. Create a virtual device to test")
      .appendLine("applications without owning a physical device.")
      .appendLine("Create virtual device", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, new CreateAvdAction(this));

    tableHeader.setReorderingAllowed(false);
    tableHeader.setResizingAllowed(false);

    refreshAvds();
  }

  @Override
  public @NotNull VirtualDeviceTableModel getModel() {
    return (VirtualDeviceTableModel)dataModel;
  }

  @Override
  public boolean isActionsColumn(int viewColumnIndex) {
    return false; //TODO
  }

  @Override
  public int viewRowIndexAtPoint(@NotNull Point point) {
    return rowAtPoint(point);
  }

  @Override
  public int viewColumnIndexAtPoint(@NotNull Point point) {
    return columnAtPoint(point);
  }

  @Override
  public int getEditingViewRowIndex() {
    return editingRow;
  }

  @Override
  public @Nullable AvdInfo getAvdInfo() {
    return getSelectedDevice().orElse(null);
  }

  private @NotNull Optional<@NotNull AvdInfo> getSelectedDevice() {
    int viewRowIndex = getSelectedRow();

    if (viewRowIndex == -1) {
      return Optional.empty();
    }

    return Optional.of(getDeviceAt(viewRowIndex));
  }

  private @NotNull AvdInfo getDeviceAt(int viewRowIndex) {
    return (AvdInfo)getValueAt(viewRowIndex, convertColumnIndexToView(VirtualDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX));
  }

  @Override
  public void refreshAvds() {
    List<AvdInfo> devices = AvdManagerConnection.getDefaultAvdManagerConnection().getAvds(true);
    getModel().setDevices(devices);

    DeviceManagerEvent event = DeviceManagerEvent.newBuilder()
      .setKind(DeviceManagerEvent.EventKind.VIRTUAL_DEVICE_COUNT)
      .setVirtualDeviceCount(devices.size())
      .build();

    DeviceManagerUsageTracker.log(event);
  }

  @Override
  public void refreshAvdsAndSelect(@Nullable AvdInfo device) {
    refreshAvds();

    //changeSelection(); TODO
  }

  @Override
  public @Nullable Project getProject() {
    return null; // TODO
  }

  @Override
  public @NotNull JComponent getAvdProviderComponent() {
    return this;
  }

  @Override
  public @NotNull JComponent getComponent() {
    return this;
  }
}
