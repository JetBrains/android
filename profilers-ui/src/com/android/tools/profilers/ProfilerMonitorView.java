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
import com.android.tools.adtui.common.AdtUiUtils;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;

public abstract class ProfilerMonitorView {
  protected static final int MAX_AXIS_WIDTH = JBUI.scale(100);
  protected static final int MARKER_LENGTH = JBUI.scale(5);
  protected static final Border LABEL_PADDING = BorderFactory.createEmptyBorder(5, 10, 5, 10);
  private static final Border MONITOR_BORDER = BorderFactory.createCompoundBorder(
    new MatteBorder(0, 0, 1, 0, AdtUiUtils.DEFAULT_BORDER_COLOR),
    new EmptyBorder(0, 0, 0, 0));

  public final JComponent initialize(Choreographer choreographer) {
    JLayeredPane container = new JLayeredPane();
    container.setOpaque(true);
    container.setBackground(Color.WHITE); // TODO use correct IJ editor background color.
    container.setBorder(MONITOR_BORDER);
    populateUi(container, choreographer);
    return container;
  }

  abstract protected void populateUi(JLayeredPane container, Choreographer choreographer);
}
