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

import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.idea.adb.wireless.PairDevicesUsingWiFiService;
import com.android.tools.idea.concurrency.FutureUtils;
import com.android.tools.idea.devicemanager.DetailsPanel;
import com.android.tools.idea.devicemanager.DeviceManagerFutureCallback;
import com.android.tools.idea.devicemanager.DevicePanel;
import com.android.tools.idea.devicemanager.Devices;
import com.android.tools.idea.wearpairing.WearPairingManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.JBDimension;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.AbstractButton;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.GroupLayout.Group;
import javax.swing.JButton;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PhysicalDevicePanel extends DevicePanel {
  private final @NotNull Disposable myParent;
  private final @NotNull Function<Project, PairDevicesUsingWiFiService> myPairDevicesUsingWiFiServiceGetInstance;
  private final @NotNull WearPairingManager myManager;
  private final @NotNull Supplier<PhysicalTabPersistentStateComponent> myPhysicalTabPersistentStateComponentGetInstance;
  private final @NotNull BiFunction<PhysicalDeviceTableModel, Project, Disposable> myNewPhysicalDeviceChangeListener;
  private final @NotNull BiFunction<PhysicalDevice, Project, DetailsPanel> myNewPhysicalDeviceDetailsPanel;

  private @Nullable AbstractButton myPairUsingWiFiButton;
  private @Nullable Component mySeparator;
  private @Nullable AbstractButton myHelpButton;

  public PhysicalDevicePanel(@Nullable Project project, @NotNull Disposable parent) {
    this(project,
         parent,
         PairDevicesUsingWiFiService::getInstance,
         WearPairingManager.getInstance(),
         PhysicalTabPersistentStateComponent::getInstance,
         PhysicalDeviceChangeListener::new,
         PhysicalDeviceDetailsPanel::new,
         new PhysicalDeviceAsyncSupplier(project),
         PhysicalDevicePanel::newSetDevices);
  }

  @VisibleForTesting
  PhysicalDevicePanel(@Nullable Project project,
                      @NotNull Disposable parent,
                      @NotNull Function<Project, PairDevicesUsingWiFiService> pairDevicesUsingWiFiServiceGetInstance,
                      @NotNull WearPairingManager manager,
                      @NotNull Supplier<PhysicalTabPersistentStateComponent> physicalTabPersistentStateComponentGetInstance,
                      @NotNull BiFunction<PhysicalDeviceTableModel, Project, Disposable> newPhysicalDeviceChangeListener,
                      @NotNull BiFunction<PhysicalDevice, Project, DetailsPanel> newPhysicalDeviceDetailsPanel,
                      @NotNull PhysicalDeviceAsyncSupplier supplier,
                      @NotNull Function<PhysicalDevicePanel, FutureCallback<List<PhysicalDevice>>> newSetDevices) {
    super(project);

    myParent = parent;
    myPairDevicesUsingWiFiServiceGetInstance = pairDevicesUsingWiFiServiceGetInstance;
    myManager = manager;
    myPhysicalTabPersistentStateComponentGetInstance = physicalTabPersistentStateComponentGetInstance;
    myNewPhysicalDeviceChangeListener = newPhysicalDeviceChangeListener;
    myNewPhysicalDeviceDetailsPanel = newPhysicalDeviceDetailsPanel;

    initPairUsingWiFiButton();
    initSeparator();
    initHelpButton();
    initTable();
    initScrollPane();
    initDetailsPanelPanel();
    layOut();

    FutureUtils.addCallback(supplier.get(), EdtExecutorService.getInstance(), newSetDevices.apply(this));
    Disposer.register(parent, this);
  }

  @VisibleForTesting
  static @NotNull FutureCallback<List<PhysicalDevice>> newSetDevices(@NotNull PhysicalDevicePanel panel) {
    return new DeviceManagerFutureCallback<>(PhysicalDevicePanel.class, devices -> {
      assert devices != null;
      panel.setDevices(panel.addOfflineDevices(devices));
    });
  }

  private void initPairUsingWiFiButton() {
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

  private void initSeparator() {
    if (myPairUsingWiFiButton == null) {
      return;
    }

    Dimension size = new JBDimension(3, 20);

    mySeparator = new JSeparator(SwingConstants.VERTICAL);
    mySeparator.setPreferredSize(size);
    mySeparator.setMaximumSize(size);
  }

  private void initHelpButton() {
    myHelpButton = new CommonButton(AllIcons.Actions.Help);
    myHelpButton.addActionListener(event -> BrowserUtil.browse("https://d.android.com/r/studio-ui/device-manager/physical"));
  }

  @Override
  protected @NotNull JTable newTable() {
    return new PhysicalDeviceTable(this, myManager);
  }

  private @NotNull List<PhysicalDevice> addOfflineDevices(@NotNull List<PhysicalDevice> onlineDevices) {
    Collection<PhysicalDevice> persistedDevices = myPhysicalTabPersistentStateComponentGetInstance.get().get();

    List<PhysicalDevice> devices = new ArrayList<>(onlineDevices.size() + persistedDevices.size());
    devices.addAll(onlineDevices);

    persistedDevices.stream()
      .filter(persistedDevice -> Devices.indexOf(onlineDevices, persistedDevice.getKey()) == -1)
      .forEach(devices::add);

    return devices;
  }

  private void setDevices(@NotNull List<PhysicalDevice> devices) {
    PhysicalDeviceTableModel model = getTable().getModel();

    model.addTableModelListener(event -> myPhysicalTabPersistentStateComponentGetInstance.get().set(model.getDevices()));
    model.setDevices(devices);

    Disposer.register(myParent, myNewPhysicalDeviceChangeListener.apply(model, myProject));
  }

  @Override
  protected @NotNull DetailsPanel newDetailsPanel() {
    return myNewPhysicalDeviceDetailsPanel.apply(getTable().getSelectedDevice().orElseThrow(AssertionError::new), myProject);
  }

  @Nullable
  @VisibleForTesting
  AbstractButton getPairUsingWiFiButton() {
    return myPairUsingWiFiButton;
  }

  @NotNull
  PhysicalDeviceTable getTable() {
    return (PhysicalDeviceTable)myTable;
  }

  private void layOut() {
    GroupLayout layout = new GroupLayout(this);
    Group toolbarHorizontalGroup = layout.createSequentialGroup();

    if (myPairUsingWiFiButton != null) {
      toolbarHorizontalGroup
        .addGap(JBUIScale.scale(5))
        .addComponent(myPairUsingWiFiButton)
        .addGap(JBUIScale.scale(4))
        .addComponent(mySeparator);
    }

    toolbarHorizontalGroup.addComponent(myHelpButton);

    Group toolbarVerticalGroup = layout.createParallelGroup(Alignment.CENTER);

    if (myPairUsingWiFiButton != null) {
      toolbarVerticalGroup
        .addComponent(myPairUsingWiFiButton)
        .addComponent(mySeparator);
    }

    toolbarVerticalGroup.addComponent(myHelpButton);

    Group horizontalGroup = layout.createParallelGroup()
      .addGroup(toolbarHorizontalGroup)
      .addComponent(myDetailsPanelPanel);

    Group verticalGroup = layout.createSequentialGroup()
      .addGroup(toolbarVerticalGroup)
      .addComponent(myDetailsPanelPanel);

    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    setLayout(layout);
  }
}
