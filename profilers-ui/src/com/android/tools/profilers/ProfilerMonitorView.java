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
import com.android.tools.profilers.timeline.AnimatedPan;
import com.android.tools.profilers.timeline.AnimatedZoom;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;

public abstract class ProfilerMonitorView<T extends ProfilerMonitor> {

  /**
   * The percentage of the current view range's length to zoom/pan per mouse wheel click.
   */
  private static final float VIEW_PERCENTAGE_PER_MOUSEHWEEL_FACTOR = 0.005f;

  private static final Border MONITOR_BORDER = BorderFactory.createCompoundBorder(
    new MatteBorder(0, 0, 1, 0, ProfilerColors.MONITOR_BORDER),
    new EmptyBorder(0, 0, 0, 0));
  private static final int MINIMUM_MONITOR_HEIGHT = JBUI.scale(50);

  @NotNull private final T myMonitor;

  public ProfilerMonitorView(@NotNull T monitor) {
    myMonitor = monitor;
  }

  @NotNull
  protected final T getMonitor() {
    return myMonitor;
  }

  public final JComponent initialize(@NotNull Choreographer choreographer) {
    JPanel container = new JBPanel();
    container.setOpaque(true);
    container.setBackground(ProfilerColors.MONITOR_BACKGROUND);
    container.setBorder(MONITOR_BORDER);
    container.setMinimumSize(new Dimension(Integer.MAX_VALUE, MINIMUM_MONITOR_HEIGHT));
    container.setPreferredSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
    populateUi(container, choreographer);

    container.addMouseWheelListener(e -> {
      int count = e.getWheelRotation();
      double deltaUs = getMonitor().getTimeline().getViewRange().getLength() * VIEW_PERCENTAGE_PER_MOUSEHWEEL_FACTOR * count;
      ProfilerTimeline timeline = getMonitor().getTimeline();
      if (e.isAltDown()) {
        double anchor = ((float)e.getX() / e.getComponent().getWidth());
        choreographer.register(new AnimatedZoom(choreographer, timeline, deltaUs, anchor));
      }
      else {
        choreographer.register(new AnimatedPan(choreographer, timeline, deltaUs));
      }
    });

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
