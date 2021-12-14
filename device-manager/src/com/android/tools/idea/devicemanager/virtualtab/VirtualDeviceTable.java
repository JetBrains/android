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
import com.android.tools.idea.avdmanager.ApiLevelComparator;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.DeviceManagerUsageTracker;
import com.android.tools.idea.devicemanager.DeviceTable;
import com.android.tools.idea.devicemanager.Table;
import com.android.tools.idea.devicemanager.Tables;
import com.android.tools.idea.devicemanager.legacy.AvdActionPanel.AvdRefreshProvider;
import com.android.tools.idea.devicemanager.legacy.AvdUiAction.AvdInfoProvider;
import com.android.tools.idea.devicemanager.legacy.CreateAvdAction;
import com.android.tools.idea.devicemanager.virtualtab.VirtualDeviceTableModel.Actions;
import com.android.tools.idea.devicemanager.virtualtab.VirtualDeviceTableModel.LaunchInEmulatorValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.scale.JBUIScale;
import java.awt.Point;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.swing.ActionMap;
import javax.swing.DefaultRowSorter;
import javax.swing.JComponent;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class VirtualDeviceTable extends DeviceTable<VirtualDevice> implements Table, AvdRefreshProvider, AvdInfoProvider {
  private final @NotNull VirtualDevicePanel myPanel;
  private final @NotNull Supplier<@NotNull List<@NotNull AvdInfo>> myGetAvds;
  private final @NotNull Predicate<@NotNull AvdInfo> myIsAvdRunning;

  VirtualDeviceTable(@NotNull VirtualDevicePanel panel) {
    this(panel,
         new VirtualDeviceTableModel(),
         () -> AvdManagerConnection.getDefaultAvdManagerConnection().getAvds(true),
         AvdManagerConnection.getDefaultAvdManagerConnection()::isAvdRunning
    );
  }

  @VisibleForTesting
  VirtualDeviceTable(@NotNull VirtualDevicePanel panel,
                     @NotNull VirtualDeviceTableModel model,
                     @NotNull Supplier<@NotNull List<@NotNull AvdInfo>> getAvds,
                     @NotNull Predicate<@NotNull AvdInfo> isAvdRunning) {
    super(model, VirtualDevice.class, VirtualDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX);

    myPanel = panel;
    myGetAvds = getAvds;
    myIsAvdRunning = isAvdRunning;

    model.addTableModelListener(event -> sizeWidthsToFit());

    if (VirtualDeviceTableModel.SPLIT_ACTIONS_ENABLED) {
      setDefaultEditor(LaunchInEmulatorValue.class, new LaunchInEmulatorButtonTableCellEditor(panel.getProject()));
      setDefaultRenderer(LaunchInEmulatorValue.class, new LaunchInEmulatorButtonTableCellRenderer());
    }
    else {
      setDefaultEditor(Actions.class, new ActionsTableCell(this));
      setDefaultRenderer(Actions.class, new ActionsTableCell(this));
    }

    setDefaultRenderer(Device.class, new VirtualDeviceTableCellRenderer());
    setRowSorter(newRowSorter(model));
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setShowGrid(false);

    if (!VirtualDeviceTableModel.SPLIT_ACTIONS_ENABLED) {
      ActionMap map = getActionMap();

      map.put("selectNextColumn", new SelectNextColumnAction());
      map.put("selectNextColumnCell", new SelectNextColumnCellAction());
      map.put("selectNextRow", new SelectNextRowAction());
      map.put("selectPreviousColumn", new SelectPreviousColumnAction());
      map.put("selectPreviousColumnCell", new SelectPreviousColumnCellAction());
      map.put("selectPreviousRow", new SelectPreviousRowAction());
    }

    // noinspection DialogTitleCapitalization
    getEmptyText()
      .appendLine("No virtual devices added. Create a virtual device to test")
      .appendLine("applications without owning a physical device.")
      .appendLine("Create virtual device", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, new CreateAvdAction(this));

    tableHeader.setReorderingAllowed(false);
    tableHeader.setResizingAllowed(false);

    refreshAvds();
  }

  private void sizeWidthsToFit() {
    getRowSorter().allRowsChanged();
    columnModel.getColumn(deviceViewColumnIndex()).setMinWidth(JBUIScale.scale(65));

    Tables.setWidths(columnModel.getColumn(apiViewColumnIndex()),
                     Tables.getPreferredColumnWidth(this, apiViewColumnIndex(), JBUIScale.scale(65)),
                     JBUIScale.scale(20));

    Tables.setWidths(columnModel.getColumn(sizeOnDiskViewColumnIndex()),
                     Tables.getPreferredColumnWidth(this, sizeOnDiskViewColumnIndex(), JBUIScale.scale(65)),
                     JBUIScale.scale(20));

    Tables.setWidths(columnModel.getColumn(actionsViewColumnIndex()),
                     Tables.getPreferredColumnWidth(this, actionsViewColumnIndex(), JBUIScale.scale(65)));
  }

  private static @NotNull RowSorter<@NotNull TableModel> newRowSorter(@NotNull TableModel model) {
    DefaultRowSorter<TableModel, Integer> sorter = new TableRowSorter<>(model);

    sorter.setComparator(VirtualDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX, Comparator.comparing(VirtualDevice::getName));
    sorter.setComparator(VirtualDeviceTableModel.API_MODEL_COLUMN_INDEX, new ApiLevelComparator().reversed());
    sorter.setComparator(VirtualDeviceTableModel.SIZE_ON_DISK_MODEL_COLUMN_INDEX, Comparator.naturalOrder().reversed());
    sorter.setSortable(VirtualDeviceTableModel.ACTIONS_MODEL_COLUMN_INDEX, false);
    sorter.setSortKeys(Collections.singletonList(new SortKey(VirtualDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX, SortOrder.ASCENDING)));

    return sorter;
  }

  @NotNull VirtualDevicePanel getPanel() {
    return myPanel;
  }

  @Override
  public @NotNull VirtualDeviceTableModel getModel() {
    return (VirtualDeviceTableModel)dataModel;
  }

  @Override
  public boolean isActionsColumn(int viewColumnIndex) {
    return convertColumnIndexToModel(viewColumnIndex) == VirtualDeviceTableModel.ACTIONS_MODEL_COLUMN_INDEX;
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
    return getSelectedDevice().map(VirtualDevice::getAvdInfo).orElse(null);
  }

  @NotNull Optional<@NotNull VirtualDevice> getSelectedDevice() {
    int viewRowIndex = getSelectedRow();

    if (viewRowIndex == -1) {
      return Optional.empty();
    }

    return Optional.of(getDeviceAt(viewRowIndex));
  }

  private int apiViewColumnIndex() {
    return convertColumnIndexToView(VirtualDeviceTableModel.API_MODEL_COLUMN_INDEX);
  }

  int sizeOnDiskViewColumnIndex() {
    return convertColumnIndexToView(VirtualDeviceTableModel.SIZE_ON_DISK_MODEL_COLUMN_INDEX);
  }

  int actionsViewColumnIndex() {
    return convertColumnIndexToView(VirtualDeviceTableModel.ACTIONS_MODEL_COLUMN_INDEX);
  }

  @Override
  public void refreshAvds() {
    List<VirtualDevice> devices = myGetAvds.get().stream()
      .map(avdInfo -> VirtualDevices.build(avdInfo, myIsAvdRunning))
      .collect(Collectors.toList());

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
    return myPanel.getProject();
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
