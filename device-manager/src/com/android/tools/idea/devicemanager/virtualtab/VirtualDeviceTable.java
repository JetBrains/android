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

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.devicemanager.ActivateDeviceFileExplorerWindowButtonTableCellEditor;
import com.android.tools.idea.devicemanager.ActivateDeviceFileExplorerWindowButtonTableCellRenderer;
import com.android.tools.idea.devicemanager.ActivateDeviceFileExplorerWindowValue;
import com.android.tools.idea.devicemanager.ApiTableCellRenderer;
import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.DeviceManagerFutureCallback;
import com.android.tools.idea.devicemanager.DeviceManagerUsageTracker;
import com.android.tools.idea.devicemanager.DeviceTable;
import com.android.tools.idea.devicemanager.DeviceType;
import com.android.tools.idea.devicemanager.Devices;
import com.android.tools.idea.devicemanager.IconButtonTableCellRenderer;
import com.android.tools.idea.devicemanager.Key;
import com.android.tools.idea.devicemanager.MergedTableColumn;
import com.android.tools.idea.devicemanager.PopUpMenuValue;
import com.android.tools.idea.devicemanager.Tables;
import com.android.tools.idea.devicemanager.virtualtab.VirtualDeviceTableModel.EditValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.concurrency.EdtExecutorService;
import java.awt.event.ActionListener;
import java.text.Collator;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javax.swing.DefaultRowSorter;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.RowSorter.SortKey;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class VirtualDeviceTable extends DeviceTable<VirtualDevice> implements VirtualDeviceWatcherListener, Disposable {
  private final @NotNull VirtualDeviceAsyncSupplier myAsyncSupplier;
  private final @NotNull NewSetDevices myNewSetDevices;
  private @Nullable IDeviceChangeListener myListener;
  private final @NotNull ActionListener myEmptyTextLinkListener;

  @VisibleForTesting
  interface NewSetDevices {
    @NotNull FutureCallback<@NotNull List<@NotNull VirtualDevice>> apply(@NotNull VirtualDeviceTable table);
  }

  VirtualDeviceTable(@NotNull VirtualDevicePanel panel) {
    this(panel, panel.getProject(), new VirtualDeviceAsyncSupplier(), VirtualDeviceTable::newSetDevices);
  }

  @VisibleForTesting
  VirtualDeviceTable(@NotNull VirtualDevicePanel panel,
                     @Nullable Project project,
                     @NotNull VirtualDeviceAsyncSupplier asyncSupplier,
                     @NotNull NewSetDevices newSetDevices) {
    super(new VirtualDeviceTableModel(project), VirtualDevice.class);

    myAsyncSupplier = asyncSupplier;
    myNewSetDevices = newSetDevices;

    initListener();

    setDefaultEditor(DeviceType.class, new DownloadButtonTableCellEditor(project));
    setDefaultEditor(VirtualDevice.State.class, new LaunchOrStopButtonTableCellEditor());

    setDefaultEditor(ActivateDeviceFileExplorerWindowValue.class,
                     new ActivateDeviceFileExplorerWindowButtonTableCellEditor<>(project,
                                                                                 this,
                                                                                 EventKind.VIRTUAL_DEVICE_FILE_EXPLORER_ACTION));

    setDefaultEditor(EditValue.class, new EditButtonTableCellEditor(panel));
    setDefaultEditor(PopUpMenuValue.class, new VirtualDevicePopUpMenuButtonTableCellEditor(panel));

    setDefaultRenderer(DeviceType.class, new VirtualDeviceIconButtonTableCellRenderer());
    setDefaultRenderer(Device.class, new VirtualDeviceTableCellRenderer());
    setDefaultRenderer(AndroidVersion.class, new ApiTableCellRenderer());
    setDefaultRenderer(Long.class, new SizeOnDiskTableCellRenderer());
    setDefaultRenderer(VirtualDevice.State.class, new LaunchOrStopButtonTableCellRenderer());

    setDefaultRenderer(ActivateDeviceFileExplorerWindowValue.class,
                       new ActivateDeviceFileExplorerWindowButtonTableCellRenderer<>(project, this));

    setDefaultRenderer(EditValue.class, new IconButtonTableCellRenderer(AllIcons.Actions.Edit, "Edit this AVD"));
    setDefaultRenderer(PopUpMenuValue.class, new IconButtonTableCellRenderer(AllIcons.Actions.More, "More Actions"));

    setRowSorter(newRowSorter(dataModel));
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setShowGrid(false);

    myEmptyTextLinkListener = new BuildVirtualDeviceConfigurationWizardActionListener(this, project, this);

    getEmptyText().setText("Loading...");

    refreshAvds();
  }

  @VisibleForTesting
  static @NotNull FutureCallback<@NotNull List<@NotNull VirtualDevice>> newSetDevices(@NotNull VirtualDeviceTable table) {
    return new DeviceManagerFutureCallback<>(VirtualDeviceTable.class, devices -> {
      table.getModel().setDevices(devices);
      // noinspection DialogTitleCapitalization
      table.getEmptyText()
        .clear()
        .appendLine("No virtual devices added. Create a virtual device to test")
        .appendLine("applications without owning a physical device.")
        .appendLine("Create virtual device", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, table.myEmptyTextLinkListener);

      DeviceManagerEvent event = DeviceManagerEvent.newBuilder()
        .setKind(EventKind.VIRTUAL_DEVICE_COUNT)
        .setVirtualDeviceCount(devices.size())
        .build();

      DeviceManagerUsageTracker.log(event);
    });
  }

  private void initListener() {
    myListener = new VirtualDeviceChangeListener(getModel());
    AndroidDebugBridge.addDeviceChangeListener(myListener);
  }

  private static @NotNull RowSorter<@NotNull TableModel> newRowSorter(@NotNull TableModel model) {
    DefaultRowSorter<TableModel, Integer> sorter = new TableRowSorter<>(model);

    sorter.setComparator(VirtualDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX,
                         Comparator.comparing(VirtualDevice::getName, Collator.getInstance()));
    sorter.setComparator(VirtualDeviceTableModel.API_MODEL_COLUMN_INDEX, Comparator.naturalOrder().reversed());
    sorter.setComparator(VirtualDeviceTableModel.SIZE_ON_DISK_MODEL_COLUMN_INDEX, Comparator.naturalOrder().reversed());
    sorter.setSortable(VirtualDeviceTableModel.LAUNCH_OR_STOP_MODEL_COLUMN_INDEX, false);
    sorter.setSortable(VirtualDeviceTableModel.ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX, false);
    sorter.setSortable(VirtualDeviceTableModel.EDIT_MODEL_COLUMN_INDEX, false);
    sorter.setSortable(VirtualDeviceTableModel.POP_UP_MENU_MODEL_COLUMN_INDEX, false);
    VirtualTabState tabState = VirtualTabPersistentStateComponent.getInstance().getState();
    sorter.setSortKeys(Collections.singletonList(new SortKey(tabState.getSortColumn(), tabState.getSortOrder())));

    sorter.addRowSorterListener(event -> {
      List<? extends SortKey> keys = sorter.getSortKeys();
      if (!keys.isEmpty()) {
        SortKey key = keys.get(0);
        VirtualTabPersistentStateComponent.getInstance().loadState(new VirtualTabState(key.getColumn(), key.getSortOrder()));
      }
    });

    return sorter;
  }

  @Override
  public void dispose() {
    AndroidDebugBridge.removeDeviceChangeListener(myListener);
  }

  @Override
  public void virtualDevicesChanged(@NotNull VirtualDeviceWatcherEvent event) {
    // TODO
  }

  @NotNull ListenableFuture<@NotNull Key> addDevice(@NotNull Key key) {
    // noinspection UnstableApiUsage
    return Futures.transform(myAsyncSupplier.get(key), this::add, EdtExecutorService.getInstance());
  }

  private @NotNull Key add(@NotNull VirtualDevice device) {
    getModel().add(device);
    return device.getKey();
  }

  @NotNull ListenableFuture<@NotNull Key> reloadDevice(@NotNull Key key) {
    // noinspection UnstableApiUsage
    return Futures.transform(myAsyncSupplier.get(key), this::set, EdtExecutorService.getInstance());
  }

  private @NotNull Key set(@NotNull VirtualDevice device) {
    getModel().set(device);
    return device.getKey();
  }

  @Override
  public @NotNull VirtualDeviceTableModel getModel() {
    return (VirtualDeviceTableModel)dataModel;
  }

  @NotNull Optional<@NotNull VirtualDevice> getSelectedDevice() {
    int viewRowIndex = getSelectedRow();

    if (viewRowIndex == -1) {
      return Optional.empty();
    }

    return Optional.of(getDeviceAt(viewRowIndex));
  }

  void setSelectedDevice(@NotNull Key key) {
    int modelRowIndex = Devices.indexOf(getModel().getDevices(), key);

    if (modelRowIndex == -1) {
      return;
    }

    int viewRowIndex = convertRowIndexToView(modelRowIndex);

    setRowSelectionInterval(viewRowIndex, viewRowIndex);
    scrollRectToVisible(getCellRect(viewRowIndex, deviceViewColumnIndex(), true));
  }

  @Override
  protected @NotNull JTableHeader createDefaultTableHeader() {
    TableColumnModel model = new DefaultTableColumnModel();

    model.addColumn(columnModel.getColumn(deviceIconViewColumnIndex()));
    model.addColumn(columnModel.getColumn(deviceViewColumnIndex()));
    model.addColumn(columnModel.getColumn(apiViewColumnIndex()));
    model.addColumn(columnModel.getColumn(sizeOnDiskViewColumnIndex()));

    Collection<TableColumn> columns = List.of(columnModel.getColumn(launchOrStopViewColumnIndex()),
                                              columnModel.getColumn(activateDeviceFileExplorerWindowViewColumnIndex()),
                                              columnModel.getColumn(editViewColumnIndex()),
                                              columnModel.getColumn(popUpMenuViewColumnIndex()));

    TableColumn column = new MergedTableColumn(columns);
    column.setHeaderValue("Actions");

    model.addColumn(column);

    JTableHeader header = super.createDefaultTableHeader();
    header.setColumnModel(model);
    header.setReorderingAllowed(false);
    header.setResizingAllowed(false);

    return header;
  }

  @Override
  public void doLayout() {
    Tables.setWidths(columnModel.getColumn(deviceIconViewColumnIndex()),
                     VirtualDeviceIconButtonTableCellRenderer.getPreferredWidth(this, DeviceType.class));

    columnModel.getColumn(deviceViewColumnIndex()).setMinWidth(JBUIScale.scale(200));

    Tables.setWidths(columnModel.getColumn(apiViewColumnIndex()),
                     Tables.getPreferredColumnWidth(this, apiViewColumnIndex(), JBUIScale.scale(65)),
                     JBUIScale.scale(20));

    Tables.setWidths(columnModel.getColumn(sizeOnDiskViewColumnIndex()),
                     Tables.getPreferredColumnWidth(this, sizeOnDiskViewColumnIndex(), JBUIScale.scale(65)),
                     JBUIScale.scale(20));

    Tables.setWidths(columnModel.getColumn(launchOrStopViewColumnIndex()),
                     IconButtonTableCellRenderer.getPreferredWidth(this, VirtualDevice.State.class));

    Tables.setWidths(columnModel.getColumn(activateDeviceFileExplorerWindowViewColumnIndex()),
                     IconButtonTableCellRenderer.getPreferredWidth(this, ActivateDeviceFileExplorerWindowValue.class));

    Tables.setWidths(columnModel.getColumn(editViewColumnIndex()), IconButtonTableCellRenderer.getPreferredWidth(this, EditValue.class));

    Tables.setWidths(columnModel.getColumn(popUpMenuViewColumnIndex()),
                     IconButtonTableCellRenderer.getPreferredWidth(this, PopUpMenuValue.class));

    super.doLayout();
  }

  private int deviceIconViewColumnIndex() {
    return convertColumnIndexToView(VirtualDeviceTableModel.DEVICE_ICON_MODEL_COLUMN_INDEX);
  }

  @Override
  protected int deviceViewColumnIndex() {
    return convertColumnIndexToView(VirtualDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX);
  }

  private int apiViewColumnIndex() {
    return convertColumnIndexToView(VirtualDeviceTableModel.API_MODEL_COLUMN_INDEX);
  }

  private int sizeOnDiskViewColumnIndex() {
    return convertColumnIndexToView(VirtualDeviceTableModel.SIZE_ON_DISK_MODEL_COLUMN_INDEX);
  }

  private int launchOrStopViewColumnIndex() {
    return convertColumnIndexToView(VirtualDeviceTableModel.LAUNCH_OR_STOP_MODEL_COLUMN_INDEX);
  }

  private int activateDeviceFileExplorerWindowViewColumnIndex() {
    return convertColumnIndexToView(VirtualDeviceTableModel.ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX);
  }

  private int editViewColumnIndex() {
    return convertColumnIndexToView(VirtualDeviceTableModel.EDIT_MODEL_COLUMN_INDEX);
  }

  private int popUpMenuViewColumnIndex() {
    return convertColumnIndexToView(VirtualDeviceTableModel.POP_UP_MENU_MODEL_COLUMN_INDEX);
  }

  void refreshAvds() {
    Futures.addCallback(myAsyncSupplier.getAll(), myNewSetDevices.apply(this), EdtExecutorService.getInstance());
  }
}
