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
import com.android.tools.adtui.stdui.ActionData;
import com.android.tools.adtui.stdui.EmptyStatePanel;
import com.android.tools.idea.devicemanager.physicaltab.Key;
import com.android.tools.idea.wearpairing.PairingDevice;
import com.android.tools.idea.wearpairing.WearDevicePairingWizard;
import com.android.tools.idea.wearpairing.WearPairingManager;
import com.android.tools.idea.wearpairing.WearPairingManager.PairingStatusChangedListener;
import com.android.tools.idea.wearpairing.WearPairingManager.PhoneWearPair;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI.CurrentTheme.Table;
import java.awt.BorderLayout;
import java.util.Collections;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.TableColumnModel;
import kotlin.Unit;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.GlobalScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PairedDevicesPanel extends JBPanel<PairedDevicesPanel> implements Disposable, PairingStatusChangedListener {
  private final @NotNull Key myDeviceId;
  private final @NotNull WearPairingManager myManager;

  public PairedDevicesPanel(@NotNull Key deviceId, @NotNull Disposable parent) {
    this(deviceId, parent, WearPairingManager.INSTANCE);
  }

  @VisibleForTesting
  PairedDevicesPanel(@NotNull Key deviceId, @NotNull Disposable parent, @NotNull WearPairingManager pairingManager) {
    super(new BorderLayout());

    myDeviceId = deviceId;
    myManager = pairingManager;

    createUi(myManager.getPairedDevices(myDeviceId.toString()));
    myManager.addDevicePairingStatusChangedListener(this);
    Disposer.register(parent, this);
  }

  @Override
  public void dispose() {
    myManager.removeDevicePairingStatusChangedListener(this);
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

    ToolbarDecorator toolbar;

    if (phoneWearPair == null) {
      toolbar = new EmptyStateToolbarDecorator()
        .setRemoveAction(button -> {
        }) // Add remove button
        .setRemoveActionUpdater(event -> false); // Disable remove button
    }
    else {
      PairingDevice peerDevice = phoneWearPair.getPeerDevice(myDeviceId.toString());

      Pairing pairing = new Pairing(peerDevice, phoneWearPair.getPairingStatus());

      JTable table = new JBTable(new PairingTableModel(Collections.singletonList(pairing)));
      table.setDefaultRenderer(Device.class, new DeviceManagerPairingDeviceTableCellRenderer());
      table.setShowGrid(false);

      TableColumnModel columnModel = table.getColumnModel();
      columnModel.getColumn(0).setPreferredWidth(80_000); // Some large number, 80% of total width
      columnModel.getColumn(1).setPreferredWidth(20_000);

      toolbar = ToolbarDecorator.createDecorator(table).setRemoveAction(button -> unpairDevice(peerDevice.getDeviceID()));
    }

    toolbar
      .setToolbarPosition(ActionToolbarPosition.TOP)
      .setAddAction(button -> pairDevice(CommonDataKeys.PROJECT.getData(button.getDataContext())));

    add(toolbar.createPanel(), BorderLayout.CENTER);

    if (getParent() != null) {
      revalidate();
    }
  }

  private final class EmptyStateToolbarDecorator extends ToolbarDecorator {
    private @Nullable JComponent myEmptyStatePanel;

    protected @NotNull JComponent getComponent() {
      ActionData data = new ActionData("Pair device", this::pairDevice);

      myEmptyStatePanel = new EmptyStatePanel("Device is not paired to companion device.", null, data);
      myEmptyStatePanel.setBackground(Table.BACKGROUND);

      return myEmptyStatePanel;
    }

    @SuppressWarnings("SameReturnValue")
    private @NotNull Unit pairDevice() {
      PairedDevicesPanel.this.pairDevice(CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myEmptyStatePanel)));
      return Unit.INSTANCE;
    }

    @Override
    protected void updateButtons() {
    }

    @Override
    protected void installDnDSupport() {
    }

    @Override
    protected boolean isModelEditable() {
      return false;
    }
  }

  private void pairDevice(@Nullable Project project) {
    new WearDevicePairingWizard().show(project, myDeviceId.toString());
  }

  private void unpairDevice(@NotNull String deviceId) {
    DeviceManagerEvent event = DeviceManagerEvent.newBuilder()
      .setKind(EventKind.PHYSICAL_UNPAIR_DEVICE_ACTION)
      .build();

    DeviceManagerUsageTracker.log(event);

    try {
      CoroutineContext context = GlobalScope.INSTANCE.getCoroutineContext();
      BuildersKt.runBlocking(context, (scope, continuation) -> myManager.removePairedDevices(deviceId, true, continuation));
    }
    catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      Logger.getInstance(PairedDevicesPanel.class).warn(exception);
    }
  }
}
