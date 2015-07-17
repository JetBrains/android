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

import com.android.ddmlib.*;
import com.android.tools.chartlib.EventData;
import com.android.tools.chartlib.TimelineComponent;
import com.android.tools.chartlib.TimelineData;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.monitor.BaseMonitorView;
import com.android.tools.idea.monitor.DeviceSampler;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithActions;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class NetworkMonitorView extends BaseMonitorView implements DeviceContext.DeviceSelectionListener {

  private static final int TIMELINE_DATA_STREAM_SIZE = 2;
  private static final int TIMELINE_DATA_SIZE = 2048;
  private static final int SAMPLE_FREQUENCY_MS = 500;
  private static final float TIMELINE_BUFFER_TIME = SAMPLE_FREQUENCY_MS * 1.5f / 1000;
  private static final float TIMELINE_INITIAL_MAX = 5.f;
  private static final float TIMELINE_INITIAL_MARKER_SEPARATION = 1.f;
  private static final Color BACKGROUND_COLOR = UIUtil.getTextFieldBackground();
  private static final String TIMELINE_COMPONENT_CARD = "timeline";
  private static final String MISSING_LABEL_CARD = "missing";

  @NotNull private final NetworkSampler myNetworkSampler;
  @NotNull private final DeviceContext myDeviceContext;
  @NotNull private final TimelineComponent myTimelineComponent;
  @NotNull private final JPanel myCardPanel;

  public NetworkMonitorView(@NotNull Project project, @NotNull DeviceContext deviceContext) {
    super(project);

    TimelineData data = new TimelineData(TIMELINE_DATA_STREAM_SIZE, TIMELINE_DATA_SIZE);
    myTimelineComponent = new TimelineComponent(data, new EventData(), TIMELINE_BUFFER_TIME, TIMELINE_INITIAL_MAX, Float.MAX_VALUE,
                                                TIMELINE_INITIAL_MARKER_SEPARATION);
    // TODO: Change the initial unit to B/s after fixing the window frozen problem.
    myTimelineComponent.configureUnits("KB/s");
    myTimelineComponent.configureStream(0, "Rx", new JBColor(0xff8000, 0xff8000));
    myTimelineComponent.configureStream(1, "Tx", new JBColor(0xffcc99, 0xffcc99));

    // Some system images do not have the network stats file, it is a bug; we show a label before the bug is fixed.
    JLabel fileMissingLabel = new JLabel("Network monitoring is not available on your device.");
    fileMissingLabel.setHorizontalAlignment(SwingConstants.CENTER);

    myCardPanel = new JPanel(new CardLayout());
    myCardPanel.add(myTimelineComponent, TIMELINE_COMPONENT_CARD);
    myCardPanel.add(fileMissingLabel, MISSING_LABEL_CARD);
    myCardPanel.setBackground(BACKGROUND_COLOR);
    setComponent(myCardPanel);

    myNetworkSampler = new NetworkSampler(data, SAMPLE_FREQUENCY_MS);
    myNetworkSampler.addListener(this);

    myDeviceContext = deviceContext;
    myDeviceContext.addListener(this, project);
  }

  @NotNull
  public ComponentWithActions createComponent() {
    return new ComponentWithActions.Impl(new DefaultActionGroup(), null, null, null, myContentPane);
  }

  @Override
  public void deviceChanged(@NotNull IDevice device, int changeMask) {
  }

  @Override
  public void deviceSelected(@Nullable IDevice device) {
    if (device == null || device.isOffline()) {
      return;
    }
    final boolean canReadNetworkStatistics = myNetworkSampler.canReadNetworkStatistics();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        CardLayout layout = (CardLayout)myCardPanel.getLayout();
        layout.show(myCardPanel, canReadNetworkStatistics ? TIMELINE_COMPONENT_CARD : MISSING_LABEL_CARD);
      }
    });
  }

  @Override
  public void clientSelected(@Nullable Client c) {
    myNetworkSampler.setClient(c);
  }

  @Override
  protected DeviceSampler getSampler() {
    return myNetworkSampler;
  }
}
