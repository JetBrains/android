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

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.ClientCellRenderer.ClientComparator;
import com.android.tools.idea.ddms.DeviceRenderer.DeviceComboBoxRenderer;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.utils.CharSequences;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.ui.PopupMenuListenerAdapter;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import javax.swing.JComboBox;
import javax.swing.event.PopupMenuEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DevicePanel implements AndroidDebugBridge.IDeviceChangeListener, AndroidDebugBridge.IDebugBridgeChangeListener,
                                    AndroidDebugBridge.IClientChangeListener, Disposable {
  private static final Logger LOG = Logger.getInstance(DevicePanel.class);

  private final DeviceContext myDeviceContext;
  @Nullable
  private final IDevice myInitialDevice;
  @Nullable private AndroidDebugBridge myBridge;

  @NotNull private final Project myProject;

  @NotNull
  private final DeviceNamePropertiesProvider myProvider;

  @NotNull private final Map<String, String> myPreferredClients;
  private boolean myIgnoringActionEvents;

  private DeviceComboBox myDeviceCombo;
  private JComboBox<Client> myProcessComboBox;
  private final NullableLazyValue<String> myCandidateClientName = new NullableLazyValue<String>() {
    @Nullable
    @Override
    protected String compute() {
      return getApplicationName();
    }
  };

  public DevicePanel(@NotNull Project project, @NotNull DeviceContext deviceContext) {
    this(project, deviceContext, null);
  }

  /**
   * A constructor that takes an initial {@link IDevice} to connect to when the {@link DeviceComboBox} is initialized.
   * If the device is not already in the combo, it is added and selected. This allows clients to restore  the state of a previously selected
   * device.
   */
  public DevicePanel(@NotNull Project project, @NotNull DeviceContext deviceContext, @Nullable IDevice initialDevice) {
    this(project, deviceContext, new MyDeviceComboBox(), new MyProcessComboBox(), initialDevice);
  }

  @VisibleForTesting
  DevicePanel(@NotNull Project project,
              @NotNull DeviceContext deviceContext,
              @NotNull DeviceComboBox deviceComboBox,
              @NotNull ComboBox<Client> processComboBox,
              @Nullable IDevice initialDevice) {
    myProject = project;
    myProvider = new DeviceNamePropertiesFetcher(myProject);
    myDeviceContext = deviceContext;
    myPreferredClients = new HashMap<>();
    myInitialDevice = initialDevice;

    initializeDeviceCombo(deviceComboBox);
    initializeProcessComboBox(processComboBox);

    AndroidDebugBridge.addDeviceChangeListener(this);
    AndroidDebugBridge.addClientChangeListener(this);
    AndroidDebugBridge.addDebugBridgeChangeListener(this);

    Disposer.register(myProject, this);
  }

  abstract static class DeviceComboBox extends ComboBox<IDevice> implements Disposable {
    DeviceComboBox() {
      AccessibleContextUtil.setName(this, "Devices");
    }

    abstract void initRenderer(@NotNull FutureCallback<DeviceNameProperties> callback);

    abstract void setSerialNumbersVisible(boolean visible);
  }

  private static final class MyDeviceComboBox extends DeviceComboBox {
    private DeviceNamePropertiesFetcher myFetcher;
    private DeviceComboBoxRenderer myRenderer;

    @Override
    void initRenderer(@NotNull FutureCallback<DeviceNameProperties> callback) {
      myFetcher = new DeviceNamePropertiesFetcher(this, callback);
      myRenderer = new DeviceComboBoxRenderer("No connected devices", false, myFetcher);

      setRenderer(myRenderer);
    }

    @Override
    public void dispose() {
      Disposer.dispose(myFetcher);
    }

    @Override
    void setSerialNumbersVisible(boolean visible) {
      myRenderer.setShowSerial(visible);
    }
  }

  private void initializeDeviceCombo(@NotNull DeviceComboBox deviceComboBox) {
    deviceComboBox.initRenderer(new MyFutureCallback());

    deviceComboBox.addActionListener(event -> {
      if (myIgnoringActionEvents) {
        return;
      }

      updateProcessComboBox();

      Object device = myDeviceCombo.getSelectedItem();
      myDeviceContext.fireDeviceSelected(device instanceof IDevice ? (IDevice)device : null);
    });

    myDeviceCombo = deviceComboBox;
  }

  private final class MyFutureCallback implements FutureCallback<DeviceNameProperties> {
    @Override
    public void onSuccess(@Nullable DeviceNameProperties properties) {
      updateDeviceCombo();
    }

    @Override
    public void onFailure(@NotNull Throwable throwable) {
      LOG.warn("Error retrieving device name properties", throwable);
    }
  }

  private static final class MyProcessComboBox extends ComboBox<Client> {
    private MyProcessComboBox() {
      setRenderer(new ClientCellRenderer("No debuggable processes"));
    }
  }

  private void initializeProcessComboBox(@NotNull ComboBox<Client> processComboBox) {
    processComboBox.setName("Processes");
    AccessibleContextUtil.setName(processComboBox, "Processes");

    processComboBox.addActionListener(event -> {
      if (myIgnoringActionEvents) {
        return;
      }

      Client client = (Client)myProcessComboBox.getSelectedItem();

      if (client != null) {
        myPreferredClients.put(client.getDevice().getName(), client.getClientData().getClientDescription());
      }

      myDeviceContext.fireClientSelected(client);
    });

    myProcessComboBox = processComboBox;
    // We don't update the combo when it's open so we must do it when it closes in case we missed changes.
    myProcessComboBox.addPopupMenuListener(new PopupMenuListenerAdapter() {
      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        updateProcessComboBox();
      }
    });
    // Don't use ComboboxSpeedSearch.installSpeedSearch() because it compares using SpeedSearchComparator#matchingFragments():
    //   matchingFragments("com.google.android.app", "cgaa") == true
    ComboboxSpeedSearch search = new ComboboxSpeedSearch(processComboBox, null) {
      @Override
      protected String getElementText(@NotNull Object element) {
        // Use the same client name that the renderer uses.
        return ClientCellRenderer.getClientName(((Client)element).getClientData());
      }

      @Override
      protected boolean compare(@NotNull String text, @Nullable String pattern) {
        return pattern != null && CharSequences.indexOfIgnoreCase(text, pattern, 0) >= 0;
      }
    };
    search.setupListeners();
  }

  @NotNull
  public JComboBox<IDevice> getDeviceComboBox() {
    return myDeviceCombo;
  }

  public void selectDevice(IDevice device) {
    myDeviceCombo.setSelectedItem(device);
  }

  @NotNull
  public JComboBox<Client> getClientComboBox() {
    return myProcessComboBox;
  }

  public void selectClient(Client client) {
    myProcessComboBox.setSelectedItem(client);
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
    AndroidDebugBridge.removeDeviceChangeListener(this);
    AndroidDebugBridge.removeClientChangeListener(this);
    AndroidDebugBridge.removeDebugBridgeChangeListener(this);
    myBridge = null;

    if (myDeviceCombo != null) {
      Disposer.dispose(myDeviceCombo);
      myDeviceCombo = null;
    }
  }

  @Override
  public void bridgeChanged(final AndroidDebugBridge bridge) {
    if (bridge == null) return;

    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(() -> UIUtil.invokeLaterIfNeeded(() -> {
      myBridge = bridge;
      updateDeviceCombo();

      if (myInitialDevice != null) {
        Optional<IDevice> optionalDevice = IntStream.range(0, myDeviceCombo.getItemCount())
          .mapToObj(i -> myDeviceCombo.getItemAt(i))
          .filter(device -> equals(device, myInitialDevice))
          .findFirst();
        if (optionalDevice.isPresent()) {
          myDeviceCombo.setSelectedItem(optionalDevice.get());
        }
        else {
          myDeviceCombo.addItem(myInitialDevice);
          myDeviceCombo.setSelectedItem(myInitialDevice);
        }
      }
    }));
  }

  @VisibleForTesting
  void setBridge(@NotNull AndroidDebugBridge bridge) {
    myBridge = bridge;
  }

  @Override
  public void deviceConnected(@NotNull final IDevice device) {
    LOG.info("Device connected: " + device.getName());
    if (device.isEmulator()) {
      // If the device is an emulator, wait until we load the AVD data before updating the device combo so that we properly identify the
      // device.
      device.getAvdData().addListener(() -> UIUtil.invokeLaterIfNeeded(this::updateDeviceCombo), directExecutor());
    } else {
      UIUtil.invokeLaterIfNeeded(this::updateDeviceCombo);
    }
  }

  @Override
  public void deviceDisconnected(@NotNull final IDevice device) {
    LOG.info("Device disconnected: " + device.getName());
    UIUtil.invokeLaterIfNeeded(this::updateDeviceCombo);
  }

  @Override
  public void deviceChanged(@NotNull final IDevice device, final int changeMask) {
    UIUtil.invokeLaterIfNeeded(() -> deviceChangedImpl(device, changeMask));
  }

  @VisibleForTesting
  void deviceChangedImpl(@NotNull IDevice device, int changeMask) {
    if ((changeMask & IDevice.CHANGE_CLIENT_LIST) != 0) {
      if (!myProcessComboBox.isPopupVisible()) {
        // Don't change the combo while it's open because it flickers. We will refresh when it closes anyway.
        updateProcessComboBox();
      }
    }
    else if ((changeMask & IDevice.CHANGE_STATE) != 0) {
      updateDeviceCombo();
    }

    myDeviceContext.fireDeviceChanged(device, changeMask);
  }

  @Override
  public void clientChanged(@NotNull Client client, int changeMask) {
    if ((changeMask & Client.CHANGE_NAME) != 0) {
      ApplicationManager.getApplication().invokeLater(this::updateProcessComboBox);
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

    myDeviceCombo.setSerialNumbersVisible(DeviceRenderer.shouldShowSerialNumbers(devices, myProvider));

    Optional<IDevice> optionalDevice = IntStream.range(0, myDeviceCombo.getItemCount())
      .mapToObj(i -> myDeviceCombo.getItemAt(i))
      .filter(device -> equals(device, selectedDevice))
      .findFirst();

    if (optionalDevice.isPresent()) {
      IDevice device = optionalDevice.get();

      myDeviceCombo.setSelectedItem(device);
      myDeviceContext.fireDeviceSelected(device);
    }
    else if (selectedDevice != null) {
      myDeviceCombo.addItem(selectedDevice);
      myDeviceCombo.setSelectedItem(selectedDevice);

      myDeviceContext.fireDeviceSelected(selectedDevice);
    }

    updateProcessComboBox();
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

  private void updateProcessComboBox() {
    if (myDeviceCombo == null) {
      return;
    }
    myIgnoringActionEvents = true;

    IDevice device = (IDevice)myDeviceCombo.getSelectedItem();
    Client selected = (Client)myProcessComboBox.getSelectedItem();
    Client toSelect = null;
    boolean update = true;
    myProcessComboBox.removeAllItems();
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
        if (!StringUtil.isEmpty(ClientCellRenderer.getClientName(client.getClientData()))) {
          // For some reason, some clients have no process name or package name. No point in showing empty cells in the combo
          myProcessComboBox.addItem(client);
        }
      }
      myProcessComboBox.setSelectedItem(toSelect);
      update = toSelect != selected;
    }

    myIgnoringActionEvents = false;

    if (update) {
      myDeviceContext.fireClientSelected((Client)myProcessComboBox.getSelectedItem());
    }
  }

  @VisibleForTesting
  void setIgnoringActionEvents(@SuppressWarnings("SameParameterValue") boolean ignoringActionEvents) {
    myIgnoringActionEvents = ignoringActionEvents;
  }

  @Nullable
  private String getPreferredClient(@NotNull String device) {
    String client = myPreferredClients.get(device);
    return client == null ? myCandidateClientName.getValue() : client;
  }

  @VisibleForTesting
  void putPreferredClient(@SuppressWarnings("SameParameterValue") @NotNull String device,
                          @SuppressWarnings("SameParameterValue") @NotNull String client) {
    myPreferredClients.put(device, client);
  }
}
