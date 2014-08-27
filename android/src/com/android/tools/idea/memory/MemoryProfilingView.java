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
import com.android.tools.idea.memory.actions.RecordingAction;
import com.android.tools.idea.memory.actions.ToggleDebugRender;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
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

public class MemoryProfilingView implements AndroidDebugBridge.IDeviceChangeListener, AndroidDebugBridge.IClientChangeListener {

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
  @NotNull
  private final JPanel myContentPane;
  @NotNull
  private final TimelineComponent myTimelineComponent;
  @NotNull
  private MemorySampler myMemorySampler;

  public MemoryProfilingView(@NotNull Project project) {
    myProject = project;
    myDeviceContext = new DeviceContext();

    TimelineData data = new TimelineData(2, SAMPLES);
    // Buffer at one and a half times the sample frequency.
    float bufferTimeInSeconds = SAMPLE_FREQUENCY_MS * 1.5f / 1000.f;
    float initialMax = 5.0f;
    float initialMarker = 2.0f;

    myContentPane = new JPanel(new BorderLayout());

    myTimelineComponent = new TimelineComponent(data, bufferTimeInSeconds, initialMax, initialMarker);

    myTimelineComponent.configureUnits("MB");

    myTimelineComponent.configureStream(0, "Allocated", new JBColor(0x78abd9, 0x78abd9));
    myTimelineComponent.configureStream(1, "Free", new JBColor(0xbaccdc, 0x51585c));

    myTimelineComponent
      .configureEvent(MemorySampler.TYPE_HPROF_REQUEST, MemorySampler.TYPE_HPROF_RESULT, 0, AndroidIcons.Ddms.ScreenCapture,
                      new JBColor(0x92ADC6, 0x718493), new JBColor(0x2B4E8C, 0xC7E5FF));
    myTimelineComponent.setBackground(BACKGROUND_COLOR);

    myMemorySampler = new MemorySampler(data, myProject, myDeviceContext, SAMPLE_FREQUENCY_MS);

    myContentPane.add(myTimelineComponent, BorderLayout.CENTER);

    JPanel panel = new JPanel(new GridLayout());
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, getToolbarActions(), false);
    panel.add(toolbar.getComponent());
    panel.setBorder(IdeBorderFactory.createBorder(SideBorder.RIGHT));

    myContentPane.add(panel, BorderLayout.WEST);


    // TODO: Handle case where no bridge can be found.
    AndroidDebugBridge bridge = AndroidSdkUtils.getDebugBridge(project);
    for (IDevice device : bridge.getDevices()) {
      findClient(device);
    }
    AndroidDebugBridge.addDeviceChangeListener(this);
    AndroidDebugBridge.addClientChangeListener(this);
  }

  @NotNull
  public ActionGroup getToolbarActions() {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(new RecordingAction(myMemorySampler));
    group.add(new MemorySnapshotAction(this));
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

  @NotNull
  public MemorySampler getMemorySampler() {
    return myMemorySampler;
  }

  @Override
  public void deviceConnected(IDevice device) {
    // Ignore.
  }

  @Override
  public void deviceDisconnected(IDevice device) {
    // Ignore.
  }

  @Override
  public void deviceChanged(IDevice device, int changeMask) {
    if ((changeMask & IDevice.CHANGE_CLIENT_LIST) != 0) {
      findClient(device);
    }
  }

  private void findClient(IDevice device) {
    String applicationName = getApplicationName();
    Client client = device.getClient(applicationName);
    myDeviceContext.fireClientSelected(client);
  }

  public void close() {
    myMemorySampler.stop();
    AndroidDebugBridge.removeClientChangeListener(this);
    AndroidDebugBridge.removeDeviceChangeListener(this);
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    toolWindowManager.unregisterToolWindow(MemoryProfilingToolWindowFactory.ID);
  }

  @Override
  public void clientChanged(Client client, int changeMask) {
    if ((changeMask & Client.CHANGE_NAME) != 0) {
      String name = client.getClientData().getClientDescription();
      if (name != null && name.equals(getApplicationName())) {
        myDeviceContext.fireClientSelected(client);
      }
    }
  }
}
