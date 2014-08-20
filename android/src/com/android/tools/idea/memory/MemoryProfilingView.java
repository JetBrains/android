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
import com.intellij.icons.AllIcons;
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
import icons.AndroidIcons;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class MemoryProfilingView extends ToolWindowManagerAdapter {

  /**
   * Maximum number of samples to keep in memory. We not only sample at {@code SAMPLE_FREQUENCY_MS} but we also receive
   * a sample on every GC.
   */
  public static final int SAMPLES = 1024;
  private static final Color BACKGROUND_COLOR = UIUtil.getTextFieldBackground();
  private static final int SAMPLE_FREQUENCY_MS = 500;
  @NotNull
  private final ToolWindowManagerEx myToolWindowManager;
  @NotNull
  private final Project myProject;
  @NotNull
  private final AndroidDebugBridge myBridge;
  @NotNull
  private final DeviceContext myDeviceContext;
  @NotNull
  private final JPanel myContentPane;
  @NotNull
  private final TimelineComponent myTimelineComponent;
  @NotNull
  private final TimelineData myData;
  private boolean myVisible;
  @NotNull
  private ToolWindow myToolWindow;
  @Nullable
  private MemorySampler myMemorySampler;
  @Nullable
  private String myApplicationName;

  public MemoryProfilingView(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    myProject = project;
    myToolWindowManager = ToolWindowManagerEx.getInstanceEx(project);
    myToolWindow = toolWindow;
    myDeviceContext = new DeviceContext();

    myData = new TimelineData(2, SAMPLES);
    // Buffer at one and a half times the sample frequency.
    float bufferTimeInSeconds = SAMPLE_FREQUENCY_MS * 1.5f / 1000.f;
    float initialMax = 5.0f;
    float initialMarker = 2.0f;

    myContentPane = new JPanel(new BorderLayout());

    myTimelineComponent = new TimelineComponent(myData, bufferTimeInSeconds, initialMax, initialMarker);

    myTimelineComponent.configureUnits("MB");

    myTimelineComponent.configureStream(0, "Allocated", new JBColor(0x78abd9, 0x78abd9));
    myTimelineComponent.configureStream(1, "Free", new JBColor(0xbaccdc, 0x51585c));

    myTimelineComponent
      .configureEvent(MemorySampler.TYPE_HPROF_REQUEST, MemorySampler.TYPE_HPROF_RESULT, 0, AndroidIcons.Ddms.ScreenCapture,
                      new JBColor(0x92ADC6, 0x718493), new JBColor(0x2B4E8C, 0xC7E5FF));

    myContentPane.add(myTimelineComponent, BorderLayout.CENTER);
    // TODO: Handle case where no bridge can be found.
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

    group.add(new MemorySnapshotAction(this));
    group.add(new GcAction(myDeviceContext));
    if (Boolean.getBoolean("studio.profiling.debug")) {
      group.add(new ToggleDebugRender());
    }

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
        myMemorySampler = new MemorySampler(myApplicationName, myData, myProject, myBridge, myDeviceContext, SAMPLE_FREQUENCY_MS);
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

  @Nullable
  public MemorySampler getMemorySampler() {
    return myMemorySampler;
  }

  private class ToggleDebugRender extends ToggleAction {
    public ToggleDebugRender() {
      super("Enable debug renderer", "Enables debug rendering", AllIcons.General.Debug);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myTimelineComponent.isDrawDebugInfo();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myTimelineComponent.setDrawDebugInfo(state);
    }
  }
}
