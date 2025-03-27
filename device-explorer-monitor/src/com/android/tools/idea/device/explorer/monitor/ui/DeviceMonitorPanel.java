/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.monitor.ui;

import com.intellij.ui.components.JBScrollPane;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import java.awt.BorderLayout;
import java.awt.Insets;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class DeviceMonitorPanel {
  static final int TEXT_RENDERER_HORIZ_PADDING = 6;
  static final int TEXT_RENDERER_VERT_PADDING = 4;
  private JPanel mainComponent;
  private JPanel toolbar;
  private JBScrollPane processTablePane;

  public DeviceMonitorPanel() {
    setupUI();
  }

  @NotNull
  public JBScrollPane getProcessTablePane() {
    return processTablePane;
  }

  @NotNull
  public JPanel getComponent() {
    return mainComponent;
  }

  @NotNull
  public JPanel getToolbar() {
    return toolbar;
  }

  private void setupUI() {
    mainComponent = new JPanel();
    mainComponent.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
    toolbar = new JPanel();
    toolbar.setLayout(new BorderLayout(0, 0));
    mainComponent.add(toolbar, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                   GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                   false));
    processTablePane = new JBScrollPane();
    mainComponent.add(processTablePane, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            null, null, null, 0, false));
  }
}
