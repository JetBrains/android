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

import com.android.annotations.concurrency.AnyThread;
import com.android.annotations.concurrency.UiThread;
import com.android.annotations.concurrency.WorkerThread;
import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.idea.wearpairing.WearDevicePairingWizard;
import com.android.tools.idea.wearpairing.WearPairingManager;
import com.android.tools.idea.wearpairing.WearPairingManager.PairingStatusChangedListener;
import com.android.tools.idea.wearpairing.WearPairingManager.PhoneWearPair;
import com.android.tools.idea.wearpairing.WearPairingManagerKt;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI.Borders;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.AbstractButton;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JComponent;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PairedDevicesPanel extends JBPanel<PairedDevicesPanel> implements Disposable, PairingStatusChangedListener {
  private final @NotNull Key myKey;
  private final @Nullable Project myProject;
  private final @NotNull WearPairingManager myManager;

  private @Nullable AbstractButton myAddButton;
  private AbstractButton myRemoveButton;
  private PairingTable myTable;
  private @Nullable JComponent myScrollPane;

  @UiThread
  public PairedDevicesPanel(@NotNull Key key, @NotNull Disposable parent, @Nullable Project project) {
    this(key, parent, project, WearPairingManager.getInstance());
  }

  @UiThread
  @VisibleForTesting
  PairedDevicesPanel(@NotNull Key key, @NotNull Disposable parent, @Nullable Project project, @NotNull WearPairingManager manager) {
    super(null);

    myKey = key;
    myProject = project;
    myManager = manager;

    initAddButton();
    initRemoveButton();
    initTable();
    initScrollPane();

    layOut();

    myManager.addDevicePairingStatusChangedListener(this);
    Disposer.register(parent, this);
  }

  @UiThread
  @Override
  public void dispose() {
    myManager.removeDevicePairingStatusChangedListener(this);
  }

  @UiThread
  private void initAddButton() {
    myAddButton = new CommonButton(AllIcons.General.Add);

    myAddButton.setToolTipText("Add");
    myAddButton.addActionListener(event -> new WearDevicePairingWizard().show(myProject, myKey.toString()));
  }

  @UiThread
  private void initRemoveButton() {
    myRemoveButton = new CommonButton(AllIcons.General.Remove);

    myRemoveButton.setEnabled(false);
    myRemoveButton.setToolTipText("Remove");

    myRemoveButton.addActionListener(event -> remove());
  }

  @UiThread
  private void remove() {
    PhoneWearPair pair = myTable.getSelectedPairing().orElseThrow(AssertionError::new).getPair();
    Object wearOs = pair.getWear();
    Object phone = pair.getPhone();

    String message = "This will disconnect " + wearOs + " from " + phone + ". To completely unpair the two devices, remove " + wearOs +
                     " from the list of devices in the Wear OS app on " + phone + " and wipe data from " + wearOs + '.';

    boolean disconnect = MessageDialogBuilder.okCancel("Disconnect " + wearOs + " from " + phone + '?', message)
      .asWarning()
      .yesText("Disconnect")
      .ask(this);

    if (disconnect) {
      WearPairingManagerKt.removePairedDevicesAsync(myManager, pair, true);
    }
  }

  @UiThread
  private void initTable() {
    myTable = new PairingTable(myKey, myProject);

    myTable.getSelectionModel().addListSelectionListener(event -> {
      if (event.getValueIsAdjusting()) {
        return;
      }

      myRemoveButton.setEnabled(myTable.getSelectedRowCount() != 0);
    });

    reloadPairings();

    if (myTable.isEmpty()) {
      return;
    }

    myTable.setRowSelectionInterval(0, 0);
  }

  @UiThread
  private void initScrollPane() {
    myScrollPane = new JBScrollPane(myTable);
    myScrollPane.setBorder(Borders.empty());
  }

  @UiThread
  private void layOut() {
    GroupLayout layout = new GroupLayout(this);

    Group horizontalGroup = layout.createParallelGroup()
      .addGroup(layout.createSequentialGroup()
                  .addComponent(myAddButton)
                  .addComponent(myRemoveButton))
      .addComponent(myScrollPane);

    Group verticalGroup = layout.createSequentialGroup()
      .addGroup(layout.createParallelGroup()
                  .addComponent(myAddButton)
                  .addComponent(myRemoveButton))
      .addComponent(myScrollPane);

    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    setLayout(layout);
  }

  /**
   * Called by IO dispatcher worker threads and the event dispatch thread
   */
  @AnyThread
  @Override
  public void pairingStatusChanged(@NotNull PhoneWearPair pair) {
    reloadPairingsIfPairContainsKey(pair);
  }

  /**
   * Called by IO dispatcher worker threads
   */
  @WorkerThread
  @Override
  public void pairingDeviceRemoved(@NotNull PhoneWearPair pair) {
    reloadPairingsIfPairContainsKey(pair);
  }

  /**
   * Called by IO dispatcher worker threads and the event dispatch thread
   */
  @AnyThread
  private void reloadPairingsIfPairContainsKey(@NotNull PhoneWearPair pair) {
    if (pair.contains(myKey.toString())) {
      ApplicationManager.getApplication().invokeLater(this::reloadPairings, ModalityState.any());
    }
  }

  @UiThread
  @VisibleForTesting
  void reloadPairings() {
    Collection<PhoneWearPair> pairs = myManager.getPairsForDevice(myKey.toString());

    if (pairs.isEmpty()) {
      Logger.getInstance(PairedDevicesPanel.class).info("No pairs");
    }
    else {
      Logger logger = Logger.getInstance(PairedDevicesPanel.class);
      int i = 1;

      for (Object pair : pairs) {
        logger.info(i++ + " " + pair);
      }
    }

    List<Pairing> pairings = pairs.stream()
      .map(pair -> new Pairing(pair, myKey))
      .collect(Collectors.toList());

    myTable.getModel().setPairings(pairings);
  }

  @VisibleForTesting
  @NotNull AbstractButton getRemoveButton() {
    return myRemoveButton;
  }

  @VisibleForTesting
  @NotNull JTable getTable() {
    return myTable;
  }
}
