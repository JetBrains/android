/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.ddms;

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.ClientCellRenderer.ClientComparator;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.FutureCallback;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.IntStream;

public class DevicePanel implements AndroidDebugBridge.IDeviceChangeListener, AndroidDebugBridge.IDebugBridgeChangeListener,
                                    AndroidDebugBridge.IClientChangeListener, Disposable {
  private static final Logger LOG = Logger.getInstance(DevicePanel.class);

  private JPanel myPanel;

  private final DeviceContext myDeviceContext;
  @Nullable private AndroidDebugBridge myBridge;

  @NotNull private final Project myProject;
  @NotNull private final Map<String, String> myPreferredClients;
  private boolean myIgnoringActionEvents;

  private JComboBox<IDevice> myDeviceCombo;
  private JComboBox<Client> myClientCombo;
  private final NullableLazyValue<String> myCandidateClientName = new NullableLazyValue<String>() {
    @Nullable
    @Override
    protected String compute() {
      return getApplicationName();
    }
  };
  private DeviceRenderer.DeviceComboBoxRenderer myDeviceRenderer;

  public DevicePanel(@NotNull Project project, @NotNull DeviceContext context) {
    myProject = project;
    myDeviceContext = context;
    myPreferredClients = Maps.newHashMap();
    Disposer.register(myProject, this);

    AndroidDebugBridge.addDeviceChangeListener(this);
    AndroidDebugBridge.addClientChangeListener(this);
    AndroidDebugBridge.addDebugBridgeChangeListener(this);
  }

  private void createUIComponents() {
    initializeDeviceCombo();
    initializeClientCombo();
  }

  private void initializeDeviceCombo() {
    myDeviceCombo = new ComboBox<>();

    AccessibleContextUtil.setName(myDeviceCombo, "Devices");
    myDeviceCombo.addActionListener(actionEvent -> {
      if (myIgnoringActionEvents) return;

      updateClientCombo();
      Object sel = myDeviceCombo.getSelectedItem();
      IDevice device = (sel instanceof IDevice) ? (IDevice)sel : null;
      myDeviceContext.fireDeviceSelected(device);
    });

    boolean showSerial = false;
    if (myBridge != null) {
      showSerial = DeviceRenderer
        .shouldShowSerialNumbers(Arrays.asList(myBridge.getDevices()));
    }

    myDeviceRenderer = new DeviceRenderer.DeviceComboBoxRenderer("No Connected Devices",
                                                                 showSerial,
                                                                 new DeviceNamePropertiesFetcher(
                                                                   new FutureCallback<DeviceNameProperties>() {
                                                                     @Override
                                                                     public void onSuccess(@Nullable DeviceNameProperties result) {
                                                                       updateDeviceCombo();
                                                                     }

                                                                     @Override
                                                                     public void onFailure(@NotNull Throwable t) {
                                                                       LOG.warn("Error retrieving device name properties", t);
                                                                     }
                                                                   }, this));
    myDeviceCombo.setRenderer(myDeviceRenderer);
    Dimension size = myDeviceCombo.getMinimumSize();
    myDeviceCombo.setMinimumSize(new Dimension(200, size.height));
  }

  private void initializeClientCombo() {
    myClientCombo = new ComboBox<>();

    AccessibleContextUtil.setName(myClientCombo, "Processes");
    myClientCombo.setName("Processes");
    myClientCombo.addActionListener(actionEvent -> {
      if (myIgnoringActionEvents) return;

      Client client = (Client)myClientCombo.getSelectedItem();
      if (client != null) {
        myPreferredClients.put(client.getDevice().getName(), client.getClientData().getClientDescription());
      }
      myDeviceContext.fireClientSelected(client);
    });

    myClientCombo.setRenderer(new ClientCellRenderer("No Debuggable Processes"));
    Dimension size = myClientCombo.getMinimumSize();
    myClientCombo.setMinimumSize(new Dimension(250, size.height));
  }

  @NotNull
  public JComboBox<IDevice> getDeviceComboBox() {
    return myDeviceCombo;
  }

  public void selectDevice(IDevice device) {
    myDeviceCombo.setSelectedItem(device);
  }

  @NotNull
  public Component getClientComboBox() {
    return myClientCombo;
  }

  public void selectClient(Client client) {
    myClientCombo.setSelectedItem(client);
  }

  @Nullable
  private String getApplicationName() {
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      AndroidModuleInfo moduleInfo = AndroidModuleInfo.getInstance(module);
      if (moduleInfo != null) {
        String pkg = moduleInfo.getPackage();
        if (pkg != null) {
          return pkg;
        }
      }
    }
    return null;
  }

  @Override
  public void dispose() {
    if (myBridge != null) {
      AndroidDebugBridge.removeDeviceChangeListener(this);
      AndroidDebugBridge.removeClientChangeListener(this);
      AndroidDebugBridge.removeDebugBridgeChangeListener(this);

      myBridge = null;
    }
  }

  public JPanel getComponent() {
    return myPanel;
  }

  @Override
  public void bridgeChanged(final AndroidDebugBridge bridge) {
    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(() -> UIUtil.invokeLaterIfNeeded(() -> {
      myBridge = bridge;
      updateDeviceCombo();
    }));
  }

  @VisibleForTesting
  void setBridge(@NotNull AndroidDebugBridge bridge) {
    myBridge = bridge;
  }

  @Override
  public void deviceConnected(@NotNull final IDevice device) {
    LOG.info("Device connected: " + device.getName());
    UIUtil.invokeLaterIfNeeded(this::updateDeviceCombo);
  }

  @Override
  public void deviceDisconnected(@NotNull final IDevice device) {
    LOG.info("Device disconnected: " + device.getName());
    UIUtil.invokeLaterIfNeeded(this::updateDeviceCombo);
  }

  @Override
  public void deviceChanged(@NotNull final IDevice device, final int changeMask) {
    UIUtil.invokeLaterIfNeeded(() -> {
      if ((changeMask & IDevice.CHANGE_CLIENT_LIST) != 0) {
        updateClientCombo();
      }
      else if ((changeMask & IDevice.CHANGE_STATE) != 0) {
        updateDeviceCombo();
      }
      myDeviceContext.fireDeviceChanged(device, changeMask);
    });
  }

  @Override
  public void clientChanged(@NotNull Client client, int changeMask) {
    if ((changeMask & Client.CHANGE_NAME) != 0) {
      ApplicationManager.getApplication().invokeLater(this::updateClientCombo);
    }
  }

  @VisibleForTesting
  void updateDeviceCombo() {
    if (myBridge == null) {
      return;
    }

    myIgnoringActionEvents = true;

    IDevice selectedDevice = (IDevice)myDeviceCombo.getSelectedItem();
    myDeviceCombo.removeAllItems();

    List<IDevice> devices = Arrays.asList(myBridge.getDevices());

    devices.forEach(device -> myDeviceCombo.addItem(device));
    myDeviceRenderer.setShowSerial(DeviceRenderer.shouldShowSerialNumbers(devices));

    Optional<IDevice> optionalDevice = IntStream.range(0, myDeviceCombo.getItemCount())
      .mapToObj(i -> myDeviceCombo.getItemAt(i))
      .filter(device -> equals(device, selectedDevice))
      .findFirst();

    if (optionalDevice.isPresent()) {
      IDevice device = optionalDevice.get();

      myDeviceCombo.setSelectedItem(device);
      myDeviceContext.fireDeviceSelected(device);
    }
    else {
      myDeviceCombo.addItem(selectedDevice);
      myDeviceCombo.setSelectedItem(selectedDevice);

      myDeviceContext.fireDeviceSelected(selectedDevice);
    }

    updateClientCombo();
    myIgnoringActionEvents = false;
  }

  private static boolean equals(@NotNull IDevice device1, @Nullable IDevice device2) {
    if (device2 == null) {
      return false;
    }

    boolean device1Emulator = device1.isEmulator();

    if (device1Emulator != device2.isEmulator()) {
      return false;
    }

    if (device1Emulator) {
      return Objects.equals(device1.getAvdName(), device2.getAvdName());
    }

    return device1.getSerialNumber().equals(device2.getSerialNumber());
  }

  private void updateClientCombo() {
    myIgnoringActionEvents = true;

    IDevice device = (IDevice)myDeviceCombo.getSelectedItem();
    Client selected = (Client)myClientCombo.getSelectedItem();
    Client toSelect = null;
    boolean update = true;
    myClientCombo.removeAllItems();
    if (device != null) {
      // Change the currently selected client if the user has a preference.
      String preferred = getPreferredClient(device.getName());
      if (preferred != null) {
        Client preferredClient = device.getClient(preferred);
        if (preferredClient != null) {
          toSelect = preferredClient;
        }
      }

      List<Client> clients = Lists.newArrayList(device.getClients());
      // There's a chance we got this update because a client we were debugging just crashed or was
      // closed. At this point we have our old handle to it but it's not in the client list
      // reported by the phone anymore. We still want to keep it in the list though, so the user
      // can look over any final error messages / profiling states.
      // However, we might get here because our device changed, so discard selected clients that
      // come from another device
      boolean selectedClientDied = selected != null && selected.getDevice() == device && !clients.contains(selected);
      if (selectedClientDied) {
        if (toSelect == null) {
          toSelect = selected;
        }
        clients.add(selected);
      }
      clients.sort(new ClientComparator());

      for (Client client : clients) {
        //noinspection unchecked
        myClientCombo.addItem(client);
      }
      myClientCombo.setSelectedItem(toSelect);
      update = toSelect != selected;
    }

    myIgnoringActionEvents = false;

    if (update) {
      myDeviceContext.fireClientSelected((Client)myClientCombo.getSelectedItem());
    }
  }

  @VisibleForTesting
  void setIgnoringActionEvents(boolean ignoringActionEvents) {
    myIgnoringActionEvents = ignoringActionEvents;
  }

  @Nullable
  private String getPreferredClient(@NotNull String device) {
    String client = myPreferredClients.get(device);
    return client == null ? myCandidateClientName.getValue() : client;
  }

  @VisibleForTesting
  void putPreferredClient(@NotNull String device, @NotNull String client) {
    myPreferredClients.put(device, client);
  }
}
