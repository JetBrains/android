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

import com.android.SdkConstants;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.ClientCellRenderer;
import com.android.tools.idea.ddms.DeviceRenderer;
import com.android.tools.idea.memory.actions.*;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.google.common.collect.Maps;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
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
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.android.tools.idea.startup.AndroidStudioSpecificInitializer.ENABLE_EXPERIMENTAL_ACTIONS;

public class MemoryMonitorView
  implements AndroidDebugBridge.IDeviceChangeListener, AndroidDebugBridge.IClientChangeListener, MemorySampler.MemorySamplerListener,
             HierarchyListener {

  /**
   * Maximum number of samples to keep in memory. We not only sample at {@code SAMPLE_FREQUENCY_MS} but we also receive
   * a sample on every GC.
   */
  public static final int SAMPLES = 2048;
  private static final Color BACKGROUND_COLOR = UIUtil.getTextFieldBackground();
  private static final int SAMPLE_FREQUENCY_MS = 500;
  @NotNull
  private final Project myProject;
  private final AndroidDebugBridge myBridge;
  @NotNull
  private final Map<String, String> myPreferredClients;
  public boolean myIgnoreActionEvents;
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
  @NotNull
  private MemorySamplerTask myMemorySamplerTask;

  public MemoryMonitorView(@NotNull Project project) {
    $$$setupUI$$$(); // See IDEA-67765

    myProject = project;
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

    myMemorySampler = new MemorySampler(myData, SAMPLE_FREQUENCY_MS);
    myMemorySampler.addListener(this);

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, getToolbarActions(), false);
    myToolbarPanel.add(toolbar.getComponent());
    myToolbarPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.RIGHT));

    myContentPane.addHierarchyListener(this);

    myMemorySamplerTask = new MemorySamplerTask(project, myMemorySampler);

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
        if (myIgnoreActionEvents) return;

        Client client = (Client)myClientCombo.getSelectedItem();
        myPreferredClients.put(client.getDevice().getName(), client.getClientData().getClientDescription());
        myMemorySampler.setClient(client);
      }
    });

    myClientCombo.setRenderer(new ClientCellRenderer("No Debuggable Applications"));
    Dimension size = myClientCombo.getMinimumSize();
    myClientCombo.setMinimumSize(new Dimension(250, size.height));
  }

  private void initializeDeviceCombo() {
    myDeviceCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        if (myIgnoreActionEvents) return;

        updateClientCombo();
      }
    });

    myDeviceCombo.setRenderer(new DeviceRenderer.DeviceComboBoxRenderer("No Connected Devices"));
    Dimension size = myDeviceCombo.getMinimumSize();
    myDeviceCombo.setMinimumSize(new Dimension(200, size.height));
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

    if (Boolean.getBoolean(ENABLE_EXPERIMENTAL_ACTIONS)) {
      group.add(new RecordingAction(myMemorySampler));
      group.add(new MemorySnapshotAction(myMemorySampler));
    }
    group.add(new GcAction(myMemorySampler));
    group.add(new CloseMemoryMonitorWindow(this));

    if (Boolean.getBoolean("studio.profiling.debug")) {
      group.addSeparator();
      group.add(new ToggleDebugRender(myTimelineComponent));
    }

    return group;
  }

  @Nullable
  private String getApplicationName() {
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

    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    ToolWindow toolWindow = toolWindowManager.getToolWindow(MemoryMonitorToolWindowFactory.ID);
    toolWindow.hide(null);
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
        if ((changeMask & IDevice.CHANGE_CLIENT_LIST) != 0) {
          updateClientCombo();
        }
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
    myIgnoreActionEvents = true;

    boolean update = true;
    IDevice selected = (IDevice)myDeviceCombo.getSelectedItem();
    myDeviceCombo.removeAllItems();
    for (IDevice device : myBridge.getDevices()) {
      myDeviceCombo.addItem(device);
      if (selected == device) {
        myDeviceCombo.setSelectedItem(device);
        update = false;
      }
    }

    if (update) {
      updateClientCombo();
    }

    myIgnoreActionEvents = false;
  }

  private void updateClientCombo() {
    myIgnoreActionEvents = true;

    IDevice device = (IDevice)myDeviceCombo.getSelectedItem();
    Client selected = (Client)myClientCombo.getSelectedItem();
    Client toSelect = selected;
    boolean update = true;
    myClientCombo.removeAllItems();
    if (device != null) {
      // Change the currently selected client if the user has a preference.
      String preferred = getPreferredClientForDevice(device.getName());
      if (preferred != null) {
        Client preferredClient = device.getClient(preferred);
        if (preferredClient != null) {
          toSelect = preferredClient;
        }
      }

      Client[] clients = device.getClients();
      Arrays.sort(clients, new ClientCellRenderer.ClientComparator());

      for (Client client : clients) {
        myClientCombo.addItem(client);
        if (client == toSelect) {
          myClientCombo.setSelectedItem(toSelect);
          update = toSelect != selected;
        }
      }
    }

    myIgnoreActionEvents = false;

    if (update) {
      selected = (Client)myClientCombo.getSelectedItem();
      myMemorySampler.setClient(selected);
    }
  }

  @Nullable
  private String getPreferredClientForDevice(String deviceName) {
    String client = myPreferredClients.get(deviceName);
    return client == null ? myCandidateClientName : client;
  }

  @Override
  public void onStart() {
  }

  @Override
  public void onStop() {
  }

  @Override
  public void onHprofCompleted(@NotNull byte[] data, @NotNull Client client) {
    File f;
    try {
      f = FileUtil.createTempFile("ddms", "." + SdkConstants.EXT_HPROF);
      FileUtil.writeToFile(f, data);
    }
    catch (IOException e) {
      return;
    }
    final VirtualFile vf = VfsUtil.findFileByIoFile(f, true);
    if (vf == null) {
      return;
    }
    OpenFileDescriptor descriptor = new OpenFileDescriptor(myProject, vf);
    FileEditorManager.getInstance(myProject).openEditor(descriptor, true);
  }

  @Override
  public void hierarchyChanged(HierarchyEvent hierarchyEvent) {
    if ((hierarchyEvent.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
      myMemorySamplerTask.exit();
      if (myContentPane.isShowing()) {
        myMemorySampler.start();
      }
      else {
        if (myMemorySampler.isRunning()) {
          myMemorySamplerTask = new MemorySamplerTask(myProject, myMemorySampler);
          ProgressManager.getInstance().run(myMemorySamplerTask);
        }
      }
    }
  }

  private static class MemorySamplerTask extends Task.Backgroundable {

    private final MemorySampler myMemorySampler;
    private final CountDownLatch myLatch;

    public MemorySamplerTask(@Nullable Project project, MemorySampler memorySampler) {
      super(project, "Monitoring Memory ...", true);
      myMemorySampler = memorySampler;
      myLatch = new CountDownLatch(1);
    }

    public void exit() {
      myLatch.countDown();
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(true);
      while (myMemorySampler.isRunning() && myLatch.getCount() > 0) {
        try {
          myLatch.await(200, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e) {
          break;
        }

        if (indicator.isCanceled()) {
          myMemorySampler.stop();
          break;
        }
      }
    }
  }
}
