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
package com.android.tools.idea.monitor;

import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.monitor.cpu.CpuMonitorView;
import com.android.tools.idea.monitor.gpu.GpuMonitorView;
import com.android.tools.idea.monitor.memory.MemoryMonitorView;
import com.android.tools.idea.monitor.network.NetworkMonitorView;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class MonitorContentFactory {
  public static void createMonitorContent(@NotNull final Project project,
                                          @NotNull DeviceContext deviceContext,
                                          @NotNull RunnerLayoutUi layoutUi) {
    BaseMonitorView[] monitors =
      new BaseMonitorView[]{new CpuMonitorView(project, deviceContext), new MemoryMonitorView(project, deviceContext),
        new NetworkMonitorView(project, deviceContext), new GpuMonitorView(project, deviceContext)};

    MonitorPanel monitorPanel = new MonitorPanel(monitors);

    JBScrollPane monitorScrollPane =
      new JBScrollPane(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    monitorScrollPane.setViewportView(monitorPanel);
    monitorScrollPane.getVerticalScrollBar().setUnitIncrement(10);
    monitorScrollPane.getHorizontalScrollBar().setUnitIncrement(10);

    Content monitorContent = layoutUi.createContent("Monitors", monitorScrollPane, "Monitors", null, null);
    monitorContent.setCloseable(false);
    layoutUi.addContent(monitorContent);
  }
}
