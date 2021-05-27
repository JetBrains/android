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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.JBDimension;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.AbstractButton;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.GroupLayout.Group;
import javax.swing.JButton;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PhysicalDevicePanel extends JBPanel<PhysicalDevicePanel> implements Disposable {
  private final @Nullable Project myProject;
  private final @NotNull Function<@NotNull Project, @NotNull PairDevicesUsingWiFiService> myPairDevicesUsingWiFiServiceGetInstance;
  private final @NotNull Supplier<@NotNull PhysicalTabPersistentStateComponent> myPhysicalTabPersistentStateComponentGetInstance;
  private final @NotNull Function<@NotNull PhysicalDeviceTableModel, @NotNull Disposable> myNewPhysicalDeviceChangeListener;

  private @Nullable AbstractButton myPairUsingWiFiButton;
  private @Nullable Component mySeparator;
  private @Nullable AbstractButton myHelpButton;
  private final @NotNull PhysicalDeviceTable myTable;

  @VisibleForTesting
  static final class SetDevices implements FutureCallback<List<PhysicalDevice>> {
    private final @NotNull PhysicalDevicePanel myPanel;

    @VisibleForTesting
    SetDevices(@NotNull PhysicalDevicePanel panel) {
      myPanel = panel;
    }

    @Override
    public void onSuccess(@Nullable List<@NotNull PhysicalDevice> devices) {
      assert devices != null;
      myPanel.setDevices(myPanel.addOfflineDevices(devices));
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
         PhysicalDeviceTable::new,
         new PhysicalDeviceAsyncSupplier(project),
         SetDevices::new);
  }

  @VisibleForTesting
  PhysicalDevicePanel(@Nullable Project project,
                      @NotNull Function<@NotNull Project, @NotNull PairDevicesUsingWiFiService> pairDevicesUsingWiFiServiceGetInstance,
                      @NotNull Supplier<@NotNull PhysicalTabPersistentStateComponent> physicalTabPersistentStateComponentGetInstance,
                      @NotNull Function<@NotNull PhysicalDeviceTableModel, @NotNull Disposable> newPhysicalDeviceChangeListener,
                      @NotNull Function<@Nullable Project, @NotNull PhysicalDeviceTable> newPhysicalDeviceTable,
                      @NotNull PhysicalDeviceAsyncSupplier supplier,
                      @NotNull Function<@NotNull PhysicalDevicePanel, @NotNull FutureCallback<@Nullable List<@NotNull PhysicalDevice>>> newSetDevices) {
    super(null);

    myProject = project;
    myPairDevicesUsingWiFiServiceGetInstance = pairDevicesUsingWiFiServiceGetInstance;
    myPhysicalTabPersistentStateComponentGetInstance = physicalTabPersistentStateComponentGetInstance;
    myNewPhysicalDeviceChangeListener = newPhysicalDeviceChangeListener;

    initPairUsingWiFiButton();
    initSeparator();
    initHelpButton();
    myTable = newPhysicalDeviceTable.apply(project);
    setLayout();

    FutureUtils.addCallback(supplier.get(), EdtExecutorService.getInstance(), newSetDevices.apply(this));
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

  private void setLayout() {
    Component scrollPane = new JBScrollPane(myTable);

    GroupLayout layout = new GroupLayout(this);
    Group toolbarHorizontalGroup = layout.createSequentialGroup();

    if (myPairUsingWiFiButton != null) {
      toolbarHorizontalGroup
        .addComponent(myPairUsingWiFiButton)
        .addComponent(mySeparator);
    }

    toolbarHorizontalGroup.addComponent(myHelpButton);

    Group horizontalGroup = layout.createParallelGroup()
      .addGroup(toolbarHorizontalGroup)
      .addComponent(scrollPane);

    Group toolbarVerticalGroup = layout.createParallelGroup(Alignment.CENTER);

    if (myPairUsingWiFiButton != null) {
      toolbarVerticalGroup
        .addComponent(myPairUsingWiFiButton)
        .addComponent(mySeparator);
    }

    toolbarVerticalGroup.addComponent(myHelpButton);

    Group verticalGroup = layout.createSequentialGroup()
      .addGroup(toolbarVerticalGroup)
      .addComponent(scrollPane);

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

  private void setDevices(@NotNull List<@NotNull PhysicalDevice> devices) {
    PhysicalDeviceTableModel model = myTable.getModel();

    model.addTableModelListener(event -> myPhysicalTabPersistentStateComponentGetInstance.get().set(model.getDevices()));
    model.setDevices(devices);

    Disposer.register(this, myNewPhysicalDeviceChangeListener.apply(model));
  }

  @Override
  public void dispose() {
  }

  @VisibleForTesting
  @Nullable AbstractButton getPairUsingWiFiButton() {
    return myPairUsingWiFiButton;
  }

  @VisibleForTesting
  @NotNull PhysicalDeviceTable getTable() {
    return myTable;
  }
}
