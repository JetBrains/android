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

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.chartlib.EventData;
import com.android.tools.chartlib.TimelineComponent;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.ddms.actions.GcAction;
import com.android.tools.idea.ddms.actions.ToggleAllocationTrackingAction;
import com.android.tools.idea.ddms.hprof.DumpHprofAction;
import com.android.tools.idea.monitor.BaseMonitorView;
import com.android.tools.idea.monitor.DeviceSampler;
import com.android.tools.idea.monitor.TimelineEventListener;
import com.android.tools.idea.monitor.actions.RecordingAction;
import com.android.tools.idea.monitor.memory.actions.ToggleDebugRender;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.HierarchyListener;

public class MemoryMonitorView extends BaseMonitorView
  implements TimelineEventListener, HierarchyListener, DeviceContext.DeviceSelectionListener {
  /**
   * Maximum number of samples to keep in memory. We not only sample at {@code SAMPLE_FREQUENCY_MS} but we also receive
   * a sample on every GC.
   */
  private static final Color BACKGROUND_COLOR = UIUtil.getTextFieldBackground();
  private static final int SAMPLE_FREQUENCY_MS = 500;
  @NotNull private final DeviceContext myDeviceContext;
  @NotNull private final TimelineComponent myTimelineComponent;
  @NotNull private MemorySampler myMemorySampler;
  private final EventData myEvents;

  public static final int EVENT_HPROF = 1;
  public static final int EVENT_ALLOC = 2;

  public MemoryMonitorView(@NotNull Project project, @NotNull DeviceContext deviceContext) {
    super(project);
    myDeviceContext = deviceContext;

    // Buffer at one and a half times the sample frequency.
    float bufferTimeInSeconds = SAMPLE_FREQUENCY_MS * 1.5f / 1000.f;
    float initialMax = 5.0f;
    float initialMarker = 2.0f;

    myMemorySampler = new MemorySampler(SAMPLE_FREQUENCY_MS);
    myMemorySampler.addListener(this);

    myEvents = new EventData();
    myTimelineComponent =
      new TimelineComponent(myMemorySampler.getTimelineData(), myEvents, bufferTimeInSeconds, initialMax, Float.MAX_VALUE, initialMarker);

    myTimelineComponent.configureUnits("MB");
    myTimelineComponent.configureStream(0, "Allocated", new JBColor(0x78abd9, 0x78abd9));
    myTimelineComponent.configureStream(1, "Free", new JBColor(0xbaccdc, 0x51585c));
    myTimelineComponent
      .configureEvent(EVENT_HPROF, 0, AndroidIcons.Ddms.DumpHprof, new JBColor(0x92ADC6, 0x718493), new JBColor(0x2B4E8C, 0xC7E5FF), false);
    myTimelineComponent
      .configureEvent(EVENT_ALLOC, 0, AndroidIcons.Ddms.AllocationTracker, new JBColor(0x92ADC6, 0x718493), new JBColor(0x2B4E8C, 0xC7E5FF),
                      true);

    myTimelineComponent.configureType(DeviceSampler.TYPE_DATA, TimelineComponent.Style.SOLID);
    myTimelineComponent.configureType(DeviceSampler.TYPE_TIMEOUT, TimelineComponent.Style.DASHED);
    myTimelineComponent.setBackground(BACKGROUND_COLOR);

    addOverlayText(PAUSED_LABEL, 0);

    setViewComponent(myTimelineComponent);

    myDeviceContext.addListener(this, project);
  }

  @Override
  @NotNull
  public ActionGroup getToolbarActions() {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(new RecordingAction(this));
    group.add(new Separator());
    group.add(new GcAction(myDeviceContext));
    group.add(new DumpHprofAction(myProject, myDeviceContext, myEvents));
    group.add(new ToggleAllocationTrackingAction(myDeviceContext, myEvents));

    if (Boolean.getBoolean("studio.profiling.debug")) {
      group.addSeparator();
      group.add(new ToggleDebugRender(myTimelineComponent));
    }

    return group;
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
  public void setPaused(boolean paused) {
    myMemorySampler.setIsPaused(paused);
    setOverlayEnabled(PAUSED_LABEL, paused);
    myTimelineComponent.setUpdateData(!paused);
  }

  @Override
  public boolean isPaused() {
    return myMemorySampler.getIsPaused();
  }

  @NotNull
  @Override
  public String getDescription() {
    return "memory usage";
  }

  @Override
  protected DeviceSampler getSampler() {
    return myMemorySampler;
  }
}
