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

import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceTableModel.Actions;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.table.JBTable;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.ListSelectionModel;
import javax.swing.table.TableCellRenderer;
import org.jetbrains.annotations.NotNull;

final class PhysicalDeviceTable extends JBTable {
  PhysicalDeviceTable(@NotNull PhysicalDevicePanel panel) {
    this(panel, new PhysicalDeviceTableModel(), PhysicalDeviceTableCellRenderer::new, ActionsTableCellRenderer::new);
  }

  @VisibleForTesting
  PhysicalDeviceTable(@NotNull PhysicalDevicePanel panel,
                      @NotNull PhysicalDeviceTableModel model,
                      @NotNull Supplier<@NotNull TableCellRenderer> newDeviceTableCellRenderer,
                      @NotNull Supplier<@NotNull TableCellRenderer> newActionsTableCellRenderer) {
    super(model);

    setDefaultEditor(Actions.class, new ActionsTableCellEditor(panel));
    setDefaultRenderer(Device.class, newDeviceTableCellRenderer.get());
    setDefaultRenderer(Actions.class, newActionsTableCellRenderer.get());
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setShowGrid(false);

    getEmptyText().setText("No physical devices added. Connect a device via USB cable.");
    tableHeader.setReorderingAllowed(false);
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
