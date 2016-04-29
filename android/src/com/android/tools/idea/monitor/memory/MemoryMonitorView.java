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

import com.android.tools.chartlib.TimelineComponent;
import com.android.tools.idea.actions.BrowserHelpAction;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.ddms.actions.GcAction;
import com.android.tools.idea.ddms.actions.ToggleAllocationTrackingAction;
import com.android.tools.idea.ddms.hprof.DumpHprofAction;
import com.android.tools.idea.monitor.BaseMonitorView;
import com.android.tools.idea.monitor.DeviceSampler;
import com.android.tools.idea.monitor.actions.RecordingAction;
import com.android.tools.idea.monitor.memory.actions.ToggleDebugRender;
import com.android.tools.idea.stats.UsageTracker;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyListener;

public class MemoryMonitorView extends BaseMonitorView<MemorySampler> implements HierarchyListener {
  /**
   * Maximum number of samples to keep in memory. We not only sample at {@code SAMPLE_FREQUENCY_MS} but we also receive
   * a sample on every GC.
   */
  private static final Color BACKGROUND_COLOR = UIUtil.getTextFieldBackground();
  private static final int SAMPLE_FREQUENCY_MS = 500;
  private static final float TIMELINE_BUFFER_TIME = SAMPLE_FREQUENCY_MS * 1.5f / 1000;
  private static final float TIMELINE_INITIAL_MAX = 5.0f;
  private static final float TIMELINE_ABSOLUTE_MAX = Float.MAX_VALUE;
  private static final float TIMELINE_INITIAL_MARKER_SEPARATION = 2.0f;

  public static final int EVENT_HPROF = 1;
  public static final int EVENT_ALLOC = 2;

  public MemoryMonitorView(@NotNull Project project, @NotNull DeviceContext deviceContext) {
    super(project, deviceContext, new MemorySampler(SAMPLE_FREQUENCY_MS), TIMELINE_BUFFER_TIME, TIMELINE_INITIAL_MAX, TIMELINE_ABSOLUTE_MAX,
          TIMELINE_INITIAL_MARKER_SEPARATION);

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

    setViewComponent(myTimelineComponent);
  }

  @Override
  @NotNull
  public ActionGroup getToolbarActions() {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(new RecordingAction(this));
    group.add(new Separator());
    group.add(new GcAction(myDeviceContext));
    group.add(new DumpHprofAction(myProject, myDeviceContext, myEvents));
    group.add(new ToggleAllocationTrackingAction(myProject, myDeviceContext, myEvents));
    group.add(new Separator());
    group.add(new BrowserHelpAction("Memory monitor", "http://developer.android.com/r/studio-ui/am-memory.html"));

    if (Boolean.getBoolean("studio.profiling.debug")) {
      group.addSeparator();
      group.add(new ToggleDebugRender(myTimelineComponent));
    }

    return group;
  }

  @NotNull
  @Override
  public String getTitleName() {
    return "Memory";
  }

  @NotNull
  @Override
  public Icon getTitleIcon() {
    return AndroidIcons.MemoryMonitor;
  }

  @Override
  protected int getDefaultPosition() {
    return 0;
  }

  @NotNull
  @Override
  public String getMonitorName() {
    return "MemoryMonitor";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "memory usage";
  }
}
