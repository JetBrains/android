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

import com.android.tools.idea.adb.wireless.PairDevicesUsingWiFiService;
import com.android.tools.idea.concurrency.FutureUtils;
import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.DeviceTableCellRenderer;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.concurrency.EdtExecutorService;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.AbstractButton;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JButton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PhysicalDevicePanel extends JBPanel<PhysicalDevicePanel> implements Disposable {
  private final @Nullable Project myProject;
  private final @NotNull Function<@NotNull Project, @NotNull PairDevicesUsingWiFiService> myPairDevicesUsingWiFiServiceGetInstance;
  private final @NotNull Supplier<@NotNull PhysicalTabPersistentStateComponent> myPhysicalTabPersistentStateComponentGetInstance;
  private final @NotNull Function<@NotNull PhysicalDeviceTableModel, @NotNull Disposable> myNewPhysicalDeviceChangeListener;

  private @Nullable AbstractButton myPairUsingWiFiButton;
  private @Nullable JBTable myTable;

  @VisibleForTesting
  static final class SetTableModel implements FutureCallback<List<PhysicalDevice>> {
    private final @NotNull PhysicalDevicePanel myPanel;

    @VisibleForTesting
    SetTableModel(@NotNull PhysicalDevicePanel panel) {
      myPanel = panel;
    }

    @Override
    public void onSuccess(@Nullable List<@NotNull PhysicalDevice> devices) {
      assert devices != null;
      myPanel.setTableModel(myPanel.addOfflineDevices(devices));
    }

    @Override
    public void onFailure(@NotNull Throwable throwable) {
      Logger.getInstance(PhysicalDevicePanel.class).warn(throwable);
    }
  }

  PhysicalDevicePanel(@Nullable Project project) {
    this(project,
         PairDevicesUsingWiFiService::getInstance,
         PhysicalTabPersistentStateComponent::getInstance,
         PhysicalDeviceChangeListener::new,
         new PhysicalDeviceAsyncSupplier(project),
         SetTableModel::new);
  }

  @VisibleForTesting
  PhysicalDevicePanel(@Nullable Project project,
                      @NotNull Function<@NotNull Project, @NotNull PairDevicesUsingWiFiService> pairDevicesUsingWiFiServiceGetInstance,
                      @NotNull Supplier<@NotNull PhysicalTabPersistentStateComponent> physicalTabPersistentStateComponentGetInstance,
                      @NotNull Function<@NotNull PhysicalDeviceTableModel, @NotNull Disposable> newPhysicalDeviceChangeListener,
                      @NotNull PhysicalDeviceAsyncSupplier supplier,
                      @NotNull Function<@NotNull PhysicalDevicePanel, @NotNull FutureCallback<@Nullable List<@NotNull PhysicalDevice>>> newSetTableModel) {
    super(null);

    myProject = project;
    myPairDevicesUsingWiFiServiceGetInstance = pairDevicesUsingWiFiServiceGetInstance;
    myPhysicalTabPersistentStateComponentGetInstance = physicalTabPersistentStateComponentGetInstance;
    myNewPhysicalDeviceChangeListener = newPhysicalDeviceChangeListener;

    initPairUsingWiFiButton();
    initTable();
    setLayout();

    FutureUtils.addCallback(supplier.get(), EdtExecutorService.getInstance(), newSetTableModel.apply(this));
  }

  private void initPairUsingWiFiButton() {
    // TODO(http://b/187102682) Does pairing using Wi-Fi need to work from the Welcome to Android Studio window?
    if (myProject == null) {
      return;
    }

    PairDevicesUsingWiFiService service = myPairDevicesUsingWiFiServiceGetInstance.apply(myProject);

    if (!service.isFeatureEnabled()) {
      return;
    }

    myPairUsingWiFiButton = new JButton("Pair using Wi-Fi");
    myPairUsingWiFiButton.addActionListener(event -> service.createPairingDialogController().showDialog());
  }

  private void initTable() {
    myTable = new JBTable(new PhysicalDeviceTableModel());

    myTable.setDefaultRenderer(Device.class, new DeviceTableCellRenderer<>(Device.class));
    myTable.getEmptyText().setText("No physical devices added. Connect a device via USB cable.");
  }

  private void setLayout() {
    Component scrollPane = new JBScrollPane(myTable);

    GroupLayout layout = new GroupLayout(this);
    Group horizontalGroup = layout.createParallelGroup();

    if (myPairUsingWiFiButton != null) {
      horizontalGroup.addComponent(myPairUsingWiFiButton);
    }

    horizontalGroup.addComponent(scrollPane);

    Group verticalGroup = layout.createSequentialGroup();

    if (myPairUsingWiFiButton != null) {
      verticalGroup.addComponent(myPairUsingWiFiButton);
    }

    verticalGroup.addComponent(scrollPane);

    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    setLayout(layout);
  }

  private @NotNull List<@NotNull PhysicalDevice> addOfflineDevices(@NotNull List<@NotNull PhysicalDevice> onlineDevices) {
    Collection<PhysicalDevice> persistedDevices = myPhysicalTabPersistentStateComponentGetInstance.get().get();

    List<PhysicalDevice> devices = new ArrayList<>(onlineDevices.size() + persistedDevices.size());
    devices.addAll(onlineDevices);

    persistedDevices.stream()
      .filter(persistedDevice -> PhysicalDevices.indexOf(onlineDevices, persistedDevice) == -1)
      .forEach(devices::add);

    return devices;
  }

  private void setTableModel(@NotNull List<@NotNull PhysicalDevice> devices) {
    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(devices);
    model.addTableModelListener(event -> myPhysicalTabPersistentStateComponentGetInstance.get().set(model.getDevices()));

    Disposer.register(this, myNewPhysicalDeviceChangeListener.apply(model));

    assert myTable != null;
    myTable.setModel(model);
  }

  @Override
  public void dispose() {
  }

  @VisibleForTesting
  @NotNull Object getData() {
    assert myTable != null;

    return IntStream.range(0, myTable.getRowCount())
      .mapToObj(this::getRowAt)
      .collect(Collectors.toList());
  }

  @VisibleForTesting
  private @NotNull Object getRowAt(int viewRowIndex) {
    assert myTable != null;

    return IntStream.range(0, myTable.getColumnCount())
      .mapToObj(viewColumnIndex -> myTable.getValueAt(viewRowIndex, viewColumnIndex))
      .collect(Collectors.toList());
  }

  @VisibleForTesting
  @Nullable AbstractButton getPairUsingWiFiButton() {
    return myPairUsingWiFiButton;
  }
}
