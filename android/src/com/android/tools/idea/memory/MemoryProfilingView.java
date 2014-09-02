/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.memory;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.ddms.actions.GcAction;
import com.android.tools.idea.memory.actions.CloseMemoryProfilingWindow;
import com.android.tools.idea.memory.actions.MemorySnapshotAction;
import com.android.tools.idea.memory.actions.RecordingAction;
import com.android.tools.idea.memory.actions.ToggleDebugRender;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.google.common.collect.Maps;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.SideBorder;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;

public class MemoryProfilingView implements AndroidDebugBridge.IDeviceChangeListener, AndroidDebugBridge.IClientChangeListener, Disposable {

  /**
   * Maximum number of samples to keep in memory. We not only sample at {@code SAMPLE_FREQUENCY_MS} but we also receive
   * a sample on every GC.
   */
  public static final int SAMPLES = 1024;
  private static final Color BACKGROUND_COLOR = UIUtil.getTextFieldBackground();
  private static final int SAMPLE_FREQUENCY_MS = 500;
  @NotNull
  private final Project myProject;
  @NotNull
  private final DeviceContext myDeviceContext;
  private final AndroidDebugBridge myBridge;
  @NotNull
  private final Map<String, String> myPreferredClients;
  public boolean myUserInitiatedInput;
  @NotNull
  private JPanel myContentPane;
  @NotNull
  private TimelineComponent myTimelineComponent;
  @NotNull
  private JPanel myToolbarPanel;
  @NotNull
  private JComboBox myDeviceCombo;
  @NotNull
  private JComboBox myClientCombo;
  private JPanel myTopPanel;
  @NotNull
  private MemorySampler myMemorySampler;
  @NotNull
  private TimelineData myData;
  @Nullable
  private String myCandidateClientName;

  public MemoryProfilingView(@NotNull Project project) {
    $$$setupUI$$$(); // See IDEA-67765

    myProject = project;
    myDeviceContext = new DeviceContext();
    myDeviceContext.addListener(new DeviceContextListener(), this);
    myPreferredClients = Maps.newHashMap();
    myCandidateClientName = getApplicationName();

    myTimelineComponent.configureUnits("MB");
    myTimelineComponent.configureStream(0, "Allocated", new JBColor(0x78abd9, 0x78abd9));
    myTimelineComponent.configureStream(1, "Free", new JBColor(0xbaccdc, 0x51585c));
    myTimelineComponent
      .configureEvent(MemorySampler.TYPE_HPROF_REQUEST, MemorySampler.TYPE_HPROF_RESULT, 0, AndroidIcons.Ddms.ScreenCapture,
                      new JBColor(0x92ADC6, 0x718493), new JBColor(0x2B4E8C, 0xC7E5FF));
    myTimelineComponent.setBackground(BACKGROUND_COLOR);
    myTopPanel.setBackground(BACKGROUND_COLOR);

    myMemorySampler = new MemorySampler(myData, myProject, SAMPLE_FREQUENCY_MS);

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, getToolbarActions(), false);
    myToolbarPanel.add(toolbar.getComponent());
    myToolbarPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.RIGHT));

    // TODO: Handle case where no bridge can be found.
    myBridge = AndroidSdkUtils.getDebugBridge(myProject);
    AndroidDebugBridge.addDeviceChangeListener(this);
    AndroidDebugBridge.addClientChangeListener(this);

    initializeDeviceCombo();
    initializeClientCombo();

    updateDeviceCombo();
  }

  private void initializeClientCombo() {
    myClientCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        Client client = (Client)myClientCombo.getSelectedItem();
        if (myUserInitiatedInput && client != null) {
          myPreferredClients.put(client.getDevice().getName(), client.getClientData().getClientDescription());
        }
        myDeviceContext.fireClientSelected(client);
      }
    });

    myClientCombo.setRenderer(new ListCellRendererWrapper<Client>() {
      @Override
      public void customize(JList list, Client value, int index, boolean selected, boolean hasFocus) {
        if (value != null) {
          String desc = value.getClientData().getClientDescription();
          setText(desc != null ? desc : value.toString());
        }
      }
    });
  }

  private void initializeDeviceCombo() {
    myDeviceCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        myDeviceContext.fireDeviceSelected((IDevice)myDeviceCombo.getSelectedItem());
      }
    });

    myDeviceCombo.setRenderer(new ListCellRendererWrapper<IDevice>() {
      @Override
      public void customize(JList list, IDevice value, int index, boolean selected, boolean hasFocus) {
        if (value != null) {
          setText(value.getName());
        }
      }
    });
  }

  private void $$$setupUI$$$() {
  }

  private void createUIComponents() {

    // Buffer at one and a half times the sample frequency.
    float bufferTimeInSeconds = SAMPLE_FREQUENCY_MS * 1.5f / 1000.f;
    float initialMax = 5.0f;
    float initialMarker = 2.0f;

    myData = new TimelineData(2, SAMPLES);
    myTimelineComponent = new TimelineComponent(myData, bufferTimeInSeconds, initialMax, initialMarker);
  }

  @NotNull
  public ActionGroup getToolbarActions() {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(new RecordingAction(myMemorySampler));
    group.add(new MemorySnapshotAction(myMemorySampler));
    group.add(new GcAction(myDeviceContext));
    group.add(new CloseMemoryProfilingWindow(this));

    if (Boolean.getBoolean("studio.profiling.debug")) {
      group.addSeparator();
      group.add(new ToggleDebugRender(myTimelineComponent));
    }

    return group;
  }

  @Nullable
  private String getApplicationName() {
    //TODO: Allow users to select the client to profile.
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      AndroidModuleInfo moduleInfo = AndroidModuleInfo.get(module);
      if (moduleInfo != null) {
        String pkg = moduleInfo.getPackage();
        if (pkg != null) {
          return pkg;
        }
      }
    }
    return null;
  }

  public JPanel getComponent() {
    return myContentPane;
  }

  public void close() {
    myMemorySampler.stop();
    AndroidDebugBridge.removeClientChangeListener(this);
    AndroidDebugBridge.removeDeviceChangeListener(this);
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    toolWindowManager.unregisterToolWindow(MemoryProfilingToolWindowFactory.ID);
    Disposer.dispose(this);
  }

  @Override
  public void deviceConnected(IDevice device) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        updateDeviceCombo();
      }
    });
  }

  @Override
  public void deviceDisconnected(IDevice device) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        updateDeviceCombo();
      }
    });
  }

  @Override
  public void deviceChanged(final IDevice device, final int changeMask) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        myDeviceContext.fireDeviceChanged(device, changeMask);
      }
    });
  }

  @Override
  public void clientChanged(Client client, int changeMask) {
    if ((changeMask & Client.CHANGE_NAME) != 0) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          updateClientCombo();
        }
      });
    }
  }

  private void updateDeviceCombo() {
    IDevice selected = (IDevice)myDeviceCombo.getSelectedItem();
    myDeviceCombo.removeAllItems();
    for (IDevice device : myBridge.getDevices()) {
      myDeviceCombo.addItem(device);
      if (selected == device) {
        myDeviceCombo.setSelectedItem(device);
      }
    }
  }

  private void updateClientCombo() {
    // Make sure selected events triggered by this method don't change the user preferences.
    myUserInitiatedInput = false;

    IDevice device = myDeviceContext.getSelectedDevice();
    Client selected = (Client)myClientCombo.getSelectedItem();
    myClientCombo.removeAllItems();
    if (device != null) {

      // Change the currently selected client if the user has a preference.
      String preferred = getPreferredClientForDevice(device.getName());
      if (preferred != null) {
        Client preferredClient = device.getClient(preferred);
        if (preferredClient != null) {
          selected = preferredClient;
        }
      }

      for (Client client : device.getClients()) {
        myClientCombo.addItem(client);
        if (client == selected) {
          myClientCombo.setSelectedItem(client);
        }
      }
    }

    myUserInitiatedInput = true;
  }

  @Nullable
  private String getPreferredClientForDevice(String deviceName) {
    String client = myPreferredClients.get(deviceName);
    return client == null ? myCandidateClientName : client;
  }

  @Override
  public void dispose() {
  }

  private class DeviceContextListener implements DeviceContext.DeviceSelectionListener {
    @Override
    public void deviceSelected(@Nullable IDevice device) {
      updateClientCombo();
    }

    @Override
    public void deviceChanged(@NotNull IDevice device, int changeMask) {
      if ((changeMask & IDevice.CHANGE_CLIENT_LIST) != 0) {
        updateClientCombo();
      }
    }

    @Override
    public void clientSelected(@Nullable Client client) {
      myMemorySampler.setClient(client);
    }
  }
}
