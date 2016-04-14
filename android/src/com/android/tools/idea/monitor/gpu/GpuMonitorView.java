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
import com.android.tools.chartlib.TimelineComponent;
import com.android.tools.chartlib.TimelineData;
import com.android.tools.idea.actions.BrowserHelpAction;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.actions.GfxTraceCaptureAction;
import com.android.tools.idea.monitor.BaseMonitorView;
import com.android.tools.idea.monitor.actions.RecordingAction;
import com.android.tools.idea.monitor.gpu.gfxinfohandlers.JHandler;
import com.android.tools.idea.monitor.gpu.gfxinfohandlers.LHandler;
import com.android.tools.idea.monitor.gpu.gfxinfohandlers.MHandler;
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

public class GpuMonitorView extends BaseMonitorView<GpuSampler> implements ProfileStateListener {
  public static final int PRE_M_SAMPLE_FREQUENCY_MS = 33;
  private static final float PRE_M_TIMELINE_BUFFER_TIME = PRE_M_SAMPLE_FREQUENCY_MS * 1.5f / 1000;
  private static final float PRE_M_TIMELINE_ABSOLUTE_MAX = 67.0f;
  public static final int POST_M_SAMPLE_FREQUENCY_MS = 200;
  private static final float POST_M_TIMELINE_BUFFER_TIME = POST_M_SAMPLE_FREQUENCY_MS * 1.5f / 1000;
  private static final float POST_M_TIMELINE_ABSOLUTE_MAX = 67.0f;
  private static final float TIMELINE_INITIAL_MAX = 17.0f;
  private static final float TIMELINE_INITIAL_MARKER_SEPARATION = 3.0f;
  private static final Color BACKGROUND_COLOR = UIUtil.getTextFieldBackground();

  private static final String NEEDS_NEWER_API_LABEL = "This device does not support the minimum API level (16) for GPU monitor.";
  private static final String PROFILING_URL = "https://developer.android.com/r/studio-ui/am-gpu.html";
  private static final String NEEDS_PROFILING_ENABLED_LABEL = "GPU Profiling needs to be enabled in the device's developer options. " +
                                                              "<a href='" + PROFILING_URL + "'>Learn more</a>.";

  @NotNull private final JPanel myPanel;
  private int myApiLevel = MHandler.MIN_API_LEVEL;

  public static final int EVENT_LAUNCH = 1;
  public static final int EVENT_TRACING = 2;

  public GpuMonitorView(@NotNull Project project, @NotNull DeviceContext deviceContext) {
    super(project, deviceContext, new GpuSampler(POST_M_SAMPLE_FREQUENCY_MS), POST_M_TIMELINE_BUFFER_TIME, TIMELINE_INITIAL_MAX,
          POST_M_TIMELINE_ABSOLUTE_MAX, TIMELINE_INITIAL_MARKER_SEPARATION);
    mySampler.myProfileStateListener = this;
    myPanel = new JPanel(new BorderLayout());

    addOverlayText(NEEDS_NEWER_API_LABEL, PAUSED_LABEL_PRIORITY - 1);
    addOverlayText(NEEDS_PROFILING_ENABLED_LABEL, PAUSED_LABEL_PRIORITY + 1);

    myApiLevel = mySampler.getApiLevel();
    configureTimelineComponent(mySampler.getTimelineData());
    deviceContext.addListener(this, project);

    myPanel.setBackground(BACKGROUND_COLOR);
    setViewComponent(myPanel);
  }

  @Override
  @NotNull
  public ActionGroup getToolbarActions() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new RecordingAction(this));
    if (GfxTraceEditor.isEnabled()) {
      group.add(new Separator());
      group.add(new GfxTraceCaptureAction.Listen(this));
      group.add(new GfxTraceCaptureAction.Launch(this));
      group.add(new Separator());
      group.add(new BrowserHelpAction("GPU monitor", PROFILING_URL));
    }
    return group;
  }

  /**
   * Used by {@link GfxTraceCaptureAction} as an argument to {@link JDialog.setLocationRelativeTo}
   * so that dialogs opened, centred on the same monitor as this panel.
   * @return the panel used by the GPU monitor view.
   */
  public JPanel getPanel() {
    return myPanel;
  }

  @Override
  public void clientSelected(@Nullable final Client client) {
    if (client != mySampler.getClient()) {
      setOverlayEnabled(NEEDS_NEWER_API_LABEL, false);
      setOverlayEnabled(NEEDS_PROFILING_ENABLED_LABEL, false);
    }
    super.clientSelected(client);
    if (client != null) {
      int newApiLevel = mySampler.getApiLevel();
      if (newApiLevel != myApiLevel) {
        myApiLevel = newApiLevel;
        configureTimelineComponent(mySampler.getTimelineData());
      }
    }
  }

  @NotNull
  @Override
  public String getTitleName() {
    return "GPU";
  }

  @NotNull
  @Override
  public Icon getTitleIcon() {
    return AndroidIcons.GpuMonitor;
  }

  @Override
  protected boolean getPreferredPausedState() {
    return true;
  }

  @Override
  protected int getDefaultPosition() {
    return 3;
  }

  @NotNull
  @Override
  public String getMonitorName() {
    return "GpuMonitor";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "gpu usage";
  }

  private void configureTimelineComponent(@NotNull TimelineData data) {
    if (myApiLevel >= MHandler.MIN_API_LEVEL) {
      myPanel.remove(myTimelineComponent);
      myTimelineComponent =
        new TimelineComponent(data, myEvents, POST_M_TIMELINE_BUFFER_TIME, TIMELINE_INITIAL_MAX, POST_M_TIMELINE_ABSOLUTE_MAX,
                              TIMELINE_INITIAL_MARKER_SEPARATION);

      myTimelineComponent.configureUnits("ms");
      myTimelineComponent.configureStream(0, "VSync Delay", new JBColor(0x007c6d, 0x00695c));
      myTimelineComponent.configureStream(1, "Input Handling", new JBColor(0x00a292, 0x00897b));
      myTimelineComponent.configureStream(2, "Animation", new JBColor(0x00b2a1, 0x009688));
      myTimelineComponent.configureStream(3, "Measure/Layout", new JBColor(0x2dc5b6, 0x26a69a));
      myTimelineComponent.configureStream(4, "Draw", new JBColor(0x27b2ff, 0x2196f3));
      myTimelineComponent.configureStream(5, "Sync", new JBColor(0x5de7ff, 0x4fc3f7));
      myTimelineComponent.configureStream(6, "Command Issue", new JBColor(0xff4f40, 0xf44336));
      myTimelineComponent.configureStream(7, "Swap Buffers", new JBColor(0xffb400, 0xff9800));
      myTimelineComponent.configureStream(8, "Misc Time", new JBColor(0x008f7f, 0x00796b));
      myTimelineComponent.setBackground(BACKGROUND_COLOR);

      setOverlayEnabled(NEEDS_NEWER_API_LABEL, false);
      myPanel.add(myTimelineComponent, BorderLayout.CENTER);
    }
    else if (myApiLevel >= JHandler.MIN_API_LEVEL) {
      myPanel.remove(myTimelineComponent);
      myTimelineComponent =
        new TimelineComponent(data, myEvents, PRE_M_TIMELINE_BUFFER_TIME, TIMELINE_INITIAL_MAX, PRE_M_TIMELINE_ABSOLUTE_MAX,
                              TIMELINE_INITIAL_MARKER_SEPARATION);

      if (myApiLevel >= LHandler.MIN_API_LEVEL) {
        myTimelineComponent.configureUnits("ms");
        myTimelineComponent.configureStream(0, "Draw", new JBColor(0x4979f2, 0x3e66cc));
        myTimelineComponent.configureStream(1, "Prepare", new JBColor(0xa900ff, 0x8f00ff));
        myTimelineComponent.configureStream(2, "Process", new JBColor(0xff4315, 0xdc3912));
        myTimelineComponent.configureStream(3, "Execute", new JBColor(0xffb400, 0xe69800));
        myTimelineComponent.setBackground(BACKGROUND_COLOR);
      }
      else {
        myTimelineComponent.configureUnits("ms");
        myTimelineComponent.configureStream(0, "Draw", new JBColor(0x4979f2, 0x3e66cc));
        myTimelineComponent.configureStream(1, "Process", new JBColor(0xff4315, 0xdc3912));
        myTimelineComponent.configureStream(2, "Execute", new JBColor(0xffb400, 0xe69800));
        myTimelineComponent.setBackground(BACKGROUND_COLOR);
      }

      setOverlayEnabled(NEEDS_NEWER_API_LABEL, false);
      myPanel.add(myTimelineComponent, BorderLayout.CENTER);
    }
    else {
      setOverlayEnabled(NEEDS_NEWER_API_LABEL, true);
    }
    myTimelineComponent
      .configureEvent(EVENT_LAUNCH, 0, AndroidIcons.Ddms.Threads, new JBColor(0x92ADC6, 0x718493), new JBColor(0x2B4E8C, 0xC7E5FF), false);
    myTimelineComponent.configureEvent(EVENT_TRACING, 0, AndroidIcons.Ddms.StartMethodProfiling, new JBColor(0x92ADC6, 0x718493),
                                       new JBColor(0x2B4E8C, 0xC7E5FF), true);
    myTimelineComponent.addReference(16.6f, JBColor.GREEN);
    myTimelineComponent.addReference(33.3f, JBColor.RED);
  }

  @Override
  public void notifyGpuProfileStateChanged(@NotNull final Client client, final boolean enabled) {
    // TODO revisit the dataflow in future refactor (i.e. consider adding error states to TimelineData)
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (mySampler.getClient() == client) {
          setOverlayEnabled(NEEDS_PROFILING_ENABLED_LABEL, !enabled);
          if (enabled) {
            configureTimelineComponent(mySampler.getTimelineData());
          }
          else {
            mySampler.getTimelineData().clear();
          }
        }
      }
    });
  }
}
