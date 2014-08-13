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
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.ddms.actions.GcAction;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.SideBorder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class MemoryProfilingView extends ToolWindowManagerAdapter {

  private static final Color BACKGROUND_COLOR = UIUtil.getTextFieldBackground();

  private static final int SAMPLE_FREQUENCY_MS = 500;
  /**
   * Maximum number of samples to keep in memory. We not only sample at {@code SAMPLE_FREQUENCY_MS} but we also receive
   * a sample on every GC.
   */
  public static final int SAMPLES = 1024;

  private final ToolWindowManagerEx myToolWindowManager;
  private final Project myProject;
  private final AndroidDebugBridge myBridge;
  private final DeviceContext myDeviceContext;
  private final JPanel myContentPane;
  private boolean myVisible;
  private ToolWindow myToolWindow;
  private MemorySampler myMemorySampler;
  private TimelineComponent myTimelineComponent;
  private TimelineData myData;
  private String myApplicationName;

  public MemoryProfilingView(Project project, ToolWindow toolWindow) {
    myProject = project;
    myToolWindowManager = ToolWindowManagerEx.getInstanceEx(project);
    myToolWindow = toolWindow;
    myDeviceContext = new DeviceContext();

    myData = new TimelineData(
      new TimelineData.Stream[]{new TimelineData.Stream("Allocated", new JBColor(new Color(0x78abd9), new Color(0x78abd9))),
        new TimelineData.Stream("Free", new JBColor(new Color(0xbaccdc), new Color(0x51585c)))}, SAMPLES, "MB");
    // Buffer at one and a half times the sample frequency.
    float bufferTimeInSeconds = SAMPLE_FREQUENCY_MS * 1.5f / 1000.f;
    float initialMax = 5.0f;
    float initialMarker = 2.0f;

    myContentPane = new JPanel(new BorderLayout());

    myTimelineComponent = new TimelineComponent(myData, bufferTimeInSeconds, initialMax, initialMarker);
    myContentPane.add(myTimelineComponent, BorderLayout.CENTER);
    myBridge = AndroidSdkUtils.getDebugBridge(project);
    myToolWindowManager.addToolWindowManagerListener(this);

    JPanel panel = new JPanel(new GridLayout());
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, getToolbarActions(), false);
    panel.add(toolbar.getComponent());
    panel.setBorder(IdeBorderFactory.createBorder(SideBorder.RIGHT));

    myContentPane.add(panel, BorderLayout.WEST);
    myContentPane.setBackground(BACKGROUND_COLOR);

    stateChanged();
    reset();
  }

  @NotNull
  public ActionGroup getToolbarActions() {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(new GcAction(myDeviceContext));

    return group;
  }

  @Override
  public void stateChanged() {
    boolean isRegistered = myToolWindowManager.getToolWindow(MemoryProfilingToolWindowFactory.ID) != null;
    boolean disposed = myToolWindow.isDisposed();
    boolean visible = !disposed && isRegistered && myToolWindow.isVisible();
    if (visible != myVisible || disposed) {
      if (myMemorySampler != null) {
        myMemorySampler.stop();
        myMemorySampler = null;
      }

      if (visible) {
        reset();
        myMemorySampler = new MemorySampler(myApplicationName, myData, myBridge, myDeviceContext, SAMPLE_FREQUENCY_MS);
      }
      myVisible = visible;
    }
  }

  private void reset() {
    myApplicationName = getApplicationName();
    myData.clear();
    myTimelineComponent.reset();
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
}
