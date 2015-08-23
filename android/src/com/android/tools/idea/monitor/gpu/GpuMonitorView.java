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
package com.android.tools.idea.monitor.gpu;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.chartlib.EventData;
import com.android.tools.chartlib.TimelineComponent;
import com.android.tools.chartlib.TimelineData;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.monitor.BaseMonitorView;
import com.android.tools.idea.monitor.DeviceSampler;
import com.android.tools.idea.monitor.TimelineEventListener;
import com.android.tools.idea.monitor.actions.RecordingAction;
import com.android.tools.idea.monitor.gpu.gfxinfohandlers.JHandler;
import com.android.tools.idea.monitor.gpu.gfxinfohandlers.LHandler;
import com.android.tools.idea.monitor.gpu.gfxinfohandlers.MHandler;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class GpuMonitorView extends BaseMonitorView
  implements ProfileStateListener, TimelineEventListener, DeviceContext.DeviceSelectionListener {
  public static final int PRE_M_SAMPLE_FREQUENCY_MS = 33;
  public static final int POST_M_SAMPLE_FREQUENCY_MS = 200;
  private static final Color BACKGROUND_COLOR = UIUtil.getTextFieldBackground();

  private static final String NEEDS_NEWER_API_LABEL = "This device does not support the minimum API level (16) for GPU monitor.";
  private static final String NEEDS_PROFILING_ENABLED_LABEL =
    "GPU Profiling needs to be enabled in the device's developer options. " +
    "<a href='https://developer.android.com/preview/testing/performance.html#timing-dump'>Learn more</a>.";

  @NotNull private final JPanel myPanel;
  @NotNull private final GpuSampler myGpuSampler;
  @Nullable private TimelineComponent myCurrentTimelineComponent;
  @Nullable private Client myClient;
  private int myApiLevel;

  public GpuMonitorView(@NotNull Project project, @NotNull DeviceContext deviceContext) {
    super(project);

    myPanel = new JPanel(new BorderLayout());

    addOverlayText(NEEDS_NEWER_API_LABEL, 0);
    addOverlayText(PAUSED_LABEL, 1);
    addOverlayText(NEEDS_PROFILING_ENABLED_LABEL, 2);

    myGpuSampler = new GpuSampler(PRE_M_SAMPLE_FREQUENCY_MS, this);
    myGpuSampler.addListener(this);

    myApiLevel = myGpuSampler.getApiLevel();
    configureTimelineComponent(myGpuSampler.getTimelineData());
    deviceContext.addListener(this, project);

    myPanel.setBackground(BACKGROUND_COLOR);
    setViewComponent(myPanel);

    setPaused(true); // Start GPU monitor as paused because GPU monitor uses app memory on the device.
  }

  @Override
  @NotNull
  public ActionGroup getToolbarActions() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new RecordingAction(this));
    return group;
  }

  @Override
  public void deviceSelected(@Nullable IDevice device) {

  }

  @Override
  public void deviceChanged(@NotNull IDevice device, int changeMask) {

  }

  @Override
  public void clientSelected(@Nullable final Client client) {
    if (client != myClient) {
      setOverlayEnabled(NEEDS_NEWER_API_LABEL, false);
      setOverlayEnabled(NEEDS_PROFILING_ENABLED_LABEL, false);
    }

    myClient = client;
    myGpuSampler.setClient(client);
    if (client != null) {
      int newApiLevel = myGpuSampler.getApiLevel();
      if (newApiLevel != myApiLevel) {
        myApiLevel = newApiLevel;
        configureTimelineComponent(myGpuSampler.getTimelineData());
      }
    }
  }

  @Override
  public void setPaused(boolean paused) {
    myGpuSampler.setIsPaused(paused);
    setOverlayEnabled(PAUSED_LABEL, paused);
    if (myCurrentTimelineComponent != null) {
      myCurrentTimelineComponent.setUpdateData(!paused);
    }
  }

  @Override
  public boolean isPaused() {
    return myGpuSampler.getIsPaused();
  }

  @NotNull
  @Override
  public String getDescription() {
    return "gpu usage";
  }

  @Override
  protected DeviceSampler getSampler() {
    return myGpuSampler;
  }

  private void configureTimelineComponent(@NotNull TimelineData data) {
    EventData events = new EventData();

    if (myApiLevel >= MHandler.MIN_API_LEVEL) {
      // Buffer at one and a half times the sample frequency.
      float bufferTimeInSeconds = POST_M_SAMPLE_FREQUENCY_MS * 1.5f / 1000.f;

      if (myCurrentTimelineComponent != null) {
        myPanel.remove(myCurrentTimelineComponent);
      }
      myCurrentTimelineComponent = new TimelineComponent(data, events, bufferTimeInSeconds, 17.0f, 67.0f, 3.0f);

      myCurrentTimelineComponent.configureUnits("ms");
      myCurrentTimelineComponent.configureStream(0, "VSync Delay", new JBColor(0x007c6d, 0x00695c));
      myCurrentTimelineComponent.configureStream(1, "Input Handling", new JBColor(0x00a292, 0x00897b));
      myCurrentTimelineComponent.configureStream(2, "Animation", new JBColor(0x00b2a1, 0x009688));
      myCurrentTimelineComponent.configureStream(3, "Measure/Layout", new JBColor(0x2dc5b6, 0x26a69a));
      myCurrentTimelineComponent.configureStream(4, "Draw", new JBColor(0x27b2ff, 0x2196f3));
      myCurrentTimelineComponent.configureStream(5, "Sync", new JBColor(0x5de7ff, 0x4fc3f7));
      myCurrentTimelineComponent.configureStream(6, "Command Issue", new JBColor(0xff4f40, 0xf44336));
      myCurrentTimelineComponent.configureStream(7, "Swap Buffers", new JBColor(0xffb400, 0xff9800));
      myCurrentTimelineComponent.configureStream(8, "Misc Time", new JBColor(0x008f7f, 0x00796b));
      myCurrentTimelineComponent.setBackground(BACKGROUND_COLOR);

      setOverlayEnabled(NEEDS_NEWER_API_LABEL, false);
      myPanel.add(myCurrentTimelineComponent, BorderLayout.CENTER);
    }
    else if (myApiLevel >= JHandler.MIN_API_LEVEL) {
      // Buffer at one and a half times the sample frequency.
      float bufferTimeInSeconds = PRE_M_SAMPLE_FREQUENCY_MS * 1.5f / 1000.f;

      if (myCurrentTimelineComponent != null) {
        myPanel.remove(myCurrentTimelineComponent);
      }
      myCurrentTimelineComponent = new TimelineComponent(data, events, bufferTimeInSeconds, 17.0f, 100.0f, 3.0f);

      if (myApiLevel >= LHandler.MIN_API_LEVEL) {
        myCurrentTimelineComponent.configureUnits("ms");
        myCurrentTimelineComponent.configureStream(0, "Draw", new JBColor(0x4979f2, 0x3e66cc));
        myCurrentTimelineComponent.configureStream(1, "Prepare", new JBColor(0xa900ff, 0x8f00ff));
        myCurrentTimelineComponent.configureStream(2, "Process", new JBColor(0xff4315, 0xdc3912));
        myCurrentTimelineComponent.configureStream(3, "Execute", new JBColor(0xffb400, 0xe69800));
        myCurrentTimelineComponent.setBackground(BACKGROUND_COLOR);
      }
      else {
        myCurrentTimelineComponent.configureUnits("ms");
        myCurrentTimelineComponent.configureStream(0, "Draw", new JBColor(0x4979f2, 0x3e66cc));
        myCurrentTimelineComponent.configureStream(1, "Process", new JBColor(0xff4315, 0xdc3912));
        myCurrentTimelineComponent.configureStream(2, "Execute", new JBColor(0xffb400, 0xe69800));
        myCurrentTimelineComponent.setBackground(BACKGROUND_COLOR);
      }

      setOverlayEnabled(NEEDS_NEWER_API_LABEL, false);
      myPanel.add(myCurrentTimelineComponent, BorderLayout.CENTER);
    }
    else {
      setOverlayEnabled(NEEDS_NEWER_API_LABEL, true);
    }
  }

  @Override
  public void notifyGpuProfileStateChanged(@NotNull final Client client, final boolean enabled) {
    // TODO revisit the dataflow in future refactor (i.e. consider adding error states to TimelineData)
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (myClient == client) {
          setOverlayEnabled(NEEDS_PROFILING_ENABLED_LABEL, !enabled);
          if (enabled) {
            configureTimelineComponent(myGpuSampler.getTimelineData());
          }
          else {
            myGpuSampler.getTimelineData().clear();
          }
        }
      }
    });
  }
}
