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

import com.android.tools.idea.wearpairing.WearDevicePairingWizard;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.scale.JBUIScale;
import java.util.Optional;
import javax.swing.ListSelectionModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PairingTable extends DeviceTable<Device> {
  private final @NotNull Object myKey;
  private final @Nullable Project myProject;

  PairingTable(@NotNull Key key, @Nullable Project project) {
    super(new PairingTableModel(), Device.class);

    myKey = key;
    myProject = project;

    setDefaultRenderer(DeviceType.class, new DeviceIconButtonTableCellRenderer<>(this));
    setDefaultRenderer(Device.class, new DeviceManagerPairingDeviceTableCellRenderer());
    setDefaultRenderer(Status.class, new StatusTableCellRenderer());

    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setShowGrid(false);

    getEmptyText()
      .appendLine("Device is not paired to companion device.")
      .appendLine("Pair wearable", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, event -> pairWearable());

    tableHeader.setReorderingAllowed(false);
    tableHeader.setResizingAllowed(false);
  }

  private void pairWearable() {
    new WearDevicePairingWizard().show(myProject, myKey.toString());
  }

  private int deviceIconViewColumnIndex() {
    return convertColumnIndexToView(PairingTableModel.DEVICE_ICON_MODEL_COLUMN_INDEX);
  }

  @Override
  protected int deviceViewColumnIndex() {
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
  public void doLayout() {
    Tables.setWidths(columnModel.getColumn(deviceIconViewColumnIndex()),
                     DeviceIconButtonTableCellRenderer.getPreferredWidth(this, DeviceType.class));

    columnModel.getColumn(deviceViewColumnIndex()).setMinWidth(JBUIScale.scale(65));

    Tables.setWidths(columnModel.getColumn(statusViewColumnIndex()),
                     Tables.getPreferredColumnWidth(this, statusViewColumnIndex(), JBUIScale.scale(65)),
                     JBUIScale.scale(20));

    super.doLayout();
  }

  @Override
  public @NotNull PairingTableModel getModel() {
    return (PairingTableModel)dataModel;
  }
}
