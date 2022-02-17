/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.devicemanager;

import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.table.JBTable;
import java.util.Optional;
import javax.swing.ListSelectionModel;
import org.jetbrains.annotations.NotNull;

final class PairingTable extends JBTable {
  PairingTable() {
    super(new PairingTableModel());

    setDefaultRenderer(Device.class, new DeviceManagerPairingDeviceTableCellRenderer());
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setShowGrid(false);

    tableHeader.setReorderingAllowed(false);
    tableHeader.setResizingAllowed(false);

    dataModel.addTableModelListener(event -> {
      columnModel.getColumn(deviceViewColumnIndex()).setMinWidth(JBUIScale.scale(65));

      Tables.setWidths(columnModel.getColumn(statusViewColumnIndex()),
                       Tables.getPreferredColumnWidth(PairingTable.this, statusViewColumnIndex(), JBUIScale.scale(65)),
                       JBUIScale.scale(20));
    });
  }

  private int deviceViewColumnIndex() {
    return convertColumnIndexToView(PairingTableModel.DEVICE_MODEL_COLUMN_INDEX);
  }

  private int statusViewColumnIndex() {
    return convertColumnIndexToView(PairingTableModel.STATUS_MODEL_COLUMN_INDEX);
  }

  @NotNull Optional<@NotNull Pairing> getSelectedPairing() {
    int viewRowIndex = getSelectedRow();

    if (viewRowIndex == -1) {
      return Optional.empty();
    }

    return Optional.of(getModel().getPairings().get(convertRowIndexToModel(viewRowIndex)));
  }

  @Override
  public @NotNull PairingTableModel getModel() {
    return (PairingTableModel)dataModel;
  }
}
