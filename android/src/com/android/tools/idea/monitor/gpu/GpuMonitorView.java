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
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithActions;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class GpuMonitorView extends BaseMonitorView implements TimelineEventListener, DeviceContext.DeviceSelectionListener {
  public static int DEFAULT_API_LEVEL = 22;
  public static int DETAILED_API_LEVEL = 23;

  public static final int PRE_M_SAMPLES = 2048;
  public static final int POST_M_SAMPLES = 8192;
  public static final int PRE_M_SAMPLE_FREQUENCY_MS = 33;
  public static final int POST_M_SAMPLE_FREQUENCY_MS = 200;
  private static final Color BACKGROUND_COLOR = UIUtil.getTextFieldBackground();

  private int myApiLevel = DEFAULT_API_LEVEL;
  @NotNull private final GpuSampler myGpuSampler;

  public GpuMonitorView(@NotNull Project project, @NotNull DeviceContext deviceContext) {
    super(project);

    myApiLevel = getApiLevel(deviceContext.getSelectedClient());
    TimelineData data = createTimelineData();
    configureTimelineComponent(data);
    myGpuSampler = new GpuSampler(data, PRE_M_SAMPLE_FREQUENCY_MS, myApiLevel);
    myGpuSampler.addListener(this);

    deviceContext.addListener(this, project);
  }

  @NotNull
  public ActionGroup getToolbarActions() {
    return new DefaultActionGroup();
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
    int newApiLevel = getApiLevel(c);
    if (newApiLevel != myApiLevel) {
      myGpuSampler.stop();
      myApiLevel = newApiLevel;
      TimelineData data = createTimelineData();
      configureTimelineComponent(data);
      myGpuSampler.resetClientState(c, data, myApiLevel);
    }
    else {
      myGpuSampler.setClient(c);
    }
  }

  @Override
  public void onStart() {
  }

  @Override
  public void onStop() {
  }

  @Override
  protected DeviceSampler getSampler() {
    return myGpuSampler;
  }

  private int getApiLevel(@Nullable Client client) {
    if (client != null) {
      String apiString = client.getDevice().getProperty("ro.build.version.sdk");
      if (apiString == null) {
        return DEFAULT_API_LEVEL;
      }
      else {
        try {
          // TODO remove this version promotion workaround after M launches
          int apiLevel = Integer.parseInt(apiString);
          if (apiLevel == 22) {
            String versionString = client.getDevice().getProperty("ro.build.version.release");
            if (versionString == null) {
              return 22;
            }
            return "M".equals(versionString) ? DETAILED_API_LEVEL : 22;
          }
          return apiLevel;
        }
        catch (NumberFormatException e) {
          return DEFAULT_API_LEVEL;
        }
      }
    }
    return myApiLevel;
  }

  @NotNull
  private TimelineData createTimelineData() {
    if (myApiLevel >= DETAILED_API_LEVEL) {
      return new TimelineData(9, POST_M_SAMPLES);
    }
    else {
      return new TimelineData(4, PRE_M_SAMPLES);
    }
  }

  private void configureTimelineComponent(@NotNull TimelineData data) {
    TimelineComponent timelineComponent;
    EventData events = new EventData();

    if (myApiLevel >= DETAILED_API_LEVEL) {
      // Buffer at one and a half times the sample frequency.
      float bufferTimeInSeconds = POST_M_SAMPLE_FREQUENCY_MS * 1.5f / 1000.f;

      timelineComponent = new TimelineComponent(data, events, bufferTimeInSeconds, 17.0f, 67.0f, 3.0f);

      timelineComponent.configureUnits("ms");
      timelineComponent.configureStream(0, "VSync Delay", new JBColor(0x007c6d, 0x00695c));
      timelineComponent.configureStream(1, "Input Handling", new JBColor(0x00a292, 0x00897b));
      timelineComponent.configureStream(2, "Animation", new JBColor(0x00b2a1, 0x009688));
      timelineComponent.configureStream(3, "Measure/Layout", new JBColor(0x2dc5b6, 0x26a69a));
      timelineComponent.configureStream(4, "Draw", new JBColor(0x27b2ff, 0x2196f3));
      timelineComponent.configureStream(5, "Sync", new JBColor(0x5de7ff, 0x4fc3f7));
      timelineComponent.configureStream(6, "Command Issue", new JBColor(0xff4f40, 0xf44336));
      timelineComponent.configureStream(7, "Swap Buffers", new JBColor(0xffb400, 0xff9800));
      timelineComponent.configureStream(8, "Misc Time", new JBColor(0x008f7f, 0x00796b));
      timelineComponent.setBackground(BACKGROUND_COLOR);
    }
    else {
      // Buffer at one and a half times the sample frequency.
      float bufferTimeInSeconds = PRE_M_SAMPLE_FREQUENCY_MS * 1.5f / 1000.f;

      timelineComponent = new TimelineComponent(data, events, bufferTimeInSeconds, 17.0f, 100.0f, 3.0f);

      timelineComponent.configureUnits("ms");
      timelineComponent.configureStream(0, "Draw", new JBColor(0x4979f2, 0x3e66cc));
      timelineComponent.configureStream(1, "Prepare", new JBColor(0xa900ff, 0x8f00ff));
      timelineComponent.configureStream(2, "Process", new JBColor(0xff4315, 0xdc3912));
      timelineComponent.configureStream(3, "Execute", new JBColor(0xffb400, 0xe69800));
      timelineComponent.setBackground(BACKGROUND_COLOR);
    }

    setComponent(timelineComponent);
  }
}
