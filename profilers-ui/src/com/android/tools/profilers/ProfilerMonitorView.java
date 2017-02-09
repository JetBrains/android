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

import com.android.tools.adtui.model.AspectObserver;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public abstract class ProfilerMonitorView<T extends ProfilerMonitor> extends AspectObserver {

  private static final int MINIMUM_MONITOR_HEIGHT = JBUI.scale(50);

  @NotNull private final T myMonitor;

  private JPanel myContainer;

  public ProfilerMonitorView(@NotNull T monitor) {
    myMonitor = monitor;
    myContainer = new JBPanel();
    myContainer.setOpaque(true);
    myContainer.setBorder(ProfilerLayout.MONITOR_BORDER);
    myContainer.setMinimumSize(new Dimension(0, MINIMUM_MONITOR_HEIGHT));

    myMonitor.addDependency(this).onChange(ProfilerMonitor.Aspect.FOCUS, this::focusChanged);
    focusChanged();
  }

  protected void focusChanged() {
    boolean highlight = myMonitor.isFocused() && myMonitor.canExpand();
    myContainer.setBackground(highlight ? ProfilerColors.MONITOR_FOCUSED : ProfilerColors.MONITOR_BACKGROUND);
  }

  @NotNull
  protected final T getMonitor() {
    return myMonitor;
  }

  public final JComponent initialize() {
    populateUi(myContainer);
    return myContainer;
  }

  /**
   * @return the vertical weight this monitor view should have in a layout.
   */
  public float getVerticalWeight() {
    return 1f;
  }

  abstract protected void populateUi(JPanel container);
}
