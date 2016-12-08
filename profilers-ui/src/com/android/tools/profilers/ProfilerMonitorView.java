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

import com.android.tools.adtui.Choreographer;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public abstract class ProfilerMonitorView<T extends ProfilerMonitor> {

  private static final int MINIMUM_MONITOR_HEIGHT = JBUI.scale(50);

  @NotNull private final T myMonitor;
  @NotNull private final StudioProfilersView myProfilersView;

  public ProfilerMonitorView(@NotNull StudioProfilersView profilersView, @NotNull T monitor) {
    myProfilersView = profilersView;
    myMonitor = monitor;
  }

  @NotNull
  protected final T getMonitor() {
    return myMonitor;
  }

  @NotNull
  protected final StudioProfilersView getProfilersView() {
    return myProfilersView;
  }

  public final JComponent initialize(@NotNull Choreographer choreographer) {
    JPanel container = new JBPanel();
    container.setOpaque(true);
    container.setBackground(ProfilerColors.MONITOR_BACKGROUND);
    container.setBorder(ProfilerLayout.MONITOR_BORDER);
    container.setMinimumSize(new Dimension(0, MINIMUM_MONITOR_HEIGHT));
    populateUi(container, choreographer);
    return container;
  }

  /**
   * @return the vertical weight this monitor view should have in a layout.
   */
  public float getVerticalWeight() {
    return 1f;
  }

  abstract protected void populateUi(JPanel container, Choreographer choreographer);
}
