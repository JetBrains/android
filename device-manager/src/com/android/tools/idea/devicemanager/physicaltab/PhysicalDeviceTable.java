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
package com.android.tools.idea.devicemanager.physicaltab;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.devicemanager.ActivateDeviceFileExplorerWindowButtonTableCellEditor;
import com.android.tools.idea.devicemanager.ActivateDeviceFileExplorerWindowButtonTableCellRenderer;
import com.android.tools.idea.devicemanager.ActivateDeviceFileExplorerWindowValue;
import com.android.tools.idea.devicemanager.ApiTableCellRenderer;
import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.DeviceIconButtonTableCellRenderer;
import com.android.tools.idea.devicemanager.DeviceTable;
import com.android.tools.idea.devicemanager.DeviceType;
import com.android.tools.idea.devicemanager.IconButtonTableCellRenderer;
import com.android.tools.idea.devicemanager.MergedTableColumn;
import com.android.tools.idea.devicemanager.PopUpMenuValue;
import com.android.tools.idea.devicemanager.Tables;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceTableModel.RemoveValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.scale.JBUIScale;
import java.text.Collator;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import javax.swing.DefaultRowSorter;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import org.jetbrains.annotations.NotNull;

public final class PhysicalDeviceTable extends DeviceTable<PhysicalDevice> {
  PhysicalDeviceTable(@NotNull PhysicalDevicePanel panel) {
    this(panel, new PhysicalDeviceTableModel());
  }

  @VisibleForTesting
  PhysicalDeviceTable(@NotNull PhysicalDevicePanel panel, @NotNull PhysicalDeviceTableModel model) {
    super(model, PhysicalDevice.class);

    Project project = panel.getProject();
    assert project != null;

    setDefaultEditor(ActivateDeviceFileExplorerWindowValue.class,
                     new ActivateDeviceFileExplorerWindowButtonTableCellEditor<>(project,
                                                                                 this,
                                                                                 EventKind.PHYSICAL_DEVICE_FILE_EXPLORER_ACTION));

    setDefaultEditor(RemoveValue.class, new RemoveButtonTableCellEditor(panel));
    setDefaultEditor(PopUpMenuValue.class, new PhysicalDevicePopUpMenuButtonTableCellEditor(panel));

    setDefaultRenderer(DeviceType.class, new DeviceIconButtonTableCellRenderer<>(this));
    setDefaultRenderer(Device.class, new PhysicalDeviceTableCellRenderer());
    setDefaultRenderer(AndroidVersion.class, new ApiTableCellRenderer());
    setDefaultRenderer(Collection.class, new TypeTableCellRenderer());

    setDefaultRenderer(ActivateDeviceFileExplorerWindowValue.class,
                       new ActivateDeviceFileExplorerWindowButtonTableCellRenderer<>(project, this));

    setDefaultRenderer(RemoveValue.class, new RemoveButtonTableCellRenderer());
    setDefaultRenderer(PopUpMenuValue.class, new IconButtonTableCellRenderer(AllIcons.Actions.More, "More Actions"));

    setRowSorter(newRowSorter(model));
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setShowGrid(false);

    getEmptyText().setText("No physical devices added. Connect a device via USB cable.");
  }

  private static @NotNull RowSorter<TableModel> newRowSorter(@NotNull TableModel model) {
    DefaultRowSorter<TableModel, Integer> sorter = new TableRowSorter<>(model);

    sorter.setComparator(PhysicalDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX,
                         Comparator.comparing(PhysicalDevice::getName, Collator.getInstance()));
    sorter.setComparator(PhysicalDeviceTableModel.API_MODEL_COLUMN_INDEX, Comparator.naturalOrder().reversed());
    sorter.setSortable(PhysicalDeviceTableModel.TYPE_MODEL_COLUMN_INDEX, false);
    sorter.setSortable(PhysicalDeviceTableModel.ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX, false);
    sorter.setSortable(PhysicalDeviceTableModel.REMOVE_MODEL_COLUMN_INDEX, false);
    sorter.setSortable(PhysicalDeviceTableModel.POP_UP_MENU_MODEL_COLUMN_INDEX, false);
    sorter.setSortKeys(Collections.singletonList(new SortKey(PhysicalDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX, SortOrder.ASCENDING)));

    return sorter;
  }

  @NotNull Optional<PhysicalDevice> getSelectedDevice() {
    int viewRowIndex = getSelectedRow();

    if (viewRowIndex == -1) {
      return Optional.empty();
    }

    return Optional.of(getDeviceAt(viewRowIndex));
  }

  @Override
  protected @NotNull JTableHeader createDefaultTableHeader() {
    TableColumnModel model = new DefaultTableColumnModel();

    model.addColumn(columnModel.getColumn(deviceIconViewColumnIndex()));
    model.addColumn(columnModel.getColumn(deviceViewColumnIndex()));
    model.addColumn(columnModel.getColumn(apiViewColumnIndex()));
    model.addColumn(columnModel.getColumn(typeViewColumnIndex()));

    Collection<TableColumn> columns = Arrays.asList(columnModel.getColumn(activateDeviceFileExplorerWindowViewColumnIndex()),
                                                    columnModel.getColumn(removeViewColumnIndex()),
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
                     DeviceIconButtonTableCellRenderer.getPreferredWidth(this, DeviceType.class));

    columnModel.getColumn(deviceViewColumnIndex()).setMinWidth(JBUIScale.scale(200));

    Tables.setWidths(columnModel.getColumn(apiViewColumnIndex()),
                     Tables.getPreferredColumnWidth(this, apiViewColumnIndex(), JBUIScale.scale(65)),
                     JBUIScale.scale(20));

    Tables.setWidths(columnModel.getColumn(typeViewColumnIndex()),
                     Tables.getPreferredColumnWidth(this, typeViewColumnIndex(), JBUIScale.scale(65)),
                     JBUIScale.scale(20));

    Tables.setWidths(columnModel.getColumn(activateDeviceFileExplorerWindowViewColumnIndex()),
                     IconButtonTableCellRenderer.getPreferredWidth(this, ActivateDeviceFileExplorerWindowValue.class));

    Tables.setWidths(columnModel.getColumn(removeViewColumnIndex()),
                     IconButtonTableCellRenderer.getPreferredWidth(this, RemoveValue.class));

    Tables.setWidths(columnModel.getColumn(popUpMenuViewColumnIndex()),
                     IconButtonTableCellRenderer.getPreferredWidth(this, PopUpMenuValue.class));

    super.doLayout();
  }

  private int deviceIconViewColumnIndex() {
    return convertColumnIndexToView(PhysicalDeviceTableModel.DEVICE_ICON_MODEL_COLUMN_INDEX);
  }

  @Override
  protected int deviceViewColumnIndex() {
    return convertColumnIndexToView(PhysicalDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX);
  }

  private int apiViewColumnIndex() {
    return convertColumnIndexToView(PhysicalDeviceTableModel.API_MODEL_COLUMN_INDEX);
  }

  private int typeViewColumnIndex() {
    return convertColumnIndexToView(PhysicalDeviceTableModel.TYPE_MODEL_COLUMN_INDEX);
  }

  private int activateDeviceFileExplorerWindowViewColumnIndex() {
    return convertColumnIndexToView(PhysicalDeviceTableModel.ACTIVATE_DEVICE_FILE_EXPLORER_WINDOW_MODEL_COLUMN_INDEX);
  }

  private int removeViewColumnIndex() {
    return convertColumnIndexToView(PhysicalDeviceTableModel.REMOVE_MODEL_COLUMN_INDEX);
  }

  private int popUpMenuViewColumnIndex() {
    return convertColumnIndexToView(PhysicalDeviceTableModel.POP_UP_MENU_MODEL_COLUMN_INDEX);
  }

  @Override
  public @NotNull PhysicalDeviceTableModel getModel() {
    return (PhysicalDeviceTableModel)dataModel;
  }
}
