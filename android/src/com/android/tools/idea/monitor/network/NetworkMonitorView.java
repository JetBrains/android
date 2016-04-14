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
import com.android.tools.idea.actions.BrowserHelpAction;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.monitor.BaseMonitorView;
import com.android.tools.idea.monitor.actions.RecordingAction;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.Future;

public class NetworkMonitorView extends BaseMonitorView<NetworkSampler> implements DeviceContext.DeviceSelectionListener {
  private static final int SAMPLE_FREQUENCY_MS = 500;
  private static final float TIMELINE_BUFFER_TIME = SAMPLE_FREQUENCY_MS * 1.5f / 1000;
  private static final float TIMELINE_INITIAL_MAX = 5.f;
  private static final float TIMELINE_ABSOLUTE_MAX = Float.MAX_VALUE;
  private static final float TIMELINE_INITIAL_MARKER_SEPARATION = 1.f;
  private static final Color BACKGROUND_COLOR = UIUtil.getTextFieldBackground();
  private static final String MISSING_LABEL = "Network monitoring is not available on your device.";
  private static final String STARTING_LABEL =
    "Starting... If this doesn't finish within seconds, the device may not be properly connected. Please reconnect.";

  private Future<?> checkStatsFileFuture;

  public NetworkMonitorView(@NotNull Project project, @NotNull DeviceContext deviceContext) {
    super(project, deviceContext, new NetworkSampler(SAMPLE_FREQUENCY_MS), TIMELINE_BUFFER_TIME, TIMELINE_INITIAL_MAX,
          TIMELINE_ABSOLUTE_MAX, TIMELINE_INITIAL_MARKER_SEPARATION);

    // TODO: Change the initial unit to B/s after fixing the window frozen problem.
    myTimelineComponent.configureUnits("KB/s");
    myTimelineComponent.configureStream(0, "Rx", new JBColor(0xff8000, 0xff8000));
    myTimelineComponent.configureStream(1, "Tx", new JBColor(0xffcc99, 0xffcc99), true);
    myTimelineComponent.setBackground(BACKGROUND_COLOR);

    // Some system images do not have the network stats file, it is a bug; we show a label before the bug is fixed.
    addOverlayText(MISSING_LABEL, PAUSED_LABEL_PRIORITY - 1);
    addOverlayText(STARTING_LABEL, PAUSED_LABEL_PRIORITY + 1);

    setViewComponent(myTimelineComponent);
    deviceContext.addListener(this, project);
  }

  @Override
  @NotNull
  public ActionGroup getToolbarActions() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new RecordingAction(this));
    group.add(new Separator());
    group.add(new BrowserHelpAction("Network monitor", "http://developer.android.com/r/studio-ui/am-network.html"));
    return group;
  }

  /**
   * Sets the newly selected client or null and resets the overlay text in a pooled thread, because an adb command
   * which may be stuck will be called.
   */
  @Override
  public void clientSelected(@Nullable final Client c) {
    mySampler.setClient(c);
    if (checkStatsFileFuture != null) {
      if (!checkStatsFileFuture.isDone()) {
        checkStatsFileFuture.cancel(true);
      }
      checkStatsFileFuture = null;
    }
    setOverlayEnabled(STARTING_LABEL, c != null);
    setOverlayEnabled(MISSING_LABEL, false);
    if (c != null) {
      checkStatsFileFuture = ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          final int networkStatsFileState = mySampler.checkStatsFile(c);
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              if (c == myDeviceContext.getSelectedClient()) {
                setOverlayEnabled(STARTING_LABEL, networkStatsFileState == 0);
                setOverlayEnabled(MISSING_LABEL, networkStatsFileState < 0);
              }
            }
          });
        }
      });
    }
  }

  @NotNull
  @Override
  public String getTitleName() {
    return "Network";
  }

  @NotNull
  @Override
  public Icon getTitleIcon() {
    return AndroidIcons.NetworkMonitor;
  }

  @Override
  protected int getDefaultPosition() {
    return 2;
  }

  @NotNull
  @Override
  public String getMonitorName() {
    return "NetworkMonitor";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "network data usage";
  }
}
