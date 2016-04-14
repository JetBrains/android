/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.monitor.cpu;

import com.android.tools.idea.actions.BrowserHelpAction;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.ddms.actions.ToggleMethodProfilingAction;
import com.android.tools.idea.monitor.BaseMonitorView;
import com.android.tools.idea.monitor.TimelineEventListener;
import com.android.tools.idea.monitor.actions.RecordingAction;
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

public class CpuMonitorView extends BaseMonitorView<CpuSampler> implements TimelineEventListener, DeviceContext.DeviceSelectionListener {
  /**
   * Maximum number of samples to keep in memory. We not only sample at {@code SAMPLE_FREQUENCY_MS} but we also receive
   * a sample on every GC.
   */
  private static final Color BACKGROUND_COLOR = UIUtil.getTextFieldBackground();
  private static final int SAMPLE_FREQUENCY_MS = 500;
  // Buffer at one and a half times the sample frequency.
  private static final float TIMELINE_BUFFER_TIME = SAMPLE_FREQUENCY_MS * 1.5f / 1000;
  private static final float TIMELINE_INITIAL_MAX = 100.0f;
  private static final float TIMELINE_ABSOLUTE_MAX = TIMELINE_INITIAL_MAX;
  private static final float TIMELINE_INITIAL_MARKER_SEPARATION = 10.0f;

  public CpuMonitorView(@NotNull Project project, @NotNull DeviceContext deviceContext) {
    super(project, deviceContext, new CpuSampler(SAMPLE_FREQUENCY_MS), TIMELINE_BUFFER_TIME, TIMELINE_INITIAL_MAX, TIMELINE_ABSOLUTE_MAX,
          TIMELINE_INITIAL_MARKER_SEPARATION);

    myTimelineComponent.configureUnits("%");
    myTimelineComponent.configureStream(0, "Kernel", new JBColor(0xd73f3f, 0xd73f3f));
    myTimelineComponent.configureStream(1, "User", new JBColor(0xeb9f9f, 0x9d4c4c));
    myTimelineComponent.setBackground(BACKGROUND_COLOR);

    setViewComponent(myTimelineComponent);
  }

  @Override
  @NotNull
  public ActionGroup getToolbarActions() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new RecordingAction(this));
    group.add(new Separator());
    group.add(new ToggleMethodProfilingAction(myProject, myDeviceContext));
    //group.add(new MyThreadDumpAction()); // thread dump -> systrace
    group.add(new Separator());
    group.add(new BrowserHelpAction("CPU monitor", "http://developer.android.com/r/studio-ui/am-cpu.html"));

    return group;
  }

  @NotNull
  @Override
  public String getTitleName() {
    return "CPU";
  }

  @NotNull
  @Override
  public Icon getTitleIcon() {
    return AndroidIcons.CpuMonitor;
  }

  @Override
  protected int getDefaultPosition() {
    return 1;
  }

  @NotNull
  @Override
  public String getMonitorName() {
    return "CpuMonitor";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "cpu usage";
  }
}
