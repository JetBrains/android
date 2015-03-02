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
package com.android.tools.idea.monitor.memory;

import com.android.SdkConstants;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.ddms.actions.GcAction;
import com.android.tools.idea.ddms.actions.ToggleAllocationTrackingAction;
import com.android.tools.idea.ddms.hprof.DumpHprofAction;
import com.android.tools.idea.monitor.*;
import com.android.tools.idea.monitor.actions.RecordingAction;
import com.android.tools.idea.monitor.memory.actions.MemorySnapshotAction;
import com.android.tools.idea.monitor.memory.actions.ToggleDebugRender;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithActions;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.HierarchyListener;
import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.startup.AndroidStudioSpecificInitializer.ENABLE_EXPERIMENTAL_ACTIONS;

public class MemoryMonitorView extends BaseMonitorView
  implements TimelineEventListener, HierarchyListener, DeviceContext.DeviceSelectionListener {
  /**
   * Maximum number of samples to keep in memory. We not only sample at {@code SAMPLE_FREQUENCY_MS} but we also receive
   * a sample on every GC.
   */
  public static final int SAMPLES = 2048;
  private static final Color BACKGROUND_COLOR = UIUtil.getTextFieldBackground();
  private static final int SAMPLE_FREQUENCY_MS = 500;
  @NotNull private final DeviceContext myDeviceContext;
  @NotNull private TimelineComponent myTimelineComponent;
  @NotNull private MemorySampler myMemorySampler;

  public MemoryMonitorView(@NotNull Project project, @NotNull DeviceContext deviceContext, @NotNull DeviceMonitorStatus deviceMonitorStatus) {
    super(project, deviceMonitorStatus);
    myDeviceContext = deviceContext;

    // Buffer at one and a half times the sample frequency.
    float bufferTimeInSeconds = SAMPLE_FREQUENCY_MS * 1.5f / 1000.f;
    float initialMax = 5.0f;
    float initialMarker = 2.0f;

    TimelineData data = new TimelineData(2, SAMPLES);
    myTimelineComponent = new TimelineComponent(data, bufferTimeInSeconds, initialMax, Float.MAX_VALUE, initialMarker);

    myTimelineComponent.configureUnits("MB");
    myTimelineComponent.configureStream(0, "Allocated", new JBColor(0x78abd9, 0x78abd9));
    myTimelineComponent.configureStream(1, "Free", new JBColor(0xbaccdc, 0x51585c));
    myTimelineComponent
      .configureEvent(MemorySampler.TYPE_HPROF_REQUEST, MemorySampler.TYPE_HPROF_RESULT, 0, AndroidIcons.Ddms.ScreenCapture,
                      new JBColor(0x92ADC6, 0x718493), new JBColor(0x2B4E8C, 0xC7E5FF));
    myTimelineComponent.setBackground(BACKGROUND_COLOR);

    setComponent(myTimelineComponent);

    myMemorySampler = new MemorySampler(data, SAMPLE_FREQUENCY_MS);
    myMemorySampler.addListener(this);

    myContentPane.addHierarchyListener(this);

    myDeviceMonitorStatus.registerView(this);

    myDeviceContext.addListener(this, project);
  }

  @NotNull
  public ActionGroup getToolbarActions() {
    DefaultActionGroup group = new DefaultActionGroup();

    if (Boolean.getBoolean(ENABLE_EXPERIMENTAL_ACTIONS)) {
      group.add(new RecordingAction(myMemorySampler));
      group.add(new MemorySnapshotAction(myMemorySampler));
    }
    group.add(new GcAction(myDeviceContext));
    group.add(new DumpHprofAction(myDeviceContext));
    group.add(new ToggleAllocationTrackingAction(myDeviceContext));

    if (Boolean.getBoolean("studio.profiling.debug")) {
      group.addSeparator();
      group.add(new ToggleDebugRender(myTimelineComponent));
    }

    return group;
  }

  @NotNull
  public ComponentWithActions createComponent() {
    return new ComponentWithActions.Impl(getToolbarActions(), null, null, null, myContentPane);
  }

  @Override
  public void deviceSelected(@Nullable IDevice device) {

  }

  @Override
  public void deviceChanged(@NotNull IDevice device, int changeMask) {

  }

  @Override
  public void clientSelected(@Nullable Client c) {
    myMemorySampler.setClient(c);
  }

  @Override
  public void onStart() {
  }

  @Override
  public void onStop() {
  }

  @Override
  public void onEvent(@NotNull TimelineEvent event) {
    if (event instanceof MemorySampler.HprofDumpCompletedEvent && event.getData() != null) {
      File f;
      try {
        f = FileUtil.createTempFile("ddms", "." + SdkConstants.EXT_HPROF);
        FileUtil.writeToFile(f, event.getData());
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
  }

  @Override
  protected DeviceSampler getSampler() {
    return myMemorySampler;
  }
}
