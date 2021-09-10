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

import com.android.tools.idea.avdmanager.ApiLevelComparator;
import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.Tables;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceTableModel.Actions;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.table.JBTable;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.DefaultRowSorter;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import org.jetbrains.annotations.NotNull;

final class PhysicalDeviceTable extends JBTable {
  private final @NotNull BiConsumer<@NotNull JTable, @NotNull Integer> mySizeWidthToFit;

  PhysicalDeviceTable(@NotNull PhysicalDevicePanel panel) {
    this(panel, new PhysicalDeviceTableModel());
  }

  @VisibleForTesting
  PhysicalDeviceTable(@NotNull PhysicalDevicePanel panel, @NotNull PhysicalDeviceTableModel model) {
    this(panel, model, Tables::sizeWidthToFit, PhysicalDeviceTableCellRenderer::new, ActionsTableCellRenderer::new);
  }

  @VisibleForTesting
  PhysicalDeviceTable(@NotNull PhysicalDevicePanel panel,
                      @NotNull PhysicalDeviceTableModel model,
                      @NotNull BiConsumer<@NotNull JTable, @NotNull Integer> sizeWidthToFit,
                      @NotNull Supplier<@NotNull TableCellRenderer> newDeviceTableCellRenderer,
                      @NotNull Supplier<@NotNull TableCellRenderer> newActionsTableCellRenderer) {
    super(model);

    mySizeWidthToFit = sizeWidthToFit;
    model.addTableModelListener(event -> sizeApiTypeAndActionsColumnWidthsToFit());

    setDefaultEditor(Actions.class, new ActionsTableCellEditor(panel));
    setDefaultRenderer(Device.class, newDeviceTableCellRenderer.get());
    setDefaultRenderer(Actions.class, newActionsTableCellRenderer.get());
    setRowSorter(newRowSorter(model));
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setShowGrid(false);

    getActionMap().put("selectNextColumn", new SelectNextColumnAction());
    getEmptyText().setText("No physical devices added. Connect a device via USB cable.");

    tableHeader.setReorderingAllowed(false);
    tableHeader.setResizingAllowed(false);
  }

  private void sizeApiTypeAndActionsColumnWidthsToFit() {
    getRowSorter().allRowsChanged();

    mySizeWidthToFit.accept(this, PhysicalDeviceTableModel.API_MODEL_COLUMN_INDEX);
    mySizeWidthToFit.accept(this, PhysicalDeviceTableModel.TYPE_MODEL_COLUMN_INDEX);
    mySizeWidthToFit.accept(this, PhysicalDeviceTableModel.ACTIONS_MODEL_COLUMN_INDEX);
  }

  private static @NotNull RowSorter<@NotNull TableModel> newRowSorter(@NotNull TableModel model) {
    DefaultRowSorter<TableModel, Integer> sorter = new TableRowSorter<>(model);

    sorter.setComparator(PhysicalDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX, Comparator.comparing(PhysicalDevice::getName));
    sorter.setComparator(PhysicalDeviceTableModel.API_MODEL_COLUMN_INDEX, new ApiLevelComparator().reversed());
    sorter.setComparator(PhysicalDeviceTableModel.TYPE_MODEL_COLUMN_INDEX, Comparator.naturalOrder().reversed());
    sorter.setSortable(PhysicalDeviceTableModel.ACTIONS_MODEL_COLUMN_INDEX, false);
    sorter.setSortKeys(Collections.singletonList(new SortKey(PhysicalDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX, SortOrder.ASCENDING)));

    return sorter;
  }

  @NotNull Optional<@NotNull PhysicalDevice> getSelectedDevice() {
    int viewRowIndex = getSelectedRow();

    if (viewRowIndex == -1) {
      return Optional.empty();
    }

    return Optional.of(getDeviceAt(viewRowIndex));
  }

  @NotNull PhysicalDevice getDeviceAt(int viewRowIndex) {
    return (PhysicalDevice)getValueAt(viewRowIndex, convertColumnIndexToView(PhysicalDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX));
  }

  boolean isActionsColumn(int viewColumnIndex) {
    return getColumnClass(viewColumnIndex).equals(Actions.class);
  }

  @NotNull ActionsTableCellEditor getActionsCellEditor() {
    return (ActionsTableCellEditor)getCellEditor();
  }

  @VisibleForTesting
  @NotNull Object getData() {
    return IntStream.range(0, getRowCount())
      .mapToObj(this::getRowAt)
      .collect(Collectors.toList());
  }

  @VisibleForTesting
  private @NotNull Object getRowAt(int viewRowIndex) {
    return IntStream.range(0, getColumnCount())
      .mapToObj(viewColumnIndex -> getValueAt(viewRowIndex, viewColumnIndex))
      .collect(Collectors.toList());
  }

  @Override
  public @NotNull PhysicalDeviceTableModel getModel() {
    return (PhysicalDeviceTableModel)dataModel;
  }
}
