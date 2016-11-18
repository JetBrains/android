/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profilers;

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.common.formatter.TimeAxisFormatter;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public abstract class StageView {
  private final Stage myStage;
  private final Choreographer myChoreographer;
  private final JPanel myComponent;

  private static final int TIME_AXIS_HEIGHT = JBUI.scale(20);

  public StageView(@NotNull Stage stage) {
    myStage = stage;
    myComponent = new JBPanel(new BorderLayout());
    myComponent.setBackground(ProfilerColors.MONITOR_BACKGROUND);
    myChoreographer = new Choreographer(myComponent);
    myChoreographer.register(new AnimatedTimeline(getTimeline()));
  }

  @NotNull
  public final Stage getStage() {
    return myStage;
  }

  @NotNull
  public final JComponent getComponent() {
    return myComponent;
  }

  @NotNull
  public final Choreographer getChoreographer() {
    return myChoreographer;
  }

  @NotNull
  public final ProfilerTimeline getTimeline() {
    return myStage.getStudioProfilers().getTimeline();
  }

  public void exit() {
    myChoreographer.stop();
  }

  @NotNull
  protected AxisComponent buildTimeAxis(StudioProfilers profilers) {
    AxisComponent.Builder builder = new AxisComponent.Builder(profilers.getTimeline().getViewRange(), TimeAxisFormatter.DEFAULT,
                                                              AxisComponent.AxisOrientation.BOTTOM);
    builder.setGlobalRange(profilers.getDataRange()).showAxisLine(false)
      .setOffset(profilers.getDeviceStartUs());
    AxisComponent timeAxis = builder.build();
    timeAxis.setMinimumSize(new Dimension(Integer.MAX_VALUE, TIME_AXIS_HEIGHT));
    return timeAxis;
  }

  abstract public JComponent getToolbar();

  protected void returnToStudioStage() {
    StudioProfilers profilers = getStage().getStudioProfilers();
    StudioMonitorStage monitor = new StudioMonitorStage(profilers);
    profilers.setStage(monitor);
  }

  /**
   * A purely visual concept as to whether this stage wants the "process and devices" selection being shown to the user.
   * It is not possible to assume processes won't change while a stage is running. For example: a process dying.
   */
  public boolean needsProcessSelection() {
    return false;
  }
}
