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
package com.android.tools.idea.monitor;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.RightAlignedToolbarAction;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

class MinimizeAction extends AnAction implements RightAlignedToolbarAction {
  private static final Icon MIN_ICON = AllIcons.General.HideDown;
  private static final Icon RESTORE_ICON = AllIcons.Debugger.RestoreLayout;

  @NotNull private MonitorPanel myMonitorPanel;
  @NotNull private BaseMonitorView myBaseMonitorView;

  MinimizeAction(@NotNull MonitorPanel monitorPanel, @NotNull BaseMonitorView monitor) {
    super("Minimize/Maximize " + monitor.getTitleName() + " Monitor",
          "Minimizes or maximizes the " + monitor.getTitleName() + " Monitor.", monitor.getIsMinimized() ? RESTORE_ICON : MIN_ICON);
    myMonitorPanel = monitorPanel;
    myBaseMonitorView = monitor;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    myMonitorPanel.setMonitorMinimized(myBaseMonitorView, !myBaseMonitorView.getIsMinimized());
    update(e);
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setIcon(myBaseMonitorView.getIsMinimized() ? RESTORE_ICON : MIN_ICON);
  }
}
