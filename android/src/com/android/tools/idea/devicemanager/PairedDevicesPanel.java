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
package com.android.tools.idea.devicemanager;

import com.android.annotations.concurrency.UiThread;
import com.android.tools.idea.devicemanager.physicaltab.Key;
import com.android.tools.idea.wearpairing.PairingDevice;
import com.android.tools.idea.wearpairing.WearPairingManager;
import com.android.tools.idea.wearpairing.WearPairingManager.PairingState;
import com.android.tools.idea.wearpairing.WearPairingManager.PairingStatusChangedListener;
import com.android.tools.idea.wearpairing.WearPairingManager.PhoneWearPair;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import java.awt.BorderLayout;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PairedDevicesPanel extends JBPanel<PairedDevicesPanel> implements PairingStatusChangedListener {
  private final @NotNull Key myDeviceId;

  public PairedDevicesPanel(@NotNull Key deviceId) {
    super(new BorderLayout());

    myDeviceId = deviceId;
    createUi(WearPairingManager.INSTANCE.getPairedDevices(myDeviceId.toString()));
    WearPairingManager.INSTANCE.addDevicePairingStatusChangedListener(this);
  }

  private static @NotNull String getConnectionStatus(@NotNull PairingState pairingState) {
    switch (pairingState) {
      case OFFLINE:
        return "Offline";
      case CONNECTING:
        return "Connecting";
      case CONNECTED:
        return "Connected";
      case PAIRING_FAILED:
        return "Error pairing";
      default:
        throw new AssertionError(pairingState);
    }
  }

  @Override
  public void pairingStatusChanged(@NotNull PhoneWearPair phoneWearPair) {
    if (phoneWearPair.contains(myDeviceId.toString())) {
      ApplicationManager.getApplication().invokeLater(() -> createUi(phoneWearPair), ModalityState.any());
    }
  }

  @Override
  public void pairingDeviceRemoved(@NotNull PhoneWearPair phoneWearPair) {
    if (phoneWearPair.contains(myDeviceId.toString())) {
      ApplicationManager.getApplication().invokeLater(() -> createUi(null), ModalityState.any());
    }
  }

  @UiThread
  private void createUi(@Nullable PhoneWearPair phoneWearPair) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    removeAll();

    // TODO: Add Devices toolbar b/193748025

    if (phoneWearPair == null) {
      // TODO: Add zero state - b/193748051
      add(new JBLabel("Device is not paired to companion device."), BorderLayout.CENTER);
    }
    else {
      PairingDevice peerDevice = phoneWearPair.getPeerDevice(myDeviceId.toString());

      // TODO: Paired Device Table b/193747557
      DefaultTableModel tableModel = new DefaultTableModel(new Object[]{"Device", "Status"}, 0);
      tableModel.addRow(new Object[]{peerDevice.getDisplayName(), getConnectionStatus(phoneWearPair.getPairingStatus())});
      JBTable table = new JBTable(tableModel);
      TableColumnModel columnModel = table.getColumnModel();
      columnModel.getColumn(0).setPreferredWidth(80_000); // Some large number, 80% of total width
      columnModel.getColumn(1).setPreferredWidth(20_000);

      add(new JBScrollPane(table), BorderLayout.CENTER);
    }

    if (getParent() != null) {
      revalidate();
    }
  }
}
