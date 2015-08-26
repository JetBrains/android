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
package com.android.tools.idea.monitor.network;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.chartlib.EventData;
import com.android.tools.chartlib.TimelineComponent;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.monitor.BaseMonitorView;
import com.android.tools.idea.monitor.DeviceSampler;
import com.android.tools.idea.monitor.actions.RecordingAction;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class NetworkMonitorView extends BaseMonitorView implements DeviceContext.DeviceSelectionListener {
  private static final int SAMPLE_FREQUENCY_MS = 500;
  private static final float TIMELINE_BUFFER_TIME = SAMPLE_FREQUENCY_MS * 1.5f / 1000;
  private static final float TIMELINE_INITIAL_MAX = 5.f;
  private static final float TIMELINE_INITIAL_MARKER_SEPARATION = 1.f;
  private static final Color BACKGROUND_COLOR = UIUtil.getTextFieldBackground();
  private static final String MISSING_LABEL = "Network monitoring is not available on your device.";

  @NotNull private final NetworkSampler myNetworkSampler;
  @NotNull private final TimelineComponent myTimelineComponent;

  public NetworkMonitorView(@NotNull Project project, @NotNull DeviceContext deviceContext) {
    super(project);

    myNetworkSampler = new NetworkSampler(SAMPLE_FREQUENCY_MS);
    myNetworkSampler.addListener(this);

    myTimelineComponent =
      new TimelineComponent(myNetworkSampler.getTimelineData(), new EventData(), TIMELINE_BUFFER_TIME, TIMELINE_INITIAL_MAX,
                            Float.MAX_VALUE, TIMELINE_INITIAL_MARKER_SEPARATION);
    // TODO: Change the initial unit to B/s after fixing the window frozen problem.
    myTimelineComponent.configureUnits("KB/s");
    myTimelineComponent.configureStream(0, "Rx", new JBColor(0xff8000, 0xff8000));
    myTimelineComponent.configureStream(1, "Tx", new JBColor(0xffcc99, 0xffcc99));
    myTimelineComponent.setBackground(BACKGROUND_COLOR);

    // Some system images do not have the network stats file, it is a bug; we show a label before the bug is fixed.
    addOverlayText(MISSING_LABEL, 0);
    addOverlayText(PAUSED_LABEL, 1);

    setViewComponent(myTimelineComponent);
    deviceContext.addListener(this, project);
  }

  @Override
  @NotNull
  public ActionGroup getToolbarActions() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new RecordingAction(this));
    return group;
  }

  @Override
  public void deviceChanged(@NotNull IDevice device, int changeMask) {
  }

  @Override
  public void deviceSelected(@Nullable IDevice device) {
  }

  @Override
  public void clientSelected(@Nullable Client c) {
    myNetworkSampler.setClient(c);
    // TODO: Need to move canReadNetworkStatistics to a separate thread. This is causing a hiccup in the UI.
    setOverlayEnabled(MISSING_LABEL, c != null && !myNetworkSampler.canReadNetworkStatistics());
  }

  @Override
  public void setPaused(boolean paused) {
    myNetworkSampler.setIsPaused(paused);
    setOverlayEnabled(PAUSED_LABEL, paused);
    myTimelineComponent.setUpdateData(!paused);
  }

  @Override
  public boolean isPaused() {
    return myNetworkSampler.getIsPaused();
  }

  @NotNull
  @Override
  public String getDescription() {
    return "network data usage";
  }

  @Override
  protected DeviceSampler getSampler() {
    return myNetworkSampler;
  }
}
